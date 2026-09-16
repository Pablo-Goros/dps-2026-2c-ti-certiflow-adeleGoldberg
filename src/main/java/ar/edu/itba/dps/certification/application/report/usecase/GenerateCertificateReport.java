package ar.edu.itba.dps.certification.application.report.usecase;

import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.report.CertificateReport;
import ar.edu.itba.dps.certification.domain.report.IssuanceAttemptReport;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.ArrayList;
import java.util.List;

public final class GenerateCertificateReport {

    private final CertificateRepository certificates;
    private final InspectionQuery inspections;
    private final FindingQuery findings;

    public GenerateCertificateReport(CertificateRepository certificates,
            InspectionQuery inspections, FindingQuery findings) {
        this.certificates = certificates;
        this.inspections = inspections;
        this.findings = findings;
    }

    public CertificateReport generate(CertificateId certificateId) {
        Certificate certificate = certificates.require(certificateId);
        return new CertificateReport(
                certificate.id(),
                certificate.assetId(),
                certificate.backingInspectionId(),
                certificate.schemaVersionId(),
                certificate.validity().issuedAt(),
                certificate.validity().expiresAt(),
                certificate.status(),
                certificate.previousCertificateId(),
                inspections.wasRectified(certificate.backingInspectionId()),
                certificate.unresolvedCauses().stream().map(SuspensionCause::describe).toList(),
                pendingCommitmentsOf(certificate.backingInspectionId()));
    }

    public IssuanceAttemptReport reportBlockedAttempt(InspectionId inspectionId,
            List<IssuanceBlocker> blockers) {
        Validate.requiredNonEmpty(blockers, "blockers");
        return IssuanceAttemptReport.blocked(inspectionId,
                blockers.stream().map(IssuanceBlocker::describe).toList());
    }

    private List<CertificateReport.PendingCommitment> pendingCommitmentsOf(InspectionId inspectionId) {
        List<CertificateReport.PendingCommitment> commitments = new ArrayList<>();
        for (Finding finding : findings.findingsOf(inspectionId)) {
            if (finding.obligationVoided() || finding.correctiveAction().status().terminal()) {
                continue;
            }
            finding.correctiveAction().plan().ifPresent(plan ->
                    commitments.add(new CertificateReport.PendingCommitment(
                            finding.criterionId(), plan.work(), plan.dueDate(),
                            finding.correctiveAction().status())));
        }
        return commitments;
    }
}
