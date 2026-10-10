package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.app.ops.OutboxOperations;
import ar.edu.itba.dps.certification.application.notification.port.NotificationSender;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.infrastructure.events.OutboxDispatcher;
import ar.edu.itba.dps.certification.infrastructure.events.OutboxEventPublisher;
import ar.edu.itba.dps.certification.infrastructure.notification.LogNotificationSender;
import ar.edu.itba.dps.certification.infrastructure.notification.WebhookNotificationSender;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcEventOutbox;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcTransactions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Technical ports of the core: time, identifiers, current actor and event delivery. */
@Configuration(proxyBeanMethods = false)
class SupportConfig {

    @Bean
    Clock clock(@Value("${certiflow.zone:America/Argentina/Buenos_Aires}") String zone) {
        return new SystemClock(ZoneId.of(zone));
    }

    @Bean
    IdGenerator ids() {
        return () -> UUID.randomUUID().toString();
    }

    @Bean
    RequestActor actors() {
        return new RequestActor();
    }

    /** Delivers outbox events to every {@link DomainEventHandler} bean, each event in its own transaction. */
    @Bean
    OutboxDispatcher outboxDispatcher(JdbcTransactions transactions, JdbcEventOutbox outbox,
            List<DomainEventHandler> handlers, @Value("${certiflow.outbox.max-attempts:10}") int maxAttempts) {
        return new OutboxDispatcher(transactions, outbox, handlers, maxAttempts);
    }

    /** Monitoring and manual control of the outbox, for the admin endpoints and the scheduled jobs. */
    @Bean
    OutboxOperations outboxOperations(JdbcTransactions transactions, JdbcEventOutbox outbox, OutboxDispatcher dispatcher) {
        return new JdbcOutboxOperations(transactions, outbox, dispatcher);
    }

    /** What the use cases publish to: the event is stored with the change and delivered after commit. */
    @Bean
    OutboxEventPublisher events(JdbcTransactions transactions, JdbcEventOutbox outbox, OutboxDispatcher dispatcher) {
        return new OutboxEventPublisher(transactions, outbox, dispatcher);
    }

    /**
     * The notification channel: a webhook when {@code certiflow.notifications.webhook-url} is set,
     * the application log otherwise.
     */
    @Bean
    NotificationSender notificationSender(
            @Value("${certiflow.notifications.webhook-url:}") String webhookUrl,
            @Value("${certiflow.notifications.timeout-seconds:3}") int timeoutSeconds) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return new LogNotificationSender();
        }
        return new WebhookNotificationSender(URI.create(webhookUrl.trim()), Duration.ofSeconds(timeoutSeconds));
    }
}
