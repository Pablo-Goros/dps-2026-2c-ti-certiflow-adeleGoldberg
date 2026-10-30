package ar.edu.itba.dps.certification.infrastructure.assistant;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException.Reason;
import ar.edu.itba.dps.certification.domain.assistant.AssistantReply;
import ar.edu.itba.dps.certification.domain.assistant.AssistantVersion;
import ar.edu.itba.dps.certification.domain.assistant.port.InspectionAssistant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The assistant over any service that speaks the OpenAI chat completions protocol: OpenAI, the
 * compatibility endpoint of Gemini, and the servers of open models such as Llama (Ollama, llama.cpp,
 * vLLM and the hosted services built on them). Changing provider is changing the base URL, the
 * model and the key.
 *
 * <p>The prompt's instructions go as the system message and the request as a JSON document. The
 * answer schema is asked for the way the prompt's parameters say: natively ({@code json_schema}),
 * with JSON mode and the schema in the instructions ({@code json_object}), or only in the
 * instructions ({@code instructions}) for servers that support neither.
 *
 * <p>Every failure is an {@link AssistanceUnavailableException} with its category, and its message
 * never quotes what the model wrote, which derives from the inspection's notes. There are no retries:
 * a failed request records nothing, and the inspector can ask again.
 */
public final class OpenAiCompatibleInspectionAssistant implements InspectionAssistant {

    public static final String ADAPTER = "openai-compatible";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> RESERVED = Set.of("model", "messages", "response_format", "stream");
    private static final int EXCERPT = 300;
    /** How providers word an input longer than the model's context. */
    private static final List<String> CONTEXT_LIMIT = List.of("context_length_exceeded", "context length",
            "context window", "context size", "too many tokens", "maximum number of tokens", "prompt is too long");

    private enum StructuredOutput { JSON_SCHEMA, JSON_OBJECT, INSTRUCTIONS }

    private final URI endpoint;
    private final String model;
    private final Optional<String> apiKey;
    private final String promptVersion;
    private final String instructions;
    private final JsonNode schema;
    private final StructuredOutput structuredOutput;
    private final ObjectNode parameters;
    private final Duration timeout;
    private final HttpClient client;

    /**
     * @param baseUrl the address the provider documents for its compatible API, without
     *        {@code /chat/completions}: {@code https://api.openai.com/v1}, {@code http://localhost:11434/v1}...
     * @param apiKey sent as a bearer token; local servers usually need none
     * @throws IllegalArgumentException when an argument or the prompt's parameters are not valid
     */
    public OpenAiCompatibleInspectionAssistant(URI baseUrl, String model, Optional<String> apiKey, Prompt prompt,
            Duration timeout) {
        if (baseUrl == null || !baseUrl.isAbsolute() || !Set.of("http", "https").contains(baseUrl.getScheme())) {
            throw new IllegalArgumentException("the base URL of the assistant must be an http or https address, not " + baseUrl);
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("the model of the assistant is required");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("the timeout of the assistant must be positive");
        }
        String base = baseUrl.toString();
        this.endpoint = URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/chat/completions");
        this.model = model.strip();
        this.apiKey = apiKey.map(String::strip).filter(key -> !key.isEmpty());
        this.promptVersion = prompt.version();
        this.schema = JSON.readTree(prompt.outputSchema());
        JsonNode settings = JSON.readTree(prompt.parameters());
        this.structuredOutput = structuredOutput(settings.path("structuredOutput"), prompt);
        this.parameters = requestParameters(settings.path("request"), prompt);
        this.instructions = structuredOutput == StructuredOutput.JSON_SCHEMA ? prompt.instructions()
                : prompt.instructions().strip() + "\n\nThe answer is a JSON object that follows this JSON schema:\n\n"
                        + prompt.outputSchema().strip() + "\n";
        this.timeout = timeout;
        // HTTP/1.1: some model servers mishandle the cleartext HTTP/2 upgrade that the client tries by default.
        this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build();
    }

    @Override
    public AssistantReply suggest(AssistanceRequest request) {
        HttpResponse<String> response = send(body(request));
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new AssistanceUnavailableException(reasonFor(status, response.body()),
                    "the provider answered HTTP " + status + ": " + excerpt(response.body()));
        }
        return read(response.body());
    }

    String body(AssistanceRequest request) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", instructions);
        messages.addObject().put("role", "user").put("content", SuggestFindingsFormat.input(request));
        body.setAll(parameters.deepCopy());
        switch (structuredOutput) {
            case JSON_SCHEMA -> {
                ObjectNode format = body.putObject("response_format").put("type", "json_schema");
                format.putObject("json_schema").put("name", "suggested_findings").put("strict", true)
                        .set("schema", schema);
            }
            case JSON_OBJECT -> body.putObject("response_format").put("type", "json_object");
            case INSTRUCTIONS -> {
            }
        }
        return JSON.writeValueAsString(body);
    }

    private HttpResponse<String> send(String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        apiKey.ifPresent(key -> request.header("Authorization", "Bearer " + key));
        // The request timeout stops counting when the headers arrive; this deadline also covers the body.
        CompletableFuture<HttpResponse<String>> response =
                client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
        try {
            return response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            response.cancel(true);
            throw new AssistanceUnavailableException(Reason.UNREACHABLE,
                    "the provider did not answer within " + timeout.toSeconds() + " s", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new AssistanceUnavailableException(Reason.UNREACHABLE, cause instanceof HttpTimeoutException
                    ? "the provider did not answer within " + timeout.toSeconds() + " s"
                    : "the provider is unreachable: " + cause, cause);
        } catch (InterruptedException e) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new AssistanceUnavailableException(Reason.UNREACHABLE, "interrupted while waiting for the provider", e);
        }
    }

    private AssistantReply read(String body) {
        JsonNode completion;
        try {
            completion = JSON.readTree(body);
        } catch (JacksonException e) {
            throw new AssistanceUnavailableException(Reason.MALFORMED,
                    "the provider's answer is not JSON (" + body.length() + " characters)", e);
        }
        JsonNode choice = completion.path("choices").path(0);
        JsonNode message = choice.path("message");
        JsonNode refusal = message.path("refusal");
        if (refusal.isString() && !refusal.stringValue().isBlank()) {
            throw new AssistanceUnavailableException(Reason.REFUSED, "the model refused to answer");
        }
        String finish = choice.path("finish_reason").isString() ? choice.path("finish_reason").stringValue() : "";
        if (finish.equals("length")) {
            throw new AssistanceUnavailableException(Reason.INCOMPLETE, "the answer was cut at the token limit");
        }
        if (finish.equals("content_filter")) {
            throw new AssistanceUnavailableException(Reason.REFUSED, "the provider's content filter stopped the answer");
        }
        JsonNode content = message.path("content");
        if (!content.isString() || content.stringValue().isBlank()) {
            throw new AssistanceUnavailableException(Reason.MALFORMED, "the provider's answer has no message content"
                    + (finish.isEmpty() ? "" : " (finish reason '" + finish + "')"));
        }
        JsonNode served = completion.path("model");
        String answeredBy = served.isString() && !served.stringValue().isBlank() ? served.stringValue() : model;
        return new AssistantReply(new AssistantVersion(ADAPTER, answeredBy, promptVersion),
                SuggestFindingsFormat.findings(content.stringValue()));
    }

    /** Transient failures can be asked again; an input longer than the context is a length limit, like a cut answer. */
    private static Reason reasonFor(int status, String body) {
        if (status == 408 || status == 429 || status >= 500) {
            return Reason.UNREACHABLE;
        }
        String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (status == 413 || (status == 400 && CONTEXT_LIMIT.stream().anyMatch(text::contains))) {
            return Reason.INCOMPLETE;
        }
        return Reason.MISCONFIGURED;
    }

    private static StructuredOutput structuredOutput(JsonNode mode, Prompt prompt) {
        String name = mode.isString() ? mode.stringValue() : "";
        return switch (name) {
            case "json_schema" -> StructuredOutput.JSON_SCHEMA;
            case "json_object" -> StructuredOutput.JSON_OBJECT;
            case "instructions" -> StructuredOutput.INSTRUCTIONS;
            default -> throw new IllegalArgumentException("the " + ADAPTER + " parameters of prompt " + prompt.version()
                    + " need structuredOutput json_schema, json_object or instructions, not '" + name + "'");
        };
    }

    private static ObjectNode requestParameters(JsonNode request, Prompt prompt) {
        if (request.isMissingNode()) {
            return JSON.createObjectNode();
        }
        if (!(request instanceof ObjectNode fields)) {
            throw new IllegalArgumentException("the " + ADAPTER + " parameters of prompt " + prompt.version()
                    + " must give the request fields as an object");
        }
        for (String name : fields.propertyNames()) {
            if (RESERVED.contains(name)) {
                throw new IllegalArgumentException("the " + ADAPTER + " parameters of prompt " + prompt.version()
                        + " cannot set '" + name + "', the adapter does");
            }
        }
        return fields;
    }

    private static String excerpt(String text) {
        String flat = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return flat.length() <= EXCERPT ? flat : flat.substring(0, EXCERPT) + "...";
    }
}
