package ar.edu.itba.dps.certification.application.inspection.port;

import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;

public record NonConformity(
        CriterionId criterionId,
        CriterionEvaluation evaluation,
        List<String> presentedEvidence) {

    public NonConformity {
        Validate.required(criterionId, "criterion id");
        Validate.required(evaluation, "evaluation");
        presentedEvidence = List.copyOf(Validate.required(presentedEvidence, "presented evidence"));
        Validate.ensure(!evaluation.result().approved(),
                "an approved criterion is not a non-conformity");
    }
}
