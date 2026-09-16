package ar.edu.itba.dps.certification.domain.inspection.record;

import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record CriterionEvaluation(
        CriterionResult result,
        List<EvaluationReason> reasons,
        Severity severity,
        Instant evaluatedAt,
        Optional<RectificationId> rectificationId) {

    public CriterionEvaluation {
        Validate.required(result, "criterion result");
        Validate.required(reasons, "evaluation reasons");
        reasons = List.copyOf(reasons);
        Validate.required(evaluatedAt, "evaluation instant");
        Validate.required(rectificationId, "rectification id");
        Validate.ensure(result.approved() == reasons.isEmpty(),
                "an approved criterion carries no reasons and a non-approved one carries at least one");
        Validate.ensure(result.approved() || severity != null,
                "a non-approved criterion must carry a severity");
    }

    public static CriterionEvaluation approved(Instant evaluatedAt) {
        return new CriterionEvaluation(CriterionResult.APPROVED, List.of(), null, evaluatedAt,
                Optional.empty());
    }

    public static CriterionEvaluation nonApproved(CriterionResult result, List<EvaluationReason> reasons,
            Severity severity, Instant evaluatedAt) {
        return new CriterionEvaluation(result, reasons, severity, evaluatedAt, Optional.empty());
    }

    public CriterionEvaluation asRectificationOf(RectificationId rectification, Instant evaluatedAt) {
        return new CriterionEvaluation(result, reasons, severity, evaluatedAt,
                Optional.of(rectification));
    }

    public Optional<Severity> optionalSeverity() {
        return Optional.ofNullable(severity);
    }

    public boolean fromRectification() {
        return rectificationId.isPresent();
    }
}
