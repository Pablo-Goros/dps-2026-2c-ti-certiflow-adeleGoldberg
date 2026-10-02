package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.*;
import ar.edu.itba.dps.certification.domain.certification.CertificateLifecycle;
import ar.edu.itba.dps.certification.domain.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.domain.shared.port.DomainEventHandler;

/** Persists and audits the consequences decided by the domain. */
public final class CertificationReactions implements DomainEventHandler {
    private final CertificateRepository certificates;
    private final CertificateLifecycle lifecycle;
    private final AuditRecorder audit;
    public CertificationReactions(CertificateRepository certificates, AuditRecorder audit) {
        this.certificates = certificates;
        this.lifecycle = new CertificateLifecycle(certificates);
        this.audit = audit;
    }
    @Override
    public void handle(DomainEvent event) {
        lifecycle.apply(event).ifPresent(change -> {
            var certificate = change.certificate();
            certificates.save(certificate);
            if (change.suspended()) {
                audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                        AuditAction.CERTIFICATE_SUSPENDED,
                        AuditDetail.stateChanged(change.previousStatus(), certificate.status()), change.reason());
            } else if (change.reactivated()) {
                audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                        AuditAction.CERTIFICATE_REACTIVATED,
                        AuditDetail.stateChanged(change.previousStatus(), certificate.status()));
            }
        });
    }
}
