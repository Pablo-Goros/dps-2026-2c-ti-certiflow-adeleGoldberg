package ar.edu.itba.dps.certification.domain.finding.event;

import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;

public record CorrectiveActionPlanned(InspectionId inspectionId, CriterionId criterionId, Instant occurredAt) implements DomainEvent {
    public CorrectiveActionPlanned {
        Validate.required(inspectionId, "inspection id"); Validate.required(criterionId, "criterion id");
        Validate.required(occurredAt, "instant");
    }
}
