package ar.edu.itba.dps.certification.application.report.usecase;

import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectionPlan;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveAction;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.report.FindingsSummary;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceShortfall;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;
import java.util.Optional;

public final class GenerateFindingsSummary {

    private final FindingQuery findings;

    public GenerateFindingsSummary(FindingQuery findings) {
        this.findings = findings;
    }

    public FindingsSummary generate(InspectionId inspectionId) {
        Validate.required(inspectionId, "inspection id");
        return new FindingsSummary(inspectionId, findings.findingsOf(inspectionId).stream()
                .map(this::lineOf)
                .toList());
    }

    private FindingsSummary.FindingLine lineOf(Finding finding) {
        return new FindingsSummary.FindingLine(
                finding.id(),
                finding.criterionId(),
                finding.result(),
                finding.severity(),
                finding.reasons().stream().map(EvaluationReason::describe).toList(),
                finding.responsible(),
                finding.presentedEvidence(),
                finding.shortfalls().stream().map(EvidenceShortfall::describe).toList(),
                finding.obligationVoided(),
                actionLineOf(finding.correctiveAction()));
    }

    private FindingsSummary.ActionLine actionLineOf(CorrectiveAction action) {
        Optional<CorrectionPlan> plan = action.plan();
        return new FindingsSummary.ActionLine(
                action.id(),
                action.status(),
                plan.map(CorrectionPlan::work),
                plan.map(CorrectionPlan::executor),
                plan.map(CorrectionPlan::dueDate),
                action.deadlineBreached(),
                verificationsOf(action));
    }

    private List<FindingsSummary.VerificationLine> verificationsOf(CorrectiveAction action) {
        return action.verifications().stream()
                .map(verification -> new FindingsSummary.VerificationLine(
                        verification.satisfactory(), verification.reason(), verification.verifiedAt()))
                .toList();
    }
}
