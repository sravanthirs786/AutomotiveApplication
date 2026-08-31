package com.raziya.diagnostics.can

data class DiagnosticRequest(
    val name: String,
    val request: CanFrame,
    val responseId: Int = 0x7E8,
)

object DiagnosticRequests {
    val supportedPids = DiagnosticRequest("Supported PIDs", CanFrame.of(0x7DF, 0x02, 0x01, 0x00))
    val rpm = DiagnosticRequest("Engine RPM", CanFrame.of(0x7DF, 0x02, 0x01, 0x0C))
    val speed = DiagnosticRequest("Vehicle speed", CanFrame.of(0x7DF, 0x02, 0x01, 0x0D))
    val coolant = DiagnosticRequest("Coolant temperature", CanFrame.of(0x7DF, 0x02, 0x01, 0x05))
    val engineLoad = DiagnosticRequest("Calculated engine load", CanFrame.of(0x7DF, 0x02, 0x01, 0x04))
    val intakeTemperature = DiagnosticRequest("Intake temperature", CanFrame.of(0x7DF, 0x02, 0x01, 0x0F))
    val controlModuleVoltage = DiagnosticRequest("Control module voltage", CanFrame.of(0x7DF, 0x02, 0x01, 0x42))
    val storedDtcs = DiagnosticRequest("Stored DTCs", CanFrame.of(0x7DF, 0x01, 0x03))
    val vin = DiagnosticRequest("Vehicle VIN", CanFrame.of(0x7E0, 0x03, 0x22, 0xF1, 0x90))
    val udsDtcs = DiagnosticRequest("UDS DTC report", CanFrame.of(0x7E0, 0x03, 0x19, 0x02, 0xFF))
    val extendedSession = DiagnosticRequest("Extended session", CanFrame.of(0x7E0, 0x02, 0x10, 0x03))

    val live = listOf(rpm, speed, coolant, engineLoad, intakeTemperature, controlModuleVoltage)
    val all = listOf(supportedPids, rpm, speed, coolant, engineLoad, intakeTemperature,
        controlModuleVoltage, storedDtcs, extendedSession, vin, udsDtcs)
}
