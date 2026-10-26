package ar.edu.itba.dps.certification.domain.notification;

/** The channel could not deliver a notification. */
public class NotificationFailedException extends RuntimeException {

    public NotificationFailedException(String message) {
        super(message);
    }

    public NotificationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
