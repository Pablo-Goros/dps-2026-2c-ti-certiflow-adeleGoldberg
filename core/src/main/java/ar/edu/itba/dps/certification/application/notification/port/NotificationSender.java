package ar.edu.itba.dps.certification.application.notification.port;

import ar.edu.itba.dps.certification.application.notification.Notification;
import ar.edu.itba.dps.certification.application.notification.NotificationFailedException;

/** Driven port: delivers a notification through some channel (log, webhook, e-mail...). */
public interface NotificationSender {

    /** @throws NotificationFailedException if the channel could not take the notification */
    void send(Notification notification);
}
