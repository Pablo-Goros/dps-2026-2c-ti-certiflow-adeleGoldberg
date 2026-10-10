package ar.edu.itba.dps.certification.app;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Minimal HTTP client for the API tests: real requests against the running server. */
final class Api {

    record Reply(int status, String body, String location) {

        @SuppressWarnings("unchecked")
        Map<String, Object> json() {
            return JSON.readValue(body, Map.class);
        }

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> jsonList() {
            return JSON.readValue(body, java.util.List.class);
        }

        java.util.List<Map<String, Object>> jsonListOrEmpty() {
            return body == null || body.isBlank() ? java.util.List.of() : jsonList();
        }

        String text(String field) {
            Object value = json().get(field);
            return value == null ? null : value.toString();
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newHttpClient();
    private final String base;

    Api(int port) {
        this.base = "http://localhost:" + port;
    }

    Reply get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), null);
    }

    Reply post(String path, Object body, String actor) {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))), actor);
    }

    Reply postRaw(String path, String rawBody) {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(rawBody)), null);
    }

    Reply put(String path, Object body, String actor) {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))), actor);
    }

    Reply delete(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).DELETE(), null);
    }

    Reply getAs(String path, String actor) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), actor);
    }

    private Reply send(HttpRequest.Builder request, String actor) {
        if (actor != null) {
            request.header("X-Actor-Id", actor);
        }
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body(),
                    response.headers().firstValue("Location").orElse(null));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
