package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionClosed;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionExpired;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionVoided;
import ar.edu.itba.dps.certification.domain.inspection.CriterionResultRevised;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

public final class CertificateLifecycle {

    public record Change(Certificate certificate, CertificateStatus previousStatus,
            boolean suspended, boolean reactivated, String reason) { }

    public Optional<Change> apply(Certificate certificate, DomainEvent event) {
        return switch (event) {
            case CorrectiveActionExpired expired -> suspendFor(certificate,
                    new SuspensionCause.OverdueAction(expired.correctiveActionId()),
                    expired.occurredAt());
            case CriterionResultRevised revised -> onResultRevised(certificate, revised);
            case CorrectiveActionClosed closed -> resolveFor(certificate,
                    matcher(closed.correctiveActionId(), closed.criterionId()),
                    "the correction was verified and the action closed", closed.occurredAt());
            case CorrectiveActionVoided voided -> resolveFor(certificate,
                    matcher(voided.correctiveActionId(), voided.criterionId()),
                    "the obligation was left without effect by rectification "
                            + voided.rectificationId(), voided.occurredAt());
            default -> Optional.empty();
        };
    }

    private Optional<Change> onResultRevised(Certificate certificate, CriterionResultRevised revised) {
        if (!revised.resultsInRejection()) {
            return Optional.empty();
        }
        return suspendFor(certificate, new SuspensionCause.RectifiedRejection(
                revised.criterionId(), revised.rectificationId()), revised.occurredAt());
    }

    private Optional<Change> suspendFor(Certificate certificate, SuspensionCause cause, Instant at) {
        CertificateStatus previousStatus = certificate.status();
        if (!certificate.suspend(cause, at)) {
            return Optional.empty();
        }
        return Optional.of(new Change(certificate, previousStatus, true, false, cause.describe()));
    }

    private Optional<Change> resolveFor(Certificate certificate, Predicate<SuspensionCause> matches,
            String how, Instant at) {
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
