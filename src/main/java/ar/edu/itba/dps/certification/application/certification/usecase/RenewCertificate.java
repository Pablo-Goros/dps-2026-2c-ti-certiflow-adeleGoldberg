package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.*;
import ar.edu.itba.dps.certification.domain.certification.CertificateFactory;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

public final class RenewCertificate {
    private final CertificateFactory factory;
    private final CertificateRepository certificates;
    private final AuditRecorder audit;
    public RenewCertificate(CertificateFactory factory, CertificateRepository certificates, AuditRecorder audit) {
        this.factory = factory;
        this.certificates = certificates;
        this.audit = audit;
    }
    public IssuanceDecision renew(InspectionId inspectionId) {
        IssuanceDecision decision = factory.renew(inspectionId);
        if (decision instanceof IssuanceDecision.Issued issued) {
            var certificate = issued.certificate();
            certificates.save(certificate);
            audit.record(AuditedElementRef.certificate(certificate.id().value()), AuditAction.CERTIFICATE_ISSUED,
                    AuditDetail.created("renewed certificate backed by inspection " + inspectionId));
            audit.record(AuditedElementRef.certificate(certificate.previousCertificateId().orElseThrow().value()),
                    AuditAction.CERTIFICATE_RENEWED,
                    AuditDetail.decision("renew the certificate", "succeeded by " + certificate.id()));
        } else if (decision instanceof IssuanceDecision.Blocked blocked) {
            audit.record(AuditedElementRef.inspection(inspectionId.value()), AuditAction.CERTIFICATE_ISSUANCE_BLOCKED,
                    AuditDetail.decision("renew a certificate", "blocked: " + blocked.describe()));
        }
        return decision;
    }
}
