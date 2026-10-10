package ar.edu.itba.dps.certification.infrastructure.notification;

import ar.edu.itba.dps.certification.application.notification.Notification;
import ar.edu.itba.dps.certification.application.notification.NotificationFailedException;
import ar.edu.itba.dps.certification.application.notification.port.NotificationSender;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Posts each notification as JSON to an HTTP endpoint (a chat webhook, an e-mail gateway...).
 * A non-2xx answer, a timeout or an unreachable endpoint is a {@link NotificationFailedException}.
 */
public final class WebhookNotificationSender implements NotificationSender {

    private final URI endpoint;
    private final HttpClient client;
    private final Duration timeout;

    public WebhookNotificationSender(URI endpoint, Duration timeout) {
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public void send(Notification notification) {
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json(notification)))
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new NotificationFailedException("webhook answered " + response.statusCode());
            }
        } catch (IOException e) {
            throw new NotificationFailedException("webhook unreachable: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotificationFailedException("interrupted while calling the webhook", e);
        }
    }

    static String json(Notification n) {
        return "{\"recipientId\":" + quote(n.recipientId())
                + ",\"recipientName\":" + quote(n.recipientName())
                + ",\"subject\":" + quote(n.subject())
                + ",\"message\":" + quote(n.message())
                + ",\"occurredAt\":" + quote(n.occurredAt().toString()) + "}";
    }

    private static String quote(String text) {
        var out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
