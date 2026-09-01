package com.raziya.diagnostics.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.raziya.diagnostics.DiagnosticsState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReportGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    fun generate(context: Context, state: DiagnosticsState): File {
        val issues = DtcCatalog.resolve(state.dtcs)
        val healthScore = (100 - issues.sumOf { it.severity.scorePenalty }).coerceIn(0, 100)
        val pdf = PdfDocument()
        var pageNumber = 0
        var page = newPage(pdf, ++pageNumber)
        var canvas = page.page.canvas
        var y = 54f
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 45, 41); textSize = 10f }
        val muted = Paint(normal).apply { color = Color.rgb(92, 108, 101); textSize = 9f }
        val heading = Paint(normal).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD }
        val section = Paint(normal).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        val issueTitle = Paint(normal).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD }

        fun nextPage() {
            pdf.finishPage(page.page)
            page = newPage(pdf, ++pageNumber)
            canvas = page.page.canvas
            y = 48f
        }
        fun line(text: String, paint: Paint = normal, gap: Float = 15f) {
            if (y > PAGE_HEIGHT - 48) nextPage()
            canvas.drawText(text, 42f, y, paint)
            y += gap
        }
        fun wrapped(text: String, paint: Paint = normal, width: Int = 88) {
            val words = text.split(" ")
            var current = ""
            words.forEach { word ->
                if ((current.length + word.length + 1) > width) { line(current, paint, 13f); current = word }
                else current = if (current.isEmpty()) word else "$current $word"
            }
            if (current.isNotEmpty()) line(current, paint, 13f)
        }

        line("VEHICLE DIAGNOSTIC REPORT", heading, 30f)
        line("Prepared by RAZIYA Diagnostics", muted, 18f)
        line("Report time: ${formatTime(state.scanCompletedAt ?: System.currentTimeMillis())}")
        line("Vehicle: ${state.vehicleProfile?.vehicleName ?: "Not provided"}")
        line("Registration: ${state.vehicleProfile?.registrationNumber ?: "Not provided"}")
        if (!state.vehicleProfile?.odometerKm.isNullOrBlank()) line("Odometer: ${state.vehicleProfile?.odometerKm} km")
        if (!state.vehicleProfile?.clientName.isNullOrBlank()) line("Client: ${state.vehicleProfile?.clientName}")
        if (!state.vehicleProfile?.clientPhone.isNullOrBlank()) line("Client phone: ${state.vehicleProfile?.clientPhone}")
        line("Vehicle VIN: ${state.vin ?: "Not available"}")
        line("Diagnostic source: ${state.deviceName ?: "Bluetooth vehicle interface"}")
        y += 8
        line("OVERALL ASSESSMENT", section, 20f)
        line("Health score: $healthScore / 100", issueTitle)
        val disposition = when {
            issues.any { it.severity == Severity.CRITICAL } -> "DO NOT CONTINUE DRIVING — critical faults require inspection."
            issues.any { it.severity == Severity.HIGH } -> "SERVICE URGENTLY — restrict driving until inspected."
            issues.isNotEmpty() -> "SERVICE RECOMMENDED — faults require scheduled diagnosis."
            else -> "NO STORED FAULTS DETECTED."
        }
        wrapped("Recommendation: $disposition", issueTitle)
        y += 8
        line("LIVE DATA SNAPSHOT", section, 20f)
        line("Engine speed: ${state.readings.rpm?.let { "$it RPM" } ?: "Unavailable"}")
        line("Vehicle speed: ${state.readings.speedKph?.let { "$it km/h" } ?: "Unavailable"}")
        line("Coolant temperature: ${state.readings.coolantC?.let { "$it °C" } ?: "Unavailable"}")
        line("Engine load: ${state.readings.engineLoadPercent?.let { "$it %" } ?: "Unavailable"}")
        line("Intake temperature: ${state.readings.intakeTemperatureC?.let { "$it °C" } ?: "Unavailable"}")
        line("Control-module voltage: ${state.readings.controlModuleVoltage?.let { "%.2f V".format(it) } ?: "Unavailable"}")
        line("Fuel level: ${state.readings.fuelLevelPercent?.let { "$it %" } ?: "Unavailable"}")
        line("Throttle: ${state.readings.throttlePercent?.let { "$it %" } ?: "Unavailable"}")
        line("Engine oil temperature: ${state.readings.oilTemperatureC?.let { "$it °C" } ?: "Unavailable"}")
        line("Fuel rate: ${state.readings.fuelRateLph?.let { "%.2f L/h".format(it) } ?: "Unavailable"}")
        y += 8
        line("BODY, TYRES & DRIVETRAIN", section, 20f)
        val openPanels = buildList {
            if (state.vehicleStatus.frontLeftDoorOpen) add("front-left door")
            if (state.vehicleStatus.frontRightDoorOpen) add("front-right door")
            if (state.vehicleStatus.rearLeftDoorOpen) add("rear-left door")
            if (state.vehicleStatus.rearRightDoorOpen) add("rear-right door")
            if (state.vehicleStatus.hoodOpen) add("hood")
            if (state.vehicleStatus.tailgateOpen) add("tailgate")
        }
        line("Open panels: ${openPanels.ifEmpty { listOf("None reported") }.joinToString()}")
        line("Central locking: ${if (state.vehicleStatus.locked) "Locked" else "Unlocked"}")
        val tyreNames = listOf("FL", "FR", "RL", "RR")
        tyreNames.forEachIndexed { index, name ->
            val pressure = state.vehicleStatus.tyrePressureKpa[index]?.let { "$it kPa" } ?: "Unavailable"
            val temperature = state.vehicleStatus.tyreTemperatureC[index]?.let { "$it °C" } ?: "Unavailable"
            line("Tyre $name: $pressure, $temperature")
        }
        line("Selected gear: ${state.vehicleStatus.selectedGear ?: "Unavailable"}")
        line("Transmission temperature: ${state.vehicleStatus.transmissionTemperatureC?.let { "$it °C" } ?: "Unavailable"}")
        y += 8
        line("DETECTED ISSUES (${issues.size})", section, 22f)
        if (issues.isEmpty()) line("No stored diagnostic trouble codes were reported.")
        issues.forEachIndexed { index, issue ->
            if (y > PAGE_HEIGHT - 145) nextPage()
            line("${index + 1}. ${issue.code} — ${issue.title}", issueTitle, 16f)
            line("Severity: ${issue.severity.label}    System: ${issue.system}", muted, 14f)
            wrapped(issue.explanation)
            wrapped("Recommended action: ${issue.recommendation}", normal)
            y += 9
        }
        if (y > PAGE_HEIGHT - 150) nextPage()
        line("TECHNICAL EVIDENCE", section, 20f)
        line("CAN frames retained by app: ${state.frames.size}")
        state.frames.take(12).reversed().forEach { log ->
            line("${log.direction}  %03X  [%d]  %s".format(log.frame.id, log.frame.dlc, log.frame.hex), muted, 12f)
        }
        y += 8
        line("NOTICE", section, 18f)
        wrapped("This report records ECU responses observed during the scan. A DTC identifies the system that detected a problem; it does not by itself prove which component must be replaced. Confirm findings with physical inspection and authorized service information.", muted)
        pdf.finishPage(page.page)

        val reports = File(context.cacheDir, "diagnostic-reports").apply { mkdirs() }
        val file = File(reports, "Vehicle-Diagnostic-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.pdf")
        FileOutputStream(file).use(pdf::writeTo)
        pdf.close()
        return file
    }

    private data class Page(val page: PdfDocument.Page)
    private fun newPage(pdf: PdfDocument, number: Int): Page {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 14f, Paint().apply { color = Color.rgb(50, 214, 145) })
        return Page(page)
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
