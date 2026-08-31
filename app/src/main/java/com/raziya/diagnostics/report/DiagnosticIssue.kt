package com.raziya.diagnostics.report

enum class Severity(val label: String, val scorePenalty: Int) {
    CRITICAL("Critical", 24), HIGH("High", 16), MEDIUM("Medium", 9), LOW("Low", 4)
}

data class DiagnosticIssue(
    val code: String,
    val title: String,
    val severity: Severity,
    val system: String,
    val explanation: String,
    val recommendation: String,
)

object DtcCatalog {
    private val issues = listOf(
        DiagnosticIssue("P0300", "Random/multiple-cylinder misfire", Severity.CRITICAL, "Engine",
            "Combustion is unstable across one or more cylinders. Continued driving can damage the catalyst.",
            "Stop heavy-load driving. Inspect ignition, plugs, coils, injectors, compression and intake leaks."),
        DiagnosticIssue("P0117", "Coolant-temperature circuit low", Severity.HIGH, "Cooling system",
            "The ECM sees an implausibly low sensor-circuit voltage while the simulated engine is overheating.",
            "Check coolant level only when safe, sensor wiring, connector, thermostat, pump and cooling fan."),
        DiagnosticIssue("P0562", "System voltage low", Severity.HIGH, "Electrical",
            "Control-module supply voltage is below the expected operating range.",
            "Load-test the battery and inspect alternator output, belt, terminals, grounds and charging cables."),
        DiagnosticIssue("P0420", "Catalyst efficiency below threshold", Severity.MEDIUM, "Emissions",
            "Catalyst oxygen-storage performance is below the expected threshold, possibly caused by the misfire.",
            "Repair the misfire first, then inspect exhaust leaks, oxygen sensors and catalyst efficiency."),
        DiagnosticIssue("C0035", "Left-front wheel-speed sensor fault", Severity.HIGH, "ABS/chassis",
            "The left-front wheel-speed signal disagrees with the other wheels.",
            "Inspect sensor clearance, wiring, connector, tone ring and wheel bearing before relying on ABS."),
        DiagnosticIssue("U0100", "Lost communication with ECM/PCM", Severity.CRITICAL, "Vehicle network",
            "Another control module reported lost communication with the engine controller.",
            "Check battery voltage first, then CAN wiring, grounds, gateway connections and ECM power feeds."),
        DiagnosticIssue("U0101", "Lost communication with TCM", Severity.HIGH, "Vehicle network",
            "Communication with the transmission controller was interrupted.",
            "Inspect TCM power, grounds and CAN network integrity; avoid driving if shifting is abnormal."),
    ).associateBy { it.code }

    fun resolve(codes: List<String>): List<DiagnosticIssue> = codes.map { code ->
        issues[code] ?: DiagnosticIssue(code, "Uncatalogued diagnostic fault", Severity.MEDIUM, "Vehicle",
            "The ECU reported a diagnostic trouble code that is not yet in the local description catalogue.",
            "Consult authorized service information for this vehicle and code before replacing components.")
    }
}
