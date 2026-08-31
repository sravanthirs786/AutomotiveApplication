package com.raziya.diagnostics.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.raziya.diagnostics.can.CanFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID

class RawCanBluetoothClient(context: Context) {
    private val appContext = context.applicationContext
    private val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var connection: android.bluetooth.BluetoothSocket? = null
    private var reader: Job? = null
    private var discoveryReceiver: BroadcastReceiver? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.sortedBy { it.name } ?: emptyList()

    @SuppressLint("MissingPermission")
    fun startDiscovery(onDevice: (BluetoothDevice) -> Unit, onFinished: (String?) -> Unit) {
        stopDiscovery()
        val bluetooth = adapter
        if (bluetooth == null) {
            onFinished("Bluetooth is not available on this phone")
            return
        }
        bluetooth.bondedDevices.forEach(onDevice)
        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> intent.bluetoothDevice()?.let(onDevice)
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        unregisterDiscoveryReceiver()
                        onFinished(null)
                    }
                }
            }
        }.also { receiver ->
            appContext.registerReceiver(receiver, IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            })
        }
        if (!bluetooth.startDiscovery()) {
            unregisterDiscoveryReceiver()
            onFinished("Android could not start Bluetooth discovery")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        runCatching { if (adapter?.isDiscovering == true) adapter.cancelDiscovery() }
        unregisterDiscoveryReceiver()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, onFrame: (CanFrame) -> Unit, onState: (Boolean, String?) -> Unit) {
        disconnect()
        reader = scope.launch {
            try {
                adapter?.cancelDiscovery()
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                connection = socket
                onState(true, null)
                readFrames(socket.inputStream, onFrame)
            } catch (error: Exception) {
                onState(false, error.message ?: "Bluetooth connection failed")
                disconnect()
            }
        }
    }

    @Synchronized
    fun send(frame: CanFrame) {
        val socket = connection ?: error("Bluetooth is not connected")
        socket.outputStream.write(frame.encode())
        socket.outputStream.flush()
    }

    fun disconnect() {
        stopDiscovery()
        reader?.cancel()
        reader = null
        runCatching { connection?.close() }
        connection = null
    }

    private suspend fun readFrames(input: InputStream, onFrame: (CanFrame) -> Unit) {
        val packet = ByteArray(CanFrame.WIRE_SIZE)
        var offset = 0
        while (scope.isActive) {
            val count = input.read(packet, offset, packet.size - offset)
            if (count < 0) error("Bluetooth connection closed")
            offset += count
            if (offset == packet.size) {
                onFrame(CanFrame.decode(packet.copyOf()))
                offset = 0
            }
        }
    }

    private fun unregisterDiscoveryReceiver() {
        val receiver = discoveryReceiver ?: return
        runCatching { appContext.unregisterReceiver(receiver) }
        discoveryReceiver = null
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
