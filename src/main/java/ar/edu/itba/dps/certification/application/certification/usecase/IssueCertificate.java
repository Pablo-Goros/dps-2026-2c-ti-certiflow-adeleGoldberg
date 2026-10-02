package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.*;
import ar.edu.itba.dps.certification.domain.certification.CertificateFactory;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

public final class IssueCertificate {
    private final CertificateFactory factory;
    private final CertificateRepository certificates;
    private final AuditRecorder audit;
    public IssueCertificate(CertificateFactory factory, CertificateRepository certificates, AuditRecorder audit) {
        this.factory = factory;
        this.certificates = certificates;
        this.audit = audit;
    }
    public IssuanceDecision issue(InspectionId inspectionId) {
        IssuanceDecision decision = factory.issue(inspectionId);
        if (decision instanceof IssuanceDecision.Issued issued) {
            var certificate = issued.certificate();
            certificates.save(certificate);
            audit.record(AuditedElementRef.certificate(certificate.id().value()), AuditAction.CERTIFICATE_ISSUED,
                    AuditDetail.created("certificate for asset " + certificate.assetId() + " backed by inspection "
                            + inspectionId + ", valid until " + certificate.validity().expiresAt()));
        } else if (decision instanceof IssuanceDecision.Blocked blocked) {
            audit.record(AuditedElementRef.inspection(inspectionId.value()), AuditAction.CERTIFICATE_ISSUANCE_BLOCKED,
                    AuditDetail.decision("issue a certificate", "blocked: " + blocked.describe()));
        }
        return decision;
    }
}
