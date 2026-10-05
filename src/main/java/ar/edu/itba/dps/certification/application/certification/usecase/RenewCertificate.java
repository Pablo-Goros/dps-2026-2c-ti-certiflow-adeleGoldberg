package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.certification.CertificateFactory;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyResolutionException;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.function.Supplier;

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
        return execute(inspectionId, CertificateScope.global(), () -> factory.renew(inspectionId));
    }

    public IssuanceDecision renewPartial(InspectionId inspectionId, Subsystem subsystem) {
        return execute(inspectionId, CertificateScope.of(subsystem), () -> factory.renewPartial(inspectionId, subsystem));
    }

    private IssuanceDecision execute(InspectionId inspectionId,
            CertificateScope scope,
            Supplier<IssuanceDecision> operation) {
        try { return record(inspectionId, operation.get()); }
        catch (PolicyResolutionException error) {
            audit.record(AuditedElementRef.inspection(inspectionId.value()), AuditAction.CERTIFICATE_ISSUANCE_BLOCKED,
                    new AuditDetail.PolicyResolutionFailed("renew", inspectionId, scope, error.getMessage()));
            throw error;
        }
    }

    private IssuanceDecision record(InspectionId inspectionId, IssuanceDecision decision) {
        if (decision instanceof IssuanceDecision.Issued issued) {
            var certificate = issued.certificate();
            certificates.save(certificate);
            audit.record(AuditedElementRef.certificate(certificate.id().value()), AuditAction.CERTIFICATE_ISSUED,
                    AuditDetail.certification("renew", decision));
            audit.record(AuditedElementRef.certificate(certificate.previousCertificateId().orElseThrow().value()),
                    AuditAction.CERTIFICATE_RENEWED,
                    AuditDetail.certification("renew", decision));
        } else if (decision instanceof IssuanceDecision.Blocked blocked) {
            audit.record(AuditedElementRef.inspection(inspectionId.value()), AuditAction.CERTIFICATE_ISSUANCE_BLOCKED,
                    AuditDetail.certification("renew", decision));
        }
        return decision;
    }
}
