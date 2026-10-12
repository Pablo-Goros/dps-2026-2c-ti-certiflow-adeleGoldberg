package ar.edu.itba.dps.certification.app.demo;

import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceRequirement;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.schema.rule.EvaluationRule;
import ar.edu.itba.dps.certification.domain.schema.rule.MappedOptionsRule;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericBand;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericRangeRule;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.schema.rule.YesNoRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

final class DemoSchema {

    static final String NAME = "Facility inspection";
    static final String SAFETY_MANUAL = "safety manual";

    static final Subsystem ELECTRICAL = Subsystem.of("electrical installation");
    static final Subsystem PRESSURE = Subsystem.of("pressure system");
    static final Subsystem BUILDING_SAFETY = Subsystem.of("building safety");

    static final CriterionId INSULATION = CriterionId.of("ELEC-INSULATION");
    static final CriterionId GROUNDING = CriterionId.of("ELEC-GROUNDING");
    static final CriterionId RELIEF_VALVE = CriterionId.of("PRES-RELIEF-VALVE");
    static final CriterionId VESSEL_TEST = CriterionId.of("PRES-VESSEL-TEST");
    static final CriterionId EXITS = CriterionId.of("SAFE-EXITS");
    static final CriterionId EXTINGUISHERS = CriterionId.of("SAFE-EXTINGUISHERS");
    static final CriterionId MANUAL = CriterionId.of("DOC-MANUAL");
    static final CriterionId TRAINING = CriterionId.of("DOC-TRAINING");
    static final CriterionId DRILL = CriterionId.of("DOC-EVACUATION-DRILL");

    private DemoSchema() {
    }

    static List<Section> firstVersion() {
        return List.of(
                Section.of("Electrical", 1, insulation(), grounding()),
                Section.of("Pressure", 2, reliefValve(), vesselTest()),
                Section.of("Building safety", 3, exits(), extinguishers()),
                Section.of("Documentation", 4, manual(), training()));
    }

    /**
     * The second version adds one criterion to the transversal section, so an inspection frozen on
     * the first version is visibly evaluating fewer criteria than a later one would.
     */
    static List<Section> secondVersion() {
        return List.of(
                Section.of("Electrical", 1, insulation(), grounding()),
                Section.of("Pressure", 2, reliefValve(), vesselTest()),
                Section.of("Building safety", 3, exits(), extinguishers()),
                Section.of("Documentation", 4, manual(), training(), evacuationDrill()));
    }

    private static Criterion insulation() {
        return Criterion.of(INSULATION.value(), megohms(), ELECTRICAL);
    }

    private static Criterion grounding() {
        return Criterion.of(GROUNDING.value(), new YesNoRule(
                RuleOutcome.approved("GND_OK", "todos los circuitos están puestos a tierra"),
                RuleOutcome.rejected("GND_MISSING", Severity.CRITICAL,
                        "al menos un circuito no está puesto a tierra")), ELECTRICAL);
    }

    private static Criterion reliefValve() {
        return Criterion.of(RELIEF_VALVE.value(), new MappedOptionsRule(Map.of(
                "ok", RuleOutcome.approved("VALVE_OK", "la válvula de alivio abre en su punto de ajuste"),
                "worn", RuleOutcome.observed("VALVE_WORN", Severity.MEDIUM,
                        "la válvula de alivio presenta desgaste y requiere mantenimiento"),
                "faulty", RuleOutcome.rejected("VALVE_FAULTY", Severity.HIGH,
                        "la válvula de alivio no abre en su punto de ajuste"))), PRESSURE);
    }

    private static Criterion vesselTest() {
        return Criterion.of(VESSEL_TEST.value(), bar(), PRESSURE);
    }

    private static Criterion exits() {
        return Criterion.of(EXITS.value(), new MappedOptionsRule(Map.of(
                "clear", RuleOutcome.approved("EXIT_CLEAR", "todas las salidas de emergencia están despejadas"),
                "obstructed", RuleOutcome.observed("EXIT_OBSTRUCTED", Severity.MEDIUM,
                        "una salida de emergencia está parcialmente obstruida"),
                "blocked", RuleOutcome.rejected("EXIT_BLOCKED", Severity.CRITICAL,
                        "una salida de emergencia está bloqueada"))), BUILDING_SAFETY);
    }

    private static Criterion extinguishers() {
        return Criterion.of(EXTINGUISHERS.value(), new YesNoRule(
                RuleOutcome.approved("EXT_OK", "todos los extintores están dentro de su fecha de servicio"),
                RuleOutcome.observed("EXT_OVERDUE", Severity.LOW,
                        "al menos un extintor pasó su fecha de servicio")), BUILDING_SAFETY);
    }

    /** Transversal: it declares no subsystem, so it weighs on every partial certificate. */
    private static Criterion manual() {
        return new Criterion(MANUAL, new YesNoRule(
                RuleOutcome.approved("MANUAL_OK", "el manual de seguridad está vigente"),
                RuleOutcome.observed("MANUAL_OUTDATED", Severity.LOW,
                        "el manual de seguridad está desactualizado")),
                List.of(EvidenceRequirement.mandatory(EvidenceType.DOCUMENT, SAFETY_MANUAL)));
    }

    /** Transversal as well, and numeric, so the two transversal criteria use different rules. */
    private static Criterion training() {
        return Criterion.of(TRAINING.value(), trainingHours());
    }

    private static Criterion evacuationDrill() {
        return Criterion.of(DRILL.value(), new YesNoRule(
                RuleOutcome.approved("DRILL_OK", "se realizó un simulacro de evacuación este año"),
                RuleOutcome.observed("DRILL_MISSING", Severity.MEDIUM,
                        "no se realizó un simulacro de evacuación este año")));
    }

    private static EvaluationRule megohms() {
        return new NumericRangeRule("MOhm", new BigDecimal("0"), new BigDecimal("1000"), List.of(
                band("0", true, "1", false, RuleOutcome.rejected("INS_LOW", Severity.CRITICAL,
                        "la resistencia de aislación es insegura")),
                band("1", true, "5", false, RuleOutcome.observed("INS_WEAK", Severity.HIGH,
                        "la resistencia de aislación está por debajo del valor recomendado")),
                band("5", true, "1000", true, RuleOutcome.approved("INS_OK",
                        "la resistencia de aislación está dentro del rango"))));
    }

    private static EvaluationRule bar() {
        return new NumericRangeRule("bar", new BigDecimal("0"), new BigDecimal("40"), List.of(
                band("0", true, "6", false, RuleOutcome.rejected("PRES_LOW", Severity.HIGH,
                        "el recipiente no sostiene su presión de prueba")),
                band("6", true, "12", true, RuleOutcome.approved("PRES_OK",
                        "el recipiente sostiene su presión de prueba")),
                band("12", false, "40", true, RuleOutcome.rejected("PRES_HIGH", Severity.CRITICAL,
                        "el recipiente supera su presión máxima admisible"))));
    }

    private static EvaluationRule trainingHours() {
        return new NumericRangeRule("h", new BigDecimal("0"), new BigDecimal("200"), List.of(
                band("0", true, "4", false, RuleOutcome.observed("TRAIN_SHORT", Severity.MEDIUM,
                        "la capacitación del personal está por debajo de las horas requeridas")),
                band("4", true, "200", true, RuleOutcome.approved("TRAIN_OK",
                        "la capacitación del personal cumple las horas requeridas"))));
    }

    private static NumericBand band(String lower, boolean lowerInclusive, String upper,
            boolean upperInclusive, RuleOutcome outcome) {
        return new NumericBand(new BigDecimal(lower), lowerInclusive, new BigDecimal(upper),
                upperInclusive, outcome);
    }
}
