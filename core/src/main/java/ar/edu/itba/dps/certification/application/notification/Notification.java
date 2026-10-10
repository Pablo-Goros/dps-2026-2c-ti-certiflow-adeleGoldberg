package ar.edu.itba.dps.certification.application.notification;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;

/** A message for one party, independent of the channel that delivers it. */
public record Notification(String recipientId, String recipientName, String subject, String message,
        Instant occurredAt) {

    public Notification {
        Validate.requiredText(recipientId, "recipient id");
        Validate.requiredText(recipientName, "recipient name");
        Validate.requiredText(subject, "subject");
        Validate.requiredText(message, "message");
        Validate.required(occurredAt, "instant");
    }
}
