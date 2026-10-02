package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;

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

    public CertificationContext contextFor(Inspection inspection) {
        Validate.required(inspection, "inspection");
        return new CertificationContext(
                inspection,
                findings.findingsOf(inspection.id()),
                clock.today(),
                certificates.findByBackingInspection(inspection.id()).map(Certificate::id),
                liveCertificateOfAsset(inspection));
    }

    private Optional<CertificateId> liveCertificateOfAsset(Inspection inspection) {
        return certificates.findNonExpiredForAsset(inspection.assetId())
                .filter(certificate -> certificate.coversMoment(clock.now()))
                .filter(certificate -> !certificate.backingInspectionId().equals(inspection.id()))
                .map(Certificate::id);
    }
}
