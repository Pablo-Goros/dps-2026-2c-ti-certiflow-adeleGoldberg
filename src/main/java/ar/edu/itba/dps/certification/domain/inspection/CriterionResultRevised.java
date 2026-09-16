package ar.edu.itba.dps.certification.domain.inspection;

import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;

public record CriterionResultRevised(
        InspectionId inspectionId,
        CriterionId criterionId,
        CriterionResult previousResult,
        CriterionResult currentResult,
        RectificationId rectificationId,
        String reason,
        Instant occurredAt) implements DomainEvent {

    public CriterionResultRevised {
        Validate.required(inspectionId, "inspection id");
        Validate.required(criterionId, "criterion id");
        Validate.required(previousResult, "previous result");
        Validate.required(currentResult, "current result");
        Validate.required(rectificationId, "rectification id");
        reason = Validate.requiredText(reason, "reason");
        Validate.required(occurredAt, "instant");
    }

    public boolean becameRejected() {
        return currentResult == CriterionResult.REJECTED
                && previousResult != CriterionResult.REJECTED;
    }

    public boolean nonConformityRemoved() {
        return currentResult.approved() && !previousResult.approved();
    }
}
