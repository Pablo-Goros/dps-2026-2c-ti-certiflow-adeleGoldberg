package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.port.CertificateRepository;
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

public final class CertificateLifecycle {

    private final CertificateRepository certificates;

    public CertificateLifecycle(CertificateRepository certificates) {
        this.certificates = certificates;
    }

    public record Change(Certificate certificate, CertificateStatus previousStatus,
            boolean suspended, boolean reactivated, String reason) { }

    public Optional<Change> apply(DomainEvent event) {
        return switch (event) {
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
            default -> Optional.empty();
        };
    }

    private Optional<Change> onResultRevised(CriterionResultRevised revised) {
        if (!revised.becameRejected()) {
            return Optional.empty();
        }
        return suspendFor(revised.inspectionId(), new SuspensionCause.RectifiedRejection(
                revised.criterionId(), revised.rectificationId()), revised.occurredAt());
    }

    private Optional<Change> suspendFor(InspectionId inspectionId, SuspensionCause cause, Instant at) {
        Optional<Certificate> backed = certificates.findByBackingInspection(inspectionId);
        if (backed.isEmpty()) {
            return Optional.empty();
        }
        Certificate certificate = backed.get();
        CertificateStatus previousStatus = certificate.status();
        if (!certificate.suspend(cause, at)) {
            return Optional.empty();
        }
        return Optional.of(new Change(certificate, previousStatus, true, false, cause.describe()));
    }

    private Optional<Change> resolveFor(InspectionId inspectionId, Predicate<SuspensionCause> matches,
            String how, Instant at) {
        Optional<Certificate> backed = certificates.findByBackingInspection(inspectionId);
        if (backed.isEmpty()) {
            return Optional.empty();
        }
        Certificate certificate = backed.get();
        CertificateStatus previousStatus = certificate.status();
        boolean reactivated = certificate.resolveCauses(matches, how, at);
        return Optional.of(new Change(certificate, previousStatus, false, reactivated, how));
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
