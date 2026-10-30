package ar.edu.itba.dps.certification.infrastructure.assistant;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException.Reason;
import ar.edu.itba.dps.certification.domain.assistant.AssistantVersion;
import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** The adapter against a local HTTP server that answers like an OpenAI-compatible provider. */
class OpenAiCompatibleInspectionAssistantIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Prompt PROMPT = PromptLibrary.load("suggest-findings/v1", OpenAiCompatibleInspectionAssistant.ADAPTER);
    private static final String SERVED_MODEL = "llama3.1:8b-instruct-q4_K_M";

    private record Received(String path, String authorization, String body) {
    }

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String answer = completion("{\"findings\": []}", "stop");
    private volatile long delayMillis;
    private volatile long stallMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), body));
            pause(delayMillis);
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            var out = exchange.getResponseBody();
            int half = bytes.length / 2;
            out.write(bytes, 0, half);
            out.flush();
            pause(stallMillis);
            out.write(bytes, half, bytes.length - half);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsTheInstructionsTheRequestAndTheSchemaOfThePrompt() {
        assistant(PROMPT, Optional.of("secret-key")).suggest(CableCase.request());

        Received request = received.getFirst();
        JsonNode body = JSON.readTree(request.body());
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.authorization()).isEqualTo("Bearer secret-key");
        assertThat(body.path("model").stringValue()).isEqualTo("llama3.1:8b");
        assertThat(body.path("messages").path(0).path("role").stringValue()).isEqualTo("system");
        assertThat(body.path("messages").path(0).path("content").stringValue()).isEqualTo(PROMPT.instructions());
        assertThat(body.path("messages").path(1).path("role").stringValue()).isEqualTo("user");
        assertThat(body.path("messages").path(1).path("content").stringValue())
                .isEqualTo(SuggestFindingsFormat.input(CableCase.request()));
        assertThat(body.path("response_format").path("type").stringValue()).isEqualTo("json_schema");
        assertThat(body.path("response_format").path("json_schema").path("schema"))
                .isEqualTo(JSON.readTree(PROMPT.outputSchema()));
        assertThat(body.path("temperature").intValue()).isZero();
        assertThat(body.path("seed").intValue()).isEqualTo(7);
        assertThat(body.has("stream")).isFalse();
    }

    @Test
    void readsTheFindingsAndTheModelThatAnswered() {
        answer = completion("""
                {"findings": [{"criterionId": "ELEC-INSULATION", "result": "REJECTED", "severity": "CRITICAL",
                  "description": "Conductor con la aislación expuesta junto al tablero",
                  "rationale": "La nota general describe un cable pelado; la medición no detecta un daño visible"}]}""",
                "stop");

        var reply = assistant(PROMPT, Optional.empty()).suggest(CableCase.request());

        assertThat(reply.version())
                .isEqualTo(new AssistantVersion("openai-compatible", SERVED_MODEL, "suggest-findings/v1"));
        assertThat(reply.findings()).containsExactly(new SuggestedFinding(CableCase.INSULATION,
                CriterionResult.REJECTED, Severity.CRITICAL, "Conductor con la aislación expuesta junto al tablero",
                "La nota general describe un cable pelado; la medición no detecta un daño visible"));
        assertThat(received.getFirst().authorization()).isNull();
    }

    @Test
    void anEmptyListIsAnAnswerAndABaseUrlWithATrailingSlashWorks() {
        var assistant = new OpenAiCompatibleInspectionAssistant(URI.create(baseUrl() + "/"), "llama3.1:8b",
                Optional.empty(), PROMPT, TIMEOUT);

        assertThat(assistant.suggest(CableCase.request()).findings()).isEmpty();
        assertThat(received.getFirst().path()).isEqualTo("/v1/chat/completions");
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                arguments("cut at the token limit", 200, completion("{\"findings\": [", "length"), Reason.INCOMPLETE),
                arguments("stopped by the content filter", 200, completion("", "content_filter"), Reason.REFUSED),
                arguments("refused by the model", 200, refusal("No puedo ayudar con eso."), Reason.REFUSED),
                arguments("prose instead of JSON", 200, completion("No encontré problemas.", "stop"), Reason.MALFORMED),
                arguments("a tool call instead of a message", 200, completion(null, "tool_calls"), Reason.MALFORMED),
                arguments("a page instead of a completion", 200, "<html>proxy error</html>", Reason.MALFORMED),
                arguments("a wrong key", 401, error("invalid api key"), Reason.MISCONFIGURED),
                arguments("an unknown model", 404, error("model not found"), Reason.MISCONFIGURED),
                arguments("an unsupported parameter", 400, error("response_format is not supported"), Reason.MISCONFIGURED),
                arguments("an inspection longer than the model's context", 400,
                        error("This model's maximum context length is 8192 tokens"), Reason.INCOMPLETE),
                arguments("a request larger than the server takes", 413, error("payload too large"), Reason.INCOMPLETE),
                arguments("a rate limit", 429, error("slow down"), Reason.UNREACHABLE),
                arguments("an overloaded provider", 503, error("overloaded"), Reason.UNREACHABLE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    void eachFailureOfTheProviderHasItsCategory(String situation, int answerStatus, String answerBody, Reason expected) {
        status = answerStatus;
        answer = answerBody;

        assertThatThrownBy(() -> assistant(PROMPT, Optional.empty()).suggest(CableCase.request()))
                .isInstanceOfSatisfying(AssistanceUnavailableException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(expected));
    }

    @Test
    void aSlowProviderIsUnreachableInsteadOfAHang() {
        delayMillis = 1500;

        assertThatThrownBy(() -> new OpenAiCompatibleInspectionAssistant(URI.create(baseUrl()), "llama3.1:8b",
                Optional.empty(), PROMPT, Duration.ofMillis(200)).suggest(CableCase.request()))
                .isInstanceOfSatisfying(AssistanceUnavailableException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(Reason.UNREACHABLE));
    }

    @Test
    void aProviderThatStallsInTheMiddleOfTheAnswerIsUnreachable() {
        stallMillis = 1500;

        assertThatThrownBy(() -> new OpenAiCompatibleInspectionAssistant(URI.create(baseUrl()), "llama3.1:8b",
                Optional.empty(), PROMPT, Duration.ofMillis(300)).suggest(CableCase.request()))
                .isInstanceOfSatisfying(AssistanceUnavailableException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(Reason.UNREACHABLE));
    }

    @Test
    void failureMessagesNeverQuoteWhatTheModelWrote() {
        for (String reply : List.of(completion("La nota de Ana Pérez habla de un cable pelado.", "stop"),
                refusal("No voy a opinar sobre Ana Pérez."), "Ana Pérez revisó el tablero")) {
            answer = reply;

            assertThatThrownBy(() -> assistant(PROMPT, Optional.empty()).suggest(CableCase.request()))
                    .isInstanceOf(AssistanceUnavailableException.class)
                    .hasMessageNotContaining("Pérez");
        }
    }

    @Test
    void anAbsentProviderIsUnreachable() {
        var assistant = assistant(PROMPT, Optional.empty());
        server.stop(0);

        assertThatThrownBy(() -> assistant.suggest(CableCase.request()))
                .isInstanceOfSatisfying(AssistanceUnavailableException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(Reason.UNREACHABLE));
    }

    @Test
    void withoutNativeSchemasTheSchemaTravelsInTheInstructions() {
        assistant(withParameters("{\"structuredOutput\": \"json_object\"}"), Optional.empty()).suggest(CableCase.request());
        assistant(withParameters("{\"structuredOutput\": \"instructions\", \"request\": {\"temperature\": 0.2}}"),
                Optional.empty()).suggest(CableCase.request());

        JsonNode jsonMode = JSON.readTree(received.get(0).body());
        JsonNode instructionsOnly = JSON.readTree(received.get(1).body());
        assertThat(jsonMode.path("response_format").path("type").stringValue()).isEqualTo("json_object");
        assertThat(jsonMode.path("messages").path(0).path("content").stringValue())
                .startsWith(PROMPT.instructions().strip()).contains("\"additionalProperties\"");
        assertThat(instructionsOnly.has("response_format")).isFalse();
        assertThat(instructionsOnly.path("temperature").doubleValue()).isEqualTo(0.2);
        assertThat(instructionsOnly.path("messages").path(0).path("content").stringValue()).contains("\"findings\"");
    }

    @Test
    void invalidSettingsAreRefusedWhenTheAdapterIsBuilt() {
        assertThatThrownBy(() -> assistant(withParameters("{\"structuredOutput\": \"xml\"}"), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("structuredOutput");
        assertThatThrownBy(() -> assistant(withParameters(
                "{\"structuredOutput\": \"json_schema\", \"request\": {\"model\": \"another\"}}"), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot set 'model'");
        assertThatThrownBy(() -> new OpenAiCompatibleInspectionAssistant(URI.create("ftp://models.example"),
                "llama3.1:8b", Optional.empty(), PROMPT, TIMEOUT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpenAiCompatibleInspectionAssistant(URI.create(baseUrl()), " ",
                Optional.empty(), PROMPT, TIMEOUT)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private OpenAiCompatibleInspectionAssistant assistant(Prompt prompt, Optional<String> key) {
        return new OpenAiCompatibleInspectionAssistant(URI.create(baseUrl()), "llama3.1:8b", key, prompt, TIMEOUT);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private static Prompt withParameters(String parameters) {
        return new Prompt(PROMPT.version(), PROMPT.instructions(), PROMPT.outputSchema(), parameters);
    }

    private static String completion(String content, String finishReason) {
        ObjectNode root = JSON.createObjectNode();
        root.put("id", "chatcmpl-1");
        root.put("model", SERVED_MODEL);
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        ObjectNode message = choice.putObject("message").put("role", "assistant");
        if (content == null) {
            message.putNull("content");
        } else {
            message.put("content", content);
        }
        choice.put("finish_reason", finishReason);
        root.putObject("usage").put("prompt_tokens", 812).put("completion_tokens", 64);
        return JSON.writeValueAsString(root);
    }

    private static String refusal(String text) {
        ObjectNode root = (ObjectNode) JSON.readTree(completion(null, "stop"));
        ((ObjectNode) root.path("choices").path(0).path("message")).put("refusal", text);
        return JSON.writeValueAsString(root);
    }

    private static String error(String message) {
        return "{\"error\": {\"message\": \"" + message + "\"}}";
    }
}
