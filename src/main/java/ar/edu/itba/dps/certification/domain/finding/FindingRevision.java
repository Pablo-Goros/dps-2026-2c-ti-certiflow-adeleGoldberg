package ar.edu.itba.dps.certification.domain.finding;

import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.List;

public record FindingRevision(
        CriterionResult result,
        List<EvaluationReason> reasons,
        Severity severity,
        RectificationId rectificationId,
        String reason,
        Instant revisedAt) {

    public FindingRevision {
        Validate.required(result, "result");
        reasons = Validate.requiredNonEmpty(reasons, "reasons");
        Validate.required(severity, "severity");
        Validate.required(rectificationId, "rectification id");
        reason = Validate.requiredText(reason, "revision reason");
        Validate.required(revisedAt, "revision instant");
    }
}
