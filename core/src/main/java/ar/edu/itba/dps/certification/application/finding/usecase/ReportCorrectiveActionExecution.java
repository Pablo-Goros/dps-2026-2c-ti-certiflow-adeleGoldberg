package ar.edu.itba.dps.certification.application.finding.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.finding.action.ExecutionReport;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.time.Instant;
import java.util.List;

public final class ReportCorrectiveActionExecution implements ar.edu.itba.dps.certification.domain.finding.port.inbound.ReportCorrectiveActionExecution {
    private final Transactions transactions;

    private final FindingRepository findings;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public ReportCorrectiveActionExecution(FindingRepository findings, Clock clock,
            AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        this.transactions = transactions;
        this.findings = findings;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    public Finding report(FindingId findingId, String statement, List<String> evidenceReferences) {
        return transactions.execute(() -> {
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
        });
    }
}
