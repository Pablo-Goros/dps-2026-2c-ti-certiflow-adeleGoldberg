package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionSummary;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Optional;

public final class CertificationContextAssembler {

    private final FindingQuery findings;
    private final CertificateRepository certificates;
    private final Clock clock;

    public CertificationContextAssembler(FindingQuery findings, CertificateRepository certificates,
            Clock clock) {
        this.findings = findings;
        this.certificates = certificates;
        this.clock = clock;
    }

    public CertificationContext contextFor(InspectionSummary inspection) {
        Validate.required(inspection, "inspection");
        return new CertificationContext(
                inspection.id(),
                inspection.closed(),
                findings.unverifiedRejectionsOf(inspection.id()).size(),
                findings.overdueOpenActionsOf(inspection.id(), clock.today()).size(),
                findings.unplannedActionsOf(inspection.id()).size(),
                certificates.findByBackingInspection(inspection.id()).map(Certificate::id),
                liveCertificateOfAsset(inspection));
    }

    private Optional<CertificateId> liveCertificateOfAsset(InspectionSummary inspection) {
        return certificates.findNonExpiredForAsset(inspection.assetId())
                .filter(certificate -> certificate.coversMoment(clock.now()))
                .filter(certificate -> !certificate.backingInspectionId().equals(inspection.id()))
                .map(Certificate::id);
    }
}
