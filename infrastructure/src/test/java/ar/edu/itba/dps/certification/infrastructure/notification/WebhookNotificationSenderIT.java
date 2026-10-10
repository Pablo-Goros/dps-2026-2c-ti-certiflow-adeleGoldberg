package ar.edu.itba.dps.certification.infrastructure.notification;

import ar.edu.itba.dps.certification.application.notification.Notification;
import ar.edu.itba.dps.certification.application.notification.NotificationFailedException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The webhook channel against a real HTTP server on a local port. */
class WebhookNotificationSenderIT {

    private record Received(String method, String contentType, String body) {
    }

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile int answer = 204;
    private volatile long delayMillis;

    private static Notification notification() {
        return new Notification("p-1", "Ana \"the boss\"", "Corrective action overdue",
                "line one\nline two", Instant.parse("2030-01-02T03:04:05Z"));
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"), body));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(answer, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private WebhookNotificationSender sender(Duration timeout) {
        return new WebhookNotificationSender(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/hook"), timeout);
    }

    @Test
    void postsTheNotificationAsJson() {
        sender(Duration.ofSeconds(2)).send(notification());

        assertThat(received).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.contentType()).isEqualTo("application/json");
            assertThat(request.body()).isEqualTo("{\"recipientId\":\"p-1\","
                    + "\"recipientName\":\"Ana \\\"the boss\\\"\","
                    + "\"subject\":\"Corrective action overdue\","
                    + "\"message\":\"line one\\nline two\","
                    + "\"occurredAt\":\"2030-01-02T03:04:05Z\"}");
        });
    }

    @Test
    void anErrorAnswerIsAFailedNotification() {
        answer = 500;

        assertThatThrownBy(() -> sender(Duration.ofSeconds(2)).send(notification()))
                .isInstanceOf(NotificationFailedException.class)
                .hasMessageContaining("500");
    }

    @Test
    void aSlowEndpointIsAFailedNotificationInsteadOfAHang() {
        delayMillis = 1500;

        assertThatThrownBy(() -> sender(Duration.ofMillis(200)).send(notification()))
                .isInstanceOf(NotificationFailedException.class);
    }

    @Test
    void anUnreachableEndpointIsAFailedNotification() {
        int closedPort = server.getAddress().getPort();
        server.stop(0);

        assertThatThrownBy(() -> new WebhookNotificationSender(
                URI.create("http://127.0.0.1:" + closedPort + "/hook"), Duration.ofSeconds(1)).send(notification()))
                .isInstanceOf(NotificationFailedException.class);
    }

    @Test
    void controlCharactersAreEscaped() {
        var withTab = new Notification("p-1", "Ana", "s", "a\tb\u0001c", Instant.parse("2030-01-02T03:04:05Z"));

        assertThat(WebhookNotificationSender.json(withTab)).contains("a\\tb\\u0001c");
    }
}
