package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;
import java.util.Optional;

/**
 * What closing the inspection now would conclude about a criterion, and why: the rule's verdict,
 * missing evidence or a non-conformity the inspector already recorded.
 */
public record ProvisionalEvaluation(CriterionResult result, Optional<Severity> severity, List<String> reasons) {

    public ProvisionalEvaluation {
        Validate.required(result, "provisional result");
        Validate.required(severity, "provisional severity");
        reasons = List.copyOf(Validate.required(reasons, "provisional reasons"));
    }

    static ProvisionalEvaluation of(CriterionEvaluation evaluation) {
        return new ProvisionalEvaluation(evaluation.result(), evaluation.optionalSeverity(),
                evaluation.reasons().stream().map(EvaluationReason::describe).toList());
    }
}
