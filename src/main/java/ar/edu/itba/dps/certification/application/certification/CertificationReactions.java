package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.certification.CertificateLifecycle;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionClosed;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionExpired;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionVoided;
import ar.edu.itba.dps.certification.domain.inspection.CriterionResultRevised;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;

import java.util.Optional;

/** Persists and audits the consequences decided by the domain. */
public final class CertificationReactions implements DomainEventHandler {

    private final CertificateRepository certificates;
    private final CertificateLifecycle lifecycle;
    private final AuditRecorder audit;

    public CertificationReactions(CertificateRepository certificates, CertificateLifecycle lifecycle,
            AuditRecorder audit) {
        this.certificates = certificates;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    @Override
    public void handle(DomainEvent event) {
        backedInspectionId(event)
                .flatMap(certificates::findByBackingInspection)
                .flatMap(certificate -> lifecycle.apply(certificate, event))
                .ifPresent(change -> {
                    var certificate = change.certificate();
                    certificates.save(certificate);
                    if (change.suspended()) {
                        audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                                AuditAction.CERTIFICATE_SUSPENDED,
                                AuditDetail.stateChanged(change.previousStatus(), certificate.status()),
                                change.reason());
                    } else if (change.reactivated()) {
                        audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                                AuditAction.CERTIFICATE_REACTIVATED,
                                AuditDetail.stateChanged(change.previousStatus(), certificate.status()));
                    }
                });
    }

    private Optional<InspectionId> backedInspectionId(DomainEvent event) {
        return switch (event) {
            case CorrectiveActionExpired expired -> Optional.of(expired.inspectionId());
            case CriterionResultRevised revised -> Optional.of(revised.inspectionId());
            case CorrectiveActionClosed closed -> Optional.of(closed.inspectionId());
            case CorrectiveActionVoided voided -> Optional.of(voided.inspectionId());
            default -> Optional.empty();
        };
    }
}
