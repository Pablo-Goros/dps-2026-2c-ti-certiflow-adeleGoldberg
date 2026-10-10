package ar.edu.itba.dps.certification.infrastructure.notification;

import ar.edu.itba.dps.certification.application.notification.Notification;
import ar.edu.itba.dps.certification.application.notification.port.NotificationSender;

/** The default channel: writes the notification to the application log. */
public final class LogNotificationSender implements NotificationSender {

    private static final System.Logger LOG = System.getLogger(LogNotificationSender.class.getName());

    @Override
    public void send(Notification notification) {
        LOG.log(System.Logger.Level.INFO, "notification for {0} ({1}): {2} - {3}",
                notification.recipientName(), notification.recipientId(), notification.subject(),
                notification.message());
    }
}
