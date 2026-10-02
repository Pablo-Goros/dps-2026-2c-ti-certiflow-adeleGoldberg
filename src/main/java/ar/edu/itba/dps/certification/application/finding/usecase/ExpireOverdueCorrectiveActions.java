package ar.edu.itba.dps.certification.application.finding.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ExpireOverdueCorrectiveActions {

    private final FindingRepository findings;
    private final Clock clock;
    private final DomainEventPublisher events;
    private final AuditRecorder audit;

    public ExpireOverdueCorrectiveActions(FindingRepository findings, Clock clock,
            DomainEventPublisher events, AuditRecorder audit) {
        this.findings = findings;
        this.clock = clock;
        this.events = events;
        this.audit = audit;
    }

    public List<Finding> sweep() {
        Instant at = clock.now();
        List<Finding> expired = new ArrayList<>();
        for (Finding finding : findings.findWithOpenActions()) {
            if (!finding.expireCorrectionIfOverdue(clock.today(), at)) {
                continue;
            }
            findings.save(finding);
            expired.add(finding);
            audit.recordAutomatic(
                    AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
                    AuditAction.CORRECTIVE_ACTION_EXPIRED,
                    AuditDetail.decision("deadline sweep", "due "
                            + finding.correctiveAction().breachedDeadline().orElseThrow()
                            + " passed without a verified correction"));
            for (var event : finding.pendingEvents()) {
                events.publish(event);
                finding.acknowledgeEvent(event);
            }
            findings.save(finding);
        }
        return expired;
    }
}
