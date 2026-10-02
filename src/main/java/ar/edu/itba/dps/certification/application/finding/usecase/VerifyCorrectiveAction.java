package ar.edu.itba.dps.certification.application.finding.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.Verification;
import ar.edu.itba.dps.certification.domain.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.DomainEventPublisher;

import java.time.Instant;

public final class VerifyCorrectiveAction {

    private final FindingRepository findings;
    private final Clock clock;
    private final DomainEventPublisher events;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public VerifyCorrectiveAction(FindingRepository findings, Clock clock, DomainEventPublisher events, AuditRecorder audit, ActorProvider actors) {
        this.findings = findings;
        this.clock = clock;
        this.events = events;
        this.audit = audit;
        this.actors = actors;
    }

    public Finding verify(FindingId findingId, boolean satisfactory, String reason) {
        var actingUser = actors.requireUser();
        PartyId actor = actingUser.partyId();
        Finding finding = findings.require(findingId);
        Instant at = clock.now();
        boolean closed = finding.concludeCorrection(
                new Verification(satisfactory, reason, actor, at), clock.today());
        findings.save(finding);
        audit.recordAs(actingUser, AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
                AuditAction.CORRECTIVE_ACTION_VERIFIED,
                AuditDetail.decision("verify the reported correction",
                        satisfactory ? "satisfactory" : "not satisfactory"), reason);
        if (closed) {
            audit.recordAs(actingUser, AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
                    AuditAction.CORRECTIVE_ACTION_CLOSED,
                    AuditDetail.stateChanged("EXECUTION_REPORTED", "CLOSED"));
            for (var event : finding.pendingEvents()) {
                events.publish(event);
                finding.acknowledgeEvent(event);
            }
            findings.save(finding);
        }
        return finding;
    }
}
