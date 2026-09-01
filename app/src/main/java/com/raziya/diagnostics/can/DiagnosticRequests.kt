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
    val manifoldPressure = DiagnosticRequest("Intake manifold pressure", CanFrame.of(0x7DF, 0x02, 0x01, 0x0B))
    val massAirFlow = DiagnosticRequest("Mass air flow", CanFrame.of(0x7DF, 0x02, 0x01, 0x10))
    val throttle = DiagnosticRequest("Throttle position", CanFrame.of(0x7DF, 0x02, 0x01, 0x11))
    val fuelLevel = DiagnosticRequest("Fuel level", CanFrame.of(0x7DF, 0x02, 0x01, 0x2F))
    val ambientTemperature = DiagnosticRequest("Ambient temperature", CanFrame.of(0x7DF, 0x02, 0x01, 0x46))
    val oilTemperature = DiagnosticRequest("Engine oil temperature", CanFrame.of(0x7DF, 0x02, 0x01, 0x5C))
    val fuelRate = DiagnosticRequest("Engine fuel rate", CanFrame.of(0x7DF, 0x02, 0x01, 0x5E))
    val bodyStatus = DiagnosticRequest("Door and body status", CanFrame.of(0x7E3, 0x03, 0x22, 0xD1, 0x00), 0x7EB)
    val tyrePressure = DiagnosticRequest("Tyre pressure", CanFrame.of(0x7E4, 0x03, 0x22, 0xD2, 0x00), 0x7EC)
    val tyreTemperature = DiagnosticRequest("Tyre temperature", CanFrame.of(0x7E4, 0x03, 0x22, 0xD2, 0x01), 0x7EC)
    val wheelSpeed = DiagnosticRequest("Wheel speeds", CanFrame.of(0x7E2, 0x03, 0x22, 0xD3, 0x00), 0x7EA)
    val transmission = DiagnosticRequest("Transmission status", CanFrame.of(0x7E1, 0x03, 0x22, 0xD4, 0x00), 0x7E9)
    val storedDtcs = DiagnosticRequest("Stored DTCs", CanFrame.of(0x7DF, 0x01, 0x03))
    val vin = DiagnosticRequest("Vehicle VIN", CanFrame.of(0x7E0, 0x03, 0x22, 0xF1, 0x90))
    val udsDtcs = DiagnosticRequest("UDS DTC report", CanFrame.of(0x7E0, 0x03, 0x19, 0x02, 0xFF))
    val extendedSession = DiagnosticRequest("Extended session", CanFrame.of(0x7E0, 0x02, 0x10, 0x03))

    val live = listOf(rpm, speed, coolant, engineLoad, intakeTemperature, controlModuleVoltage,
        manifoldPressure, massAirFlow, throttle, fuelLevel, ambientTemperature, oilTemperature,
        fuelRate, bodyStatus, tyrePressure, tyreTemperature, wheelSpeed, transmission)
    val all = listOf(supportedPids, rpm, speed, coolant, engineLoad, intakeTemperature,
        controlModuleVoltage, storedDtcs, extendedSession, vin, udsDtcs)
}
