package ar.edu.itba.dps.certification.application.finding.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.finding.action.ExecutionReport;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.application.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.application.shared.port.Clock;

import java.time.Instant;
import java.util.List;

public final class ReportCorrectiveActionExecution {

    private final FindingRepository findings;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public ReportCorrectiveActionExecution(FindingRepository findings, Clock clock,
            AuditRecorder audit, ActorProvider actors) {
        this.findings = findings;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    public Finding report(FindingId findingId, String statement, List<String> evidenceReferences) {
        var actingUser = actors.requireUser();
        PartyId actor = actingUser.partyId();
        Finding finding = findings.require(findingId);
        Instant at = clock.now();
        CorrectiveActionStatus previousStatus = finding.correctiveAction().status();
        finding.reportCorrectionExecution(
                new ExecutionReport(statement, evidenceReferences, actor, at));
        findings.save(finding);
        audit.recordAs(actingUser, AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
                AuditAction.CORRECTIVE_ACTION_EXECUTION_REPORTED,
                AuditDetail.stateChanged(previousStatus, finding.correctiveAction().status()));
        return finding;
    }
}
