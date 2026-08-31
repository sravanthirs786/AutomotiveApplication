package com.raziya.diagnostics

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.raziya.diagnostics.report.DiagnosticReportGenerator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RaziyaTheme { DiagnosticsApp() } }
    }
}

private val Ink = Color(0xFF07110E)
private val Surface = Color(0xFF10201A)
private val Mint = Color(0xFF60F4B2)
private val Muted = Color(0xFF9AAFA6)
private val Warning = Color(0xFFFFC857)

@Composable
fun RaziyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Mint, background = Ink, surface = Surface, onBackground = Color.White),
        typography = Typography(), content = content
    )
}

@Composable
fun DiagnosticsApp(vm: DiagnosticsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showDevices by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) {
            showDevices = true
            vm.startBluetoothScan()
        }
    }

    Scaffold(containerColor = Ink) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Header(state.connected, state.deviceName) { if (state.connected) vm.disconnect() else {
                val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionLauncher.launch(permissions)
            } } }
            item {
                HeroCard(state, onScan = vm::runHealthScan, onShare = {
                    runCatching {
                        val report = DiagnosticReportGenerator.generate(context, state)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Vehicle diagnostic report — ${state.vin ?: "VIN unavailable"}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share diagnostic report"))
                    }.onFailure { error ->
                        // Surface generation/share failures through the existing app dialog.
                        vm.reportError(error.message ?: "Unable to create diagnostic report")
                    }
                })
            }
            item { Text("LIVE TELEMETRY", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            item { TelemetryGrid(state.readings) }
            item { VehicleIdentity(state.vin, state.dtcs) }
            item { FrameMonitor(state.frames) }
        }
    }

    if (showDevices) DevicePicker(state, vm, onDismiss = { vm.stopBluetoothScan(); showDevices = false })
    state.error?.let { error ->
        AlertDialog(onDismissRequest = vm::dismissError, confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
            icon = { Icon(Icons.Rounded.ErrorOutline, null) }, title = { Text("Connection issue") }, text = { Text(error) })
    }
}

@Composable
private fun Header(connected: Boolean, device: String?, onConnection: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(44.dp).background(Mint, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CarRepair, null, tint = Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("RAZIYA", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(if (connected) device ?: "Vehicle connected" else "Remote vehicle diagnostics", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        FilledTonalButton(onClick = onConnection) {
            Icon(if (connected) Icons.Rounded.Close else Icons.Rounded.Bluetooth, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp)); Text(if (connected) "Disconnect" else "Connect")
        }
    }
}

@Composable
private fun HeroCard(state: DiagnosticsState, onScan: () -> Unit, onShare: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (state.connected) Color(0xFF123B2C) else Surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state.connected) Icons.Rounded.CheckCircle else Icons.Rounded.Memory, null, tint = if (state.connected) Mint else Muted, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (state.connected) "Diagnostic link ready" else "Connect to VehicleSim-OBD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(if (state.connected) "Binary CAN • ISO-TP • 500 kbit/s" else "Pair the Raspberry Pi in Android settings first", color = Muted)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onScan, enabled = state.connected && !state.healthScanRunning, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                if (state.healthScanRunning) {
                    CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                }
                Text(if (state.healthScanRunning) "SCANNING VEHICLE…" else "RUN COMPLETE HEALTH SCAN", fontWeight = FontWeight.Bold)
            }
            if (state.scanCompletedAt != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("SHARE CLIENT PDF REPORT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TelemetryGrid(readings: com.raziya.diagnostics.can.LiveReading) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("ENGINE SPEED", readings.rpm?.toString() ?: "—", "RPM", Modifier.weight(1f))
            Metric("VEHICLE SPEED", readings.speedKph?.toString() ?: "—", "km/h", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("COOLANT", readings.coolantC?.toString() ?: "—", "°C", Modifier.weight(1f))
            Metric("ENGINE LOAD", readings.engineLoadPercent?.toString() ?: "—", "%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("INTAKE AIR", readings.intakeTemperatureC?.toString() ?: "—", "°C", Modifier.weight(1f))
            Metric("MODULE VOLTAGE", readings.controlModuleVoltage?.let { "%.1f".format(it) } ?: "—", "V", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(17.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(5.dp)); Text(unit, color = Muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun VehicleIdentity(vin: String?, dtcs: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("VEHICLE HEALTH", color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp)); Text(vin ?: "VIN not read", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp)); HorizontalDivider(color = Color.White.copy(alpha = .08f)); Spacer(Modifier.height(14.dp))
            if (dtcs.isEmpty()) Text("No stored trouble codes", color = Mint)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { dtcs.forEach { AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Icons.Rounded.ErrorOutline, null, tint = Warning) }) } }
        }
    }
}

@Composable
private fun FrameMonitor(frames: List<FrameLog>) {
    Column {
        Text("CAN FRAME MONITOR", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1712)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                if (frames.isEmpty()) Text("Frames will appear after connection", color = Muted, modifier = Modifier.padding(8.dp))
                frames.take(8).forEach { log ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(log.direction, color = if (log.direction == "TX") Warning else Mint, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                        Text("%03X".format(log.frame.id), fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
                        Text("[${log.frame.dlc}]  ${log.frame.hex}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Suppress("MissingPermission")
@Composable
private fun DevicePicker(state: DiagnosticsState, vm: DiagnosticsViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Connect to Raspberry Pi")
                Text(
                    if (state.scanning) "Scanning for nearby Bluetooth devices…" else "Scan finished",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
                if (state.scanning) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        text = {
            if (state.devices.isEmpty()) {
                Text(if (state.scanning) "Keep VehicleSim-OBD discoverable on the Raspberry Pi." else "VehicleSim-OBD was not found. Make the Pi discoverable and scan again.")
            } else LazyColumn {
                items(state.devices, key = { it.address }) { device ->
                    val isPi = device.name == "VehicleSim-OBD"
                    Card(
                        onClick = { vm.connect(device); onDismiss() },
                        colors = CardDefaults.cardColors(containerColor = if (isPi) Color(0xFF173E30) else Color.Transparent),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        ListItem(
                            headlineContent = { Text(device.name ?: "Unnamed Bluetooth device", fontWeight = if (isPi) FontWeight.Bold else FontWeight.Normal) },
                            supportingContent = { Text(device.address) },
                            leadingContent = { Icon(Icons.Rounded.Bluetooth, null, tint = if (isPi) Mint else Muted) },
                            trailingContent = { Text("CONNECT", color = Mint, style = MaterialTheme.typography.labelMedium) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.startBluetoothScan() }, enabled = !state.scanning) { Text("SCAN AGAIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}
