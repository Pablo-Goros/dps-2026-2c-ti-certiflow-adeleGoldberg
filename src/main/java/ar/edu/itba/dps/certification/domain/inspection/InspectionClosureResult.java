package ar.edu.itba.dps.certification.domain.inspection;

import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Map;

public record InspectionClosureResult(
        InspectionId inspectionId,
        Instant closedAt,
        boolean alreadyClosed,
        Map<CriterionId, CriterionEvaluation> evaluations) {

    public InspectionClosureResult {
        Validate.required(inspectionId, "inspection id");
        Validate.required(closedAt, "closure instant");
        Validate.required(evaluations, "evaluations");
        evaluations = Map.copyOf(evaluations);
    }

    public Map<CriterionId, CriterionEvaluation> nonApproved() {
        return evaluations.entrySet().stream()
                .filter(entry -> !entry.getValue().result().approved())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        Map.Entry::getValue));
    }
}
