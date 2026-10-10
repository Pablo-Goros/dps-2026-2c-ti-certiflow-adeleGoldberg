package ar.edu.itba.dps.certification.application.notification;

import ar.edu.itba.dps.certification.application.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.application.notification.port.NotificationSender;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionExpired;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;

import java.util.function.BiConsumer;

/**
 * Tells the finding's responsible that a corrective action ran past its due date.
 *
 * <p>Best effort on purpose: the handlers of an event share one transaction, so a channel that is
 * down must not roll back (or dead-letter) the certificate reactions that travel with the same
 * event. A failed delivery is reported to {@code onFailure} and the event is still considered
 * handled.
 */
public final class NotifyCorrectiveActionExpiry implements DomainEventHandler {

    private final FindingRepository findings;
    private final PartyRepository parties;
    private final NotificationSender sender;
    private final BiConsumer<Notification, RuntimeException> onFailure;

    public NotifyCorrectiveActionExpiry(FindingRepository findings, PartyRepository parties,
            NotificationSender sender, BiConsumer<Notification, RuntimeException> onFailure) {
        this.findings = findings;
        this.parties = parties;
        this.sender = sender;
        this.onFailure = onFailure;
    }

    @Override
    public void handle(DomainEvent event) {
        if (!(event instanceof CorrectiveActionExpired expired)) {
            return;
        }
        var finding = findings.findById(expired.findingId());
        if (finding.isEmpty()) {
            return;
        }
        var recipient = parties.findById(finding.get().responsible());
        if (recipient.isEmpty()) {
            return;
        }
        var party = recipient.get();
        var notification = new Notification(party.id().value(), party.name(),
                "Corrective action overdue",
                "The corrective action for criterion " + expired.criterionId().value() + " of inspection "
                        + expired.inspectionId().value() + " expired without being completed (finding "
                        + expired.findingId().value() + ").",
                expired.occurredAt());
        try {
            sender.send(notification);
        } catch (RuntimeException failure) {
            onFailure.accept(notification, failure);
        }
    }
}
