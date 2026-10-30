package ar.edu.itba.dps.certification.infrastructure.assistant;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.CriterionContext;
import ar.edu.itba.dps.certification.domain.assistant.Observation;
import ar.edu.itba.dps.certification.domain.assistant.OutcomeOption;
import ar.edu.itba.dps.certification.domain.assistant.ProvisionalEvaluation;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * The case that motivates the assistant: the insulation measurement is within limits, so the rule
 * approves, but a general note describes a stripped cable next to the panel.
 */
final class CableCase {

    static final CriterionId INSULATION = CriterionId.of("ELEC-INSULATION");
    static final CriterionId VALVES = CriterionId.of("PRES-VALVES");
    static final String NOTE = "cable con la aislación pelada junto al tablero principal";

    private CableCase() {
    }

    static AssistanceRequest request() {
        var electrical = Subsystem.of("electrical installation");
        var pressure = Subsystem.of("pressure system");
        return new AssistanceRequest(AssetType.FACILITY, new LinkedHashSet<>(List.of(electrical, pressure)), List.of(
                new CriterionContext(INSULATION, "Electrical", Optional.of(electrical), Optional.of("12 MOhm"),
                        Optional.of(new ProvisionalEvaluation(CriterionResult.APPROVED, Optional.empty(), List.of())),
                        List.of(), List.of("MEASUREMENT: insulation test report"), List.of(
                                new OutcomeOption("INSULATION_LOW", CriterionResult.REJECTED, Optional.of(Severity.CRITICAL),
                                        "insulation resistance below 1 MOhm: risk of electric shock"),
                                new OutcomeOption("INSULATION_OK", CriterionResult.APPROVED, Optional.empty(),
                                        "insulation resistance of at least 1 MOhm"))),
                new CriterionContext(VALVES, "Pressure", Optional.of(pressure), Optional.empty(), Optional.empty(),
                        List.of(), List.of(), List.of(
                                new OutcomeOption("VALVES_LEAK", CriterionResult.REJECTED, Optional.of(Severity.HIGH),
                                        "a valve leaks"),
                                new OutcomeOption("VALVES_OK", CriterionResult.APPROVED, Optional.empty(),
                                        "valves tight and labelled")))),
                List.of(new Observation(Optional.empty(), NOTE)));
    }
}
