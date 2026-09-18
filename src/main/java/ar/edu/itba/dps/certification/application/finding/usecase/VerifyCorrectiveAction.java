package ar.edu.itba.dps.certification.application.finding.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionSummary;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.Verification;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionClosed;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;

public final class VerifyCorrectiveAction {

    private final FindingRepository findings;
    private final InspectionQuery inspections;
    private final Clock clock;
    private final DomainEventPublisher events;
    private final AuditRecorder audit;

    public VerifyCorrectiveAction(FindingRepository findings, InspectionQuery inspections,
            Clock clock, DomainEventPublisher events, AuditRecorder audit) {
        this.findings = findings;
        this.inspections = inspections;
        this.clock = clock;
        this.events = events;
        this.audit = audit;
    }

    public Finding verify(FindingId findingId, boolean satisfactory, String reason, PartyId verifiedBy) {
        Finding finding = findings.require(findingId);
        InspectionSummary inspection = inspections.summaryOf(finding.inspectionId());
        Validate.ensure(inspection.inspector().equals(verifiedBy),
                "only the inspector of inspection " + inspection.id()
                        + " may verify the corrective actions of its findings");
        Instant at = clock.now();
        boolean closed = finding.concludeCorrection(
                new Verification(satisfactory, reason, verifiedBy, at), clock.today());
        findings.save(finding);
        audit.record(AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
                AuditAction.CORRECTIVE_ACTION_VERIFIED,
                AuditDetail.decision("verify the reported correction",
                        satisfactory ? "satisfactory" : "not satisfactory"), reason);
        if (closed) {
            audit.record(AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
                    AuditAction.CORRECTIVE_ACTION_CLOSED,
                    AuditDetail.stateChanged("EXECUTION_REPORTED", "CLOSED"));
            events.publish(new CorrectiveActionClosed(finding.inspectionId(), finding.id(),
                    finding.correctiveAction().id(), finding.criterionId(), at));
        }
        return finding;
    }
}
