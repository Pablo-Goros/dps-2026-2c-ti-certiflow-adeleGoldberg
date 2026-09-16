package ar.edu.itba.dps.certification.application.audit;

import ar.edu.itba.dps.certification.application.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.application.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.shared.Actor;

import java.util.Optional;

public final class AuditRecorder {

    private final AuditTrail trail;
    private final ActorProvider actors;
    private final Clock clock;

    public AuditRecorder(AuditTrail trail, ActorProvider actors, Clock clock) {
        this.trail = trail;
        this.actors = actors;
        this.clock = clock;
    }

    public void record(AuditedElementRef element, AuditAction action, AuditDetail detail) {
        append(element, action, actors.current(), Optional.empty(), detail);
    }

    public void record(AuditedElementRef element, AuditAction action, AuditDetail detail,
            String reason) {
        append(element, action, actors.current(), Optional.of(reason), detail);
    }

    public void recordAutomatic(AuditedElementRef element, AuditAction action, AuditDetail detail) {
        append(element, action, Actor.system(), Optional.empty(), detail);
    }

    public void recordAutomatic(AuditedElementRef element, AuditAction action, AuditDetail detail,
            String reason) {
        append(element, action, Actor.system(), Optional.of(reason), detail);
    }

    private void append(AuditedElementRef element, AuditAction action, Actor actor,
            Optional<String> reason, AuditDetail detail) {
        trail.append(new AuditEntry(element, action, clock.now(), actor, reason, detail));
    }
}
