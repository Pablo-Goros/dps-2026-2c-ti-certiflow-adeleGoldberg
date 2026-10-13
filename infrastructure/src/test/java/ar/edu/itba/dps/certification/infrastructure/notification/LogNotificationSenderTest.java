package ar.edu.itba.dps.certification.infrastructure.notification;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.Test;

import ar.edu.itba.dps.certification.application.notification.Notification;

class LogNotificationSenderTest {

    @Test
    void takingANotificationNeverFails() {
        var notification = new Notification("p-1", "Ana", "Subject", "Message", Instant.parse("2030-01-02T03:04:05Z"));

        assertThatCode(() -> new LogNotificationSender().send(notification)).doesNotThrowAnyException();
    }
}
