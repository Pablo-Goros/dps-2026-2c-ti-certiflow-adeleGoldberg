package ar.edu.itba.dps.certification.domain.finding.event;

import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;

public record CorrectiveActionClosed(
        InspectionId inspectionId,
        FindingId findingId,
        CorrectiveActionId correctiveActionId,
        CriterionId criterionId,
        Instant occurredAt) implements DomainEvent {

    public CorrectiveActionClosed {
        Validate.required(inspectionId, "inspection id");
        Validate.required(findingId, "finding id");
        Validate.required(correctiveActionId, "corrective action id");
        Validate.required(criterionId, "criterion id");
        Validate.required(occurredAt, "instant");
    }
}
