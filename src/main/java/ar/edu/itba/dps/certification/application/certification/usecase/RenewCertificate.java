package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionSummary;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;

import java.util.Optional;

public final class RenewCertificate {

    private final InspectionQuery inspections;
    private final CertificateRepository certificates;
    private final IssueCertificate issueCertificate;
    private final Clock clock;
    private final AuditRecorder audit;

    public RenewCertificate(InspectionQuery inspections, CertificateRepository certificates,
            IssueCertificate issueCertificate, Clock clock, AuditRecorder audit) {
        this.inspections = inspections;
        this.certificates = certificates;
        this.issueCertificate = issueCertificate;
        this.clock = clock;
        this.audit = audit;
    }

    public IssuanceDecision renew(InspectionId newInspectionId) {
        InspectionSummary inspection = inspections.summaryOf(newInspectionId);
        Certificate previous = certificates.findLatestForAsset(inspection.assetId())
                .orElseThrow(() -> new DomainException("asset " + inspection.assetId()
                        + " has no certificate to renew"));
        if (!previous.validity().expiredAt(clock.now())) {
            throw new DomainException("certificate " + previous.id()
                    + " has not expired yet, so it cannot be renewed");
        }

        IssuanceDecision decision = issueCertificate.issue(newInspectionId, Optional.of(previous.id()));
        if (decision instanceof IssuanceDecision.Issued issued) {
            audit.record(AuditedElementRef.certificate(previous.id().value()),
                    AuditAction.CERTIFICATE_RENEWED,
                    AuditDetail.decision("renew the certificate",
                            "succeeded by " + issued.certificate().id()));
        }
        return decision;
    }
}
