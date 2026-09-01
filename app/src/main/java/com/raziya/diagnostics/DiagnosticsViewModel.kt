package com.raziya.diagnostics

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raziya.diagnostics.bluetooth.RawCanBluetoothClient
import com.raziya.diagnostics.can.CanFrame
import com.raziya.diagnostics.can.DiagnosticRequest
import com.raziya.diagnostics.can.DiagnosticRequests
import com.raziya.diagnostics.can.IsoTpReassembler
import com.raziya.diagnostics.can.LiveReading
import com.raziya.diagnostics.can.ObdDecoder
import com.raziya.diagnostics.can.VehicleStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FrameLog(val direction: String, val frame: CanFrame, val timestamp: Long = System.currentTimeMillis())

data class VehicleProfile(
    val vehicleName: String,
    val registrationNumber: String,
    val clientName: String = "",
    val clientPhone: String = "",
    val odometerKm: String = "",
)

data class DiagnosticsState(
    val vehicleProfile: VehicleProfile? = null,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val deviceName: String? = null,
    val readings: LiveReading = LiveReading(),
    val vehicleStatus: VehicleStatus = VehicleStatus(),
    val vin: String? = null,
    val dtcs: List<String> = emptyList(),
    val frames: List<FrameLog> = emptyList(),
    val error: String? = null,
    val scanning: Boolean = false,
    val devices: List<BluetoothDevice> = emptyList(),
    val healthScanRunning: Boolean = false,
    val scanCompletedAt: Long? = null,
)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    private val client = RawCanBluetoothClient(application)
    private val isoTpById = mutableMapOf<Int, IsoTpReassembler>()
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()
    private var polling: Job? = null

    fun pairedDevices(): List<BluetoothDevice> = runCatching { client.bondedDevices() }.getOrDefault(emptyList())

    fun selectVehicle(profile: VehicleProfile) = _state.update { it.copy(vehicleProfile = profile, error = null) }

    fun changeVehicle() {
        disconnect()
        _state.value = DiagnosticsState()
    }

    fun startBluetoothScan() {
        _state.update { it.copy(scanning = true, devices = emptyList(), error = null) }
        client.startDiscovery(
            onDevice = { device ->
                _state.update { current ->
                    val updated = (current.devices + device).distinctBy { it.address }
                        .sortedWith(compareByDescending<BluetoothDevice> { it.name == "VehicleSim-OBD" }.thenBy { it.name ?: it.address })
                    current.copy(devices = updated)
                }
            },
            onFinished = { error -> _state.update { it.copy(scanning = false, error = error) } },
        )
    }

    fun stopBluetoothScan() {
        client.stopDiscovery()
        _state.update { it.copy(scanning = false) }
    }

    fun connect(device: BluetoothDevice) {
        stopBluetoothScan()
        _state.update { it.copy(connecting = true, error = null, deviceName = device.name ?: device.address) }
        client.connect(device, ::receive) { connected, error ->
            _state.update { it.copy(connected = connected, connecting = false, error = error) }
            // Connection milestone only: do not transmit CAN requests automatically.
            // Live polling will be enabled after the Bluetooth link is verified on hardware.
            if (!connected) polling?.cancel()
        }
    }

    fun disconnect() {
        polling?.cancel()
        client.disconnect()
        _state.update { it.copy(connected = false, connecting = false) }
    }

    fun send(request: DiagnosticRequest) = send(request.request)

    fun runHealthScan() {
        if (!_state.value.connected || _state.value.healthScanRunning) return
        polling?.cancel()
        _state.update { it.copy(healthScanRunning = true, scanCompletedAt = null, dtcs = emptyList(), error = null) }
        viewModelScope.launch {
            listOf(DiagnosticRequests.supportedPids, DiagnosticRequests.storedDtcs,
                DiagnosticRequests.extendedSession, DiagnosticRequests.vin, DiagnosticRequests.udsDtcs)
                .forEach { request -> send(request); delay(250) }
            DiagnosticRequests.live.forEach { request -> send(request); delay(220) }
            delay(500)
            _state.update { it.copy(healthScanRunning = false, scanCompletedAt = System.currentTimeMillis()) }
            // The Bluetooth and diagnostic paths are now verified. Begin the
            // lightweight live-data loop only after the mechanic starts a scan.
            startPolling()
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun reportError(message: String) = _state.update { it.copy(error = message) }

    private fun startPolling() {
        polling?.cancel()
        polling = viewModelScope.launch {
            while (isActive) {
                DiagnosticRequests.live.forEach { send(it); delay(180) }
                delay(650)
            }
        }
    }

    private fun send(frame: CanFrame) {
        runCatching { client.send(frame) }
            .onSuccess { log("TX", frame) }
            .onFailure { error -> _state.update { it.copy(error = error.message) } }
    }

    private fun receive(frame: CanFrame) {
        log("RX", frame)
        when (val result = isoTpById.getOrPut(frame.id) { IsoTpReassembler() }.accept(frame)) {
            is IsoTpReassembler.Result.Complete -> decode(result.payload)
            is IsoTpReassembler.Result.FlowControlRequired -> send(result.frame)
            is IsoTpReassembler.Result.Invalid -> _state.update { it.copy(error = result.reason) }
            IsoTpReassembler.Result.Pending -> Unit
        }
    }

    private fun decode(payload: ByteArray) {
        _state.update { current ->
            val vin = ObdDecoder.decodeVin(payload) ?: current.vin
            val dtcs = ObdDecoder.decodeObdDtcs(payload).ifEmpty { current.dtcs }
            current.copy(
                readings = ObdDecoder.update(payload, current.readings),
                vehicleStatus = ObdDecoder.updateVehicleStatus(payload, current.vehicleStatus),
                vin = vin,
                dtcs = dtcs,
            )
        }
    }

    private fun log(direction: String, frame: CanFrame) = _state.update {
        it.copy(frames = (listOf(FrameLog(direction, frame)) + it.frames).take(100))
    }

    override fun onCleared() { client.disconnect() }
}
