package ar.edu.itba.dps.certification.application.finding.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectionPlan;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.LocalDate;

public final class PlanCorrectiveAction {

    private final FindingRepository findings;
    private final AuditRecorder audit;

    public PlanCorrectiveAction(FindingRepository findings, AuditRecorder audit) {
        this.findings = findings;
        this.audit = audit;
    }

    public Finding plan(FindingId findingId, String work, PartyId executor, LocalDate dueDate) {
        Finding finding = findings.require(findingId);
        CorrectionPlan plan = new CorrectionPlan(work, executor, dueDate);
        finding.planCorrection(plan);
        findings.save(finding);
        audit.record(AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
                AuditAction.CORRECTIVE_ACTION_PLANNED,
                AuditDetail.decision("confirm the correction plan",
                        work + ", executor " + executor + ", due " + dueDate));
        return finding;
    }
}
