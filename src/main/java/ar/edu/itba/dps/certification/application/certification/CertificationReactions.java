package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionClosed;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionExpired;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionVoided;
import ar.edu.itba.dps.certification.domain.inspection.CriterionResultRevised;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

public final class CertificationReactions implements DomainEventHandler {

    private final CertificateRepository certificates;
    private final AuditRecorder audit;

    public CertificationReactions(CertificateRepository certificates, AuditRecorder audit) {
        this.certificates = certificates;
        this.audit = audit;
    }

    @Override
    public void handle(DomainEvent event) {
        switch (event) {
            case CorrectiveActionExpired expired -> suspendFor(expired.inspectionId(),
                    new SuspensionCause.OverdueAction(expired.correctiveActionId()),
                    expired.occurredAt());
            case CriterionResultRevised revised -> onResultRevised(revised);
            case CorrectiveActionClosed closed -> resolveFor(closed.inspectionId(),
                    matcher(closed.correctiveActionId(), closed.criterionId()),
                    "the correction was verified and the action closed", closed.occurredAt());
            case CorrectiveActionVoided voided -> resolveFor(voided.inspectionId(),
                    matcher(voided.correctiveActionId(), voided.criterionId()),
                    "the obligation was left without effect by rectification "
                            + voided.rectificationId(), voided.occurredAt());
            default -> {
            }
        }
    }

    private void onResultRevised(CriterionResultRevised revised) {
        if (!revised.becameRejected()) {
            return;
        }
        suspendFor(revised.inspectionId(), new SuspensionCause.RectifiedRejection(
                revised.criterionId(), revised.rectificationId()), revised.occurredAt());
    }

    private void suspendFor(InspectionId inspectionId, SuspensionCause cause, Instant at) {
        Optional<Certificate> backed = certificates.findByBackingInspection(inspectionId);
        if (backed.isEmpty()) {
            return;
        }
        Certificate certificate = backed.get();
        if (!certificate.suspend(cause, at)) {
            return;
        }
        certificates.save(certificate);
        audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                AuditAction.CERTIFICATE_SUSPENDED,
                AuditDetail.stateChanged("VALID", "SUSPENDED"), cause.describe());
    }

    private void resolveFor(InspectionId inspectionId, Predicate<SuspensionCause> matches,
            String how, Instant at) {
        Optional<Certificate> backed = certificates.findByBackingInspection(inspectionId);
        if (backed.isEmpty()) {
            return;
        }
        Certificate certificate = backed.get();
        boolean reactivated = certificate.resolveCauses(matches, how, at);
        certificates.save(certificate);
        if (reactivated) {
            audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                    AuditAction.CERTIFICATE_REACTIVATED,
                    AuditDetail.stateChanged("SUSPENDED", "VALID"));
        }
    }

    private Predicate<SuspensionCause> matcher(CorrectiveActionId actionId, CriterionId criterionId) {
        return cause -> switch (cause) {
            case SuspensionCause.OverdueAction overdue ->
                    overdue.correctiveActionId().equals(actionId);
            case SuspensionCause.RectifiedRejection rejection ->
                    rejection.criterionId().equals(criterionId);
        };
    }
}
