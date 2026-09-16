package ar.edu.itba.dps.certification.domain.audit;

import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Optional;

public record AuditEntry(
        AuditedElementRef element,
        AuditAction action,
        Instant occurredAt,
        Actor actor,
        Optional<String> reason,
        AuditDetail detail) {

    public AuditEntry {
        Validate.required(element, "audited element");
        Validate.required(action, "audit action");
        Validate.required(occurredAt, "audit timestamp");
        Validate.required(actor, "audit actor");
        Validate.required(reason, "audit reason");
        Validate.required(detail, "audit detail");
        Validate.ensure(!action.requiresReason() || reason.isPresent(),
                "action " + action + " requires a reason");
    }
}
