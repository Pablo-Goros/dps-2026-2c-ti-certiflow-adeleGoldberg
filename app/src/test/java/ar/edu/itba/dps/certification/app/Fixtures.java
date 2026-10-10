package ar.edu.itba.dps.certification.app;

import java.util.List;
import java.util.Map;

/** JSON bodies used by several API tests. */
final class Fixtures {

    private Fixtures() {
    }

    static Map<String, Object> outcome(String code, String result, String severity, String description) {
        var outcome = new java.util.HashMap<String, Object>();
        outcome.put("code", code);
        outcome.put("result", result);
        outcome.put("severity", severity);
        outcome.put("description", description);
        return outcome;
    }

    static Map<String, Object> yesNoRule() {
        return Map.of("type", "YES_NO",
                "yes", outcome("OK", "APPROVED", null, "fine"),
                "no", outcome("NOT_OK", "OBSERVED", "LOW", "not fine"));
    }

    static Map<String, Object> temperatureRule() {
        return Map.of("type", "NUMERIC_RANGE", "unit", "c", "minimum", -50, "maximum", 150, "bands", List.of(
                band(-50, true, 2, false, outcome("TEMP_LOW", "REJECTED", "HIGH", "too cold")),
                band(2, true, 8, true, outcome("TEMP_OK", "APPROVED", null, "in range")),
                band(8, false, 150, true, outcome("TEMP_HIGH", "REJECTED", "CRITICAL", "too hot"))));
    }

    private static Map<String, Object> band(int lower, boolean lowerInclusive, int upper, boolean upperInclusive,
            Map<String, Object> outcome) {
        return Map.of("lower", lower, "lowerInclusive", lowerInclusive, "upper", upper,
                "upperInclusive", upperInclusive, "outcome", outcome);
    }

    /** A laboratory section: a temperature measurement and a document-backed yes/no check. */
    static Map<String, Object> laboratorySection() {
        var temperature = new java.util.HashMap<String, Object>();
        temperature.put("id", "TEMP");
        temperature.put("rule", temperatureRule());
        temperature.put("subsystem", null);
        temperature.put("evidence", List.of());
        var documentation = new java.util.HashMap<String, Object>();
        documentation.put("id", "DOC");
        documentation.put("rule", yesNoRule());
        documentation.put("subsystem", null);
        documentation.put("evidence", List.of(Map.of("type", "DOCUMENT", "label", "safety manual",
                "mandatory", true, "minimumCount", 1)));
        return Map.of("name", "Safety", "order", 1, "criteria", List.of(temperature, documentation));
    }

    static Map<String, Object> measurement(String value) {
        return Map.of("type", "MEASUREMENT", "value", new java.math.BigDecimal(value), "unit", "c");
    }

    static Map<String, Object> yes() {
        return Map.of("type", "YES_NO", "affirmative", true);
    }

    /** A facility section: one housekeeping check per subsystem, so partial certificates are possible (F1). */
    static Map<String, Object> facilitySection() {
        return Map.of("name", "Subsystems", "order", 1, "criteria", List.of(
                subsystemCriterion("ELEC", "electrical installation"),
                subsystemCriterion("PRES", "pressure system"),
                subsystemCriterion("SAFE", "building safety")));
    }

    private static Map<String, Object> subsystemCriterion(String id, String subsystem) {
        var options = Map.of(
                "clean", outcome("HK_OK", "APPROVED", null, "area is clean"),
                "untidy", outcome("HK_UNTIDY", "OBSERVED", "MEDIUM", "area is untidy"),
                "hazardous", outcome("HK_HAZARD", "REJECTED", "HIGH", "area is unsafe"));
        return Map.of("id", id, "subsystem", subsystem, "evidence", List.of(),
                "rule", Map.of("type", "OPTIONS", "options", options));
    }

    /** A single check that is not tied to a subsystem, answered with clean / untidy (observed, medium) / hazardous (rejected, high). */
    static Map<String, Object> housekeepingSection() {
        var housekeeping = new java.util.HashMap<String, Object>();
        housekeeping.put("id", "HK");
        housekeeping.put("subsystem", null);
        housekeeping.put("evidence", List.of());
        housekeeping.put("rule", Map.of("type", "OPTIONS", "options", Map.of(
                "clean", outcome("HK_OK", "APPROVED", null, "area is clean"),
                "untidy", outcome("HK_UNTIDY", "OBSERVED", "MEDIUM", "area is untidy"),
                "hazardous", outcome("HK_HAZARD", "REJECTED", "HIGH", "area is unsafe"))));
        return Map.of("name", "Housekeeping", "order", 1, "criteria", List.of(housekeeping));
    }

    static Map<String, Object> option(String key) {
        return Map.of("type", "OPTION", "option", key);
    }
}
