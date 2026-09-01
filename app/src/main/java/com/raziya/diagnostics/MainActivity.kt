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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.TireRepair
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.raziya.diagnostics.report.DiagnosticReportGenerator
import com.raziya.diagnostics.report.DtcCatalog
import com.raziya.diagnostics.report.Severity

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

private enum class AppSection(val label: String) { OVERVIEW("Home"), LIVE("Live"), DIAGNOSE("Scan"), VEHICLE("Vehicle"), REPORTS("Reports") }

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
    val snackbarHostState = remember { SnackbarHostState() }
    var showDevices by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(AppSection.OVERVIEW) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) {
            showDevices = true
            vm.startBluetoothScan()
        }
    }
    val requestConnection = {
        val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionLauncher.launch(permissions)
    }

    if (state.vehicleProfile == null) {
        VehicleIntakeScreen(onContinue = vm::selectVehicle)
        return
    }

    val shareReport: () -> Unit = {
        runCatching {
            val report = DiagnosticReportGenerator.generate(context, state)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Vehicle diagnostic report — ${state.vin ?: "VIN unavailable"}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share diagnostic report"))
        }.onFailure { error -> vm.reportError(error.message ?: "Unable to create diagnostic report") }
    }

    LaunchedEffect(state.scanCompletedAt) {
        if (state.scanCompletedAt != null) {
            section = AppSection.LIVE
            snackbarHostState.showSnackbar(
                message = "Diagnosis complete — ${state.dtcs.size} issues detected. Report ready.",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
        }
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0C1814)) {
                AppSection.entries.forEach { item ->
                    val icon = when (item) {
                        AppSection.OVERVIEW -> Icons.Rounded.Dashboard
                        AppSection.LIVE -> Icons.Rounded.Speed
                        AppSection.DIAGNOSE -> Icons.Rounded.FactCheck
                        AppSection.VEHICLE -> Icons.Rounded.DirectionsCar
                        AppSection.REPORTS -> Icons.Rounded.Description
                    }
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item },
                        icon = { Icon(icon, item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, indicatorColor = Mint),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Header(state.vehicleProfile, vm::changeVehicle) }
            when (section) {
                AppSection.OVERVIEW -> {
                    item { HeroCard(state, onConnect = requestConnection, onDisconnect = vm::disconnect, onScan = vm::runHealthScan) }
                    item { SectionTitle("AT A GLANCE", "The essentials for roadside triage") }
                    item { TelemetryGrid(state.readings, compact = true) }
                    item { QuickAlerts(state) { section = AppSection.VEHICLE } }
                }
                AppSection.LIVE -> {
                    item { SectionTitle("LIVE DATA", "Standard OBD-II parameters from the engine ECU") }
                    item { TelemetryGrid(state.readings) }
                }
                AppSection.DIAGNOSE -> {
                    item { SectionTitle("COMPLETE DIAGNOSIS", "Scan emissions and lab ECU modules") }
                    item { HeroCard(state, onConnect = requestConnection, onDisconnect = vm::disconnect, onScan = vm::runHealthScan) }
                    if (state.scanCompletedAt != null) item { DiagnosticReportCard(state, shareReport) }
                }
                AppSection.VEHICLE -> {
                    item { SectionTitle("BODY & CHASSIS", "Lab BCM, TPMS, ABS and transmission data") }
                    item { DoorStatusCard(state.vehicleStatus) }
                    item { TyreStatusCard(state.vehicleStatus) }
                    item { DrivetrainCard(state.vehicleStatus) }
                }
                AppSection.REPORTS -> {
                    item { SectionTitle("REPORTS", "Keep diagnosis separate from client documents") }
                    if (state.scanCompletedAt != null) item { DiagnosticReportCard(state, shareReport) }
                    else item { EmptyReportCard { section = AppSection.DIAGNOSE } }
                }
            }
        }
    }

    if (showDevices) DevicePicker(state, vm, onDismiss = { vm.stopBluetoothScan(); showDevices = false })
    LaunchedEffect(state.connected) {
        if (state.connected) showDevices = false
    }
    state.error?.let { error ->
        AlertDialog(onDismissRequest = vm::dismissError, confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
            icon = { Icon(Icons.Rounded.ErrorOutline, null) }, title = { Text("Connection issue") }, text = { Text(error) })
    }
}

@Composable
private fun VehicleIntakeScreen(onContinue: (VehicleProfile) -> Unit) {
    var vehicleName by rememberSaveable { mutableStateOf("") }
    var registration by rememberSaveable { mutableStateOf("") }
    var clientName by rememberSaveable { mutableStateOf("") }
    var clientPhone by rememberSaveable { mutableStateOf("") }
    var odometer by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val valid = vehicleName.isNotBlank() && registration.isNotBlank()

    Scaffold(containerColor = Ink) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.size(58.dp).background(Mint, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CarRepair, null, tint = Ink, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text("Start a diagnosis", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text("Identify the vehicle before connecting the diagnostic adapter.", color = Muted, style = MaterialTheme.typography.bodyLarge)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("VEHICLE DETAILS", color = Mint, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(value = vehicleName, onValueChange = { vehicleName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Vehicle name / model *") }, placeholder = { Text("Example: Kia Carens") }, leadingIcon = { Icon(Icons.Rounded.DirectionsCar, null) }, singleLine = true, isError = attempted && vehicleName.isBlank())
                        OutlinedTextField(value = registration, onValueChange = { registration = it.uppercase() }, modifier = Modifier.fillMaxWidth(), label = { Text("Registration number *") }, placeholder = { Text("Example: TS 09 AB 1234") }, leadingIcon = { Icon(Icons.Rounded.Badge, null) }, singleLine = true, isError = attempted && registration.isBlank())
                        OutlinedTextField(value = odometer, onValueChange = { odometer = it.filter(Char::isDigit).take(7) }, modifier = Modifier.fillMaxWidth(), label = { Text("Odometer (optional)") }, suffix = { Text("km") }, singleLine = true)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("CLIENT DETAILS", color = Mint, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                        Text("Optional — included in the diagnostic report.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = clientName, onValueChange = { clientName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Client name") }, leadingIcon = { Icon(Icons.Rounded.Person, null) }, singleLine = true)
                        OutlinedTextField(value = clientPhone, onValueChange = { clientPhone = it.filter { char -> char.isDigit() || char == '+' }.take(15) }, modifier = Modifier.fillMaxWidth(), label = { Text("Phone number") }, singleLine = true)
                    }
                }
            }
            item {
                Button(onClick = {
                    attempted = true
                    if (valid) onContinue(VehicleProfile(vehicleName.trim(), registration.trim(), clientName.trim(), clientPhone.trim(), odometer.trim()))
                }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("CONTINUE TO BLUETOOTH", fontWeight = FontWeight.Black) }
                if (attempted && !valid) Text("Vehicle name and registration number are required.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Header(profile: VehicleProfile?, onChangeVehicle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(44.dp).background(Mint, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CarRepair, null, tint = Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile?.vehicleName ?: "Vehicle", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(profile?.registrationNumber ?: "Registration unavailable", color = Mint, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onChangeVehicle) { Text("CHANGE", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun HeroCard(state: DiagnosticsState, onConnect: () -> Unit, onDisconnect: () -> Unit, onScan: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (state.connected) Color(0xFF123B2C) else Surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state.connected) Icons.Rounded.CheckCircle else Icons.Rounded.Memory, null, tint = if (state.connected) Mint else Muted, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(when { state.connecting -> "Connecting to vehicle…"; state.connected -> "Diagnostic link ready"; else -> "Connect to VehicleSim-OBD" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(if (state.connected) "Connected • ${state.deviceName ?: "Bluetooth interface"}" else "Scan for the Raspberry Pi diagnostic interface", color = Muted)
                }
            }
            Spacer(Modifier.height(20.dp))
            when {
                state.healthScanRunning -> {
                    LinearProgressIndicator(progress = { state.scanProgress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Column { Text(state.scanStatus ?: "Scanning vehicle…", fontWeight = FontWeight.Bold); Text("${(state.scanProgress * 100).toInt()}% complete", color = Muted, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                !state.connected -> Button(onClick = onConnect, enabled = !state.connecting, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    if (state.connecting) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Bluetooth, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp)); Text(if (state.connecting) "CONNECTING…" else "SCAN & CONNECT", fontWeight = FontWeight.Bold)
                }
                else -> {
                    Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("RUN COMPLETE HEALTH SCAN", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.End)) { Text("DISCONNECT") }
                }
            }
        }
    }
}

@Composable
private fun TelemetryGrid(readings: com.raziya.diagnostics.can.LiveReading, compact: Boolean = false) {
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
        if (!compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("FUEL LEVEL", readings.fuelLevelPercent?.toString() ?: "—", "%", Modifier.weight(1f))
                Metric("THROTTLE", readings.throttlePercent?.toString() ?: "—", "%", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("MANIFOLD", readings.intakeManifoldKpa?.toString() ?: "—", "kPa", Modifier.weight(1f))
                Metric("AIR FLOW", readings.massAirFlowGps?.let { "%.1f".format(it) } ?: "—", "g/s", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("OIL TEMP", readings.oilTemperatureC?.toString() ?: "—", "°C", Modifier.weight(1f))
                Metric("AMBIENT", readings.ambientTemperatureC?.toString() ?: "—", "°C", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("FUEL RATE", readings.fuelRateLph?.let { "%.1f".format(it) } ?: "—", "L/h", Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column { Text(title, color = Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black); Text(subtitle, color = Muted) }
}

@Composable
private fun QuickAlerts(state: DiagnosticsState, onOpen: () -> Unit) {
    val lowTyres = state.vehicleStatus.tyrePressureKpa.count { it != null && it < 190 }
    val openDoors = listOf(state.vehicleStatus.frontLeftDoorOpen, state.vehicleStatus.frontRightDoorOpen,
        state.vehicleStatus.rearLeftDoorOpen, state.vehicleStatus.rearRightDoorOpen, state.vehicleStatus.hoodOpen,
        state.vehicleStatus.tailgateOpen).count { it }
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = if (lowTyres + openDoors > 0) Warning else Mint)
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                Text("Vehicle alerts", fontWeight = FontWeight.Bold)
                Text(if (state.scanCompletedAt == null) "Run a scan to read body and tyre status" else "$openDoors open panels • $lowTyres low tyres", color = Muted)
            }; Text("VIEW", color = Mint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DoorStatusCard(status: com.raziya.diagnostics.can.VehicleStatus) {
    val panels = listOf("Front left" to status.frontLeftDoorOpen, "Front right" to status.frontRightDoorOpen,
        "Rear left" to status.rearLeftDoorOpen, "Rear right" to status.rearRightDoorOpen,
        "Hood" to status.hoodOpen, "Tailgate" to status.tailgateOpen)
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (status.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, tint = Mint); Spacer(Modifier.width(10.dp)); Text("Doors & access", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(14.dp)); panels.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (name, open) ->
                    Surface(Modifier.weight(1f).padding(vertical = 4.dp), color = if (open) Warning.copy(alpha = .12f) else Color.White.copy(alpha = .04f), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) { Text(name, color = Muted, style = MaterialTheme.typography.labelMedium); Text(if (open) "OPEN" else "Closed", color = if (open) Warning else Mint, fontWeight = FontWeight.Bold) }
                    }
                } }
            }
        }
    }
}

@Composable
private fun TyreStatusCard(status: com.raziya.diagnostics.can.VehicleStatus) {
    val names = listOf("Front left", "Front right", "Rear left", "Rear right")
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.TireRepair, null, tint = Mint); Spacer(Modifier.width(10.dp)); Text("Tyre pressure", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(14.dp)); names.forEachIndexed { i, name ->
                val pressure = status.tyrePressureKpa[i]; val low = pressure != null && pressure < 190
                ListItem(headlineContent = { Text(name) }, supportingContent = { Text(status.tyreTemperatureC[i]?.let { "$it °C" } ?: "Temperature unavailable") }, trailingContent = { Text(pressure?.let { "$it kPa" } ?: "—", color = if (low) Warning else Mint, fontWeight = FontWeight.Black) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            Text("Thresholds are lab settings. Real TPMS availability and limits are vehicle-specific.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DrivetrainCard(status: com.raziya.diagnostics.can.VehicleStatus) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp)) { Row(Modifier.fillMaxWidth().padding(20.dp)) {
        Metric("SELECTED GEAR", status.selectedGear ?: "—", "", Modifier.weight(1f)); Spacer(Modifier.width(10.dp)); Metric("TRANSMISSION", status.transmissionTemperatureC?.toString() ?: "—", "°C", Modifier.weight(1f))
    } }
}

@Composable
private fun EmptyReportCard(onScan: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Description, null, tint = Muted, modifier = Modifier.size(44.dp)); Spacer(Modifier.height(12.dp)); Text("No report yet", fontWeight = FontWeight.Bold); Text("Complete a vehicle scan first.", color = Muted); Spacer(Modifier.height(16.dp)); Button(onClick = onScan) { Text("GO TO DIAGNOSIS") }
    } }
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
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dtcs.chunked(3).forEach { rowCodes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCodes.forEach { code ->
                            AssistChip(onClick = {}, label = { Text(code) }, leadingIcon = { Icon(Icons.Rounded.ErrorOutline, null, tint = Warning) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticReportCard(state: DiagnosticsState, onShare: () -> Unit) {
    val issues = DtcCatalog.resolve(state.dtcs)
    val score = (100 - issues.sumOf { it.severity.scorePenalty }).coerceIn(0, 100)
    val critical = issues.count { it.severity == Severity.CRITICAL }
    val high = issues.count { it.severity == Severity.HIGH }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17251F)), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Mint.copy(alpha = .14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Description, null, tint = Mint)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Diagnostic report", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Scan complete • PDF ready", color = Mint, style = MaterialTheme.typography.bodySmall)
                }
                Text("$score", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("/100", color = Muted, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    critical > 0 -> "$critical critical and $high high-priority issues require immediate attention."
                    high > 0 -> "$high high-priority issues require urgent service."
                    issues.isEmpty() -> "No stored diagnostic faults were detected."
                    else -> "${issues.size} issues require further inspection."
                }, color = if (critical > 0) Warning else Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("CREATE & SHARE PDF", fontWeight = FontWeight.Bold)
            }
            Text("You choose the recipient in Android's share sheet.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 9.dp))
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
