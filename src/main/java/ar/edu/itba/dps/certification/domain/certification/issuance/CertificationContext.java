package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Facts the issuance policy decides on. The business meaning of each fact (what counts as an
 * unverified rejection, an overdue or unplanned action) is computed here, in the domain, from
 * the findings themselves, never precomputed by an adapter.
 */
public record CertificationContext(
        Inspection inspection, List<Finding> findings, LocalDate today,
        Optional<InspectionId> laterClosedInspection,
        Optional<CertificateId> nonExpiredCertificateOfAsset) {

    public CertificationContext {
        Validate.required(inspection, "inspection");
        findings = List.copyOf(Validate.required(findings, "findings"));
        Validate.required(today, "today");
        Validate.required(laterClosedInspection, "later closed inspection");
        Validate.required(nonExpiredCertificateOfAsset, "non expired certificate of asset");
        Validate.ensure(findings.stream().allMatch(f -> f.inspectionId().equals(inspection.id())),
                "all findings must belong to the backing inspection");
    }

    public InspectionId inspectionId() {
        return inspection.id();
    }

    public boolean inspectionClosed() {
        return inspection.status().closed();
    }

    public int unverifiedRejections() {
        return (int) inspection.currentEvaluations().entrySet().stream()
                .filter(e -> e.getValue().result() == CriterionResult.REJECTED)
                .filter(e -> findings.stream().noneMatch(f -> f.criterionId().equals(e.getKey()) && !f.blocksCertification()))
                .count();
    }

    public int overdueOpenActions() {
        return (int) findings.stream().filter(f -> !f.obligationVoided())
                .filter(f -> f.correctiveAction().overdueAndOpen(today)).count();
    }

    public int unplannedActions() {
        int pending = (int) findings.stream().filter(f -> !f.obligationVoided())
                .filter(f -> f.correctiveAction().awaitingPlan()).count();
        int missing = (int) inspection.currentEvaluations().entrySet().stream()
                .filter(e -> !e.getValue().result().approved())
                .filter(e -> findings.stream().noneMatch(f -> f.criterionId().equals(e.getKey()))).count();
        return pending + missing;
    }
}
