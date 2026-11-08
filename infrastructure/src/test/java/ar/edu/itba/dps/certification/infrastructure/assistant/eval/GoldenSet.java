package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.CriterionContext;
import ar.edu.itba.dps.certification.domain.assistant.Observation;
import ar.edu.itba.dps.certification.domain.assistant.OutcomeOption;
import ar.edu.itba.dps.certification.domain.assistant.ProvisionalEvaluation;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.infrastructure.assistant.eval.GoldenCase.ExpectedFinding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The golden set: one JSON file per case under {@code assistant/golden/} on the test classpath. The
 * request uses the same fields the model receives (see {@code SuggestFindingsFormat.input}), so a case
 * reads as exactly what the assistant saw. Cases do not depend on a prompt version.
 */
public final class GoldenSet {

    private static final String FOLDER = "assistant/golden";
    private static final ObjectMapper JSON = new ObjectMapper();

    private GoldenSet() {
    }

    /** Every case, in file-name order. */
    public static List<GoldenCase> all() {
        try (Stream<Path> files = Files.list(folder())) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .map(GoldenSet::read)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the golden set", e);
        }
    }

    public static GoldenCase load(String id) {
        return read(folder().resolve(id + ".json"));
    }

    private static Path folder() {
        var url = GoldenSet.class.getClassLoader().getResource(FOLDER);
        if (url == null) {
            throw new IllegalStateException("the golden set is not on the classpath at " + FOLDER);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the golden set", e);
        }
    }

    private static GoldenCase read(Path file) {
        String name = file.getFileName().toString();
        try {
            JsonNode root = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            String id = text(root, "id", name);
            if (!name.equals(id + ".json")) {
                throw new IllegalArgumentException(name + " declares id '" + id + "'");
            }
            return new GoldenCase(id, text(root, "summary", name), request(root.path("request"), name),
                    list(root.path("expected"), item -> new ExpectedFinding(
                            CriterionId.of(text(item, "criterionId", name)),
                            CriterionResult.valueOf(text(item, "result", name)),
                            Severity.valueOf(text(item, "severity", name)))));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read golden case " + name, e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("golden case " + name + " is not valid: " + e.getMessage(), e);
        }
    }

    private static AssistanceRequest request(JsonNode request, String name) {
        Set<Subsystem> subsystems = new LinkedHashSet<>(list(request.path("subsystems"),
                item -> Subsystem.of(item.stringValue())));
        return new AssistanceRequest(AssetType.valueOf(text(request, "assetType", name)), subsystems,
                list(request.path("criteria"), item -> criterion(item, name)),
                list(request.path("observations"), item -> new Observation(
                        optionalText(item, "criterionId").map(CriterionId::of), text(item, "text", name))));
    }

    private static CriterionContext criterion(JsonNode item, String name) {
        JsonNode provisional = item.path("provisionalEvaluation");
        return new CriterionContext(
                CriterionId.of(text(item, "id", name)),
                text(item, "section", name),
                optionalText(item, "subsystem").map(Subsystem::of),
                optionalText(item, "answer"),
                provisional.isObject()
                        ? Optional.of(new ProvisionalEvaluation(
                                CriterionResult.valueOf(text(provisional, "result", name)),
                                optionalText(provisional, "severity").map(Severity::valueOf),
                                list(provisional.path("reasons"), JsonNode::stringValue)))
                        : Optional.empty(),
                list(item.path("observedNonConformities"), JsonNode::stringValue),
                list(item.path("evidence"), JsonNode::stringValue),
                list(item.path("outcomes"), outcome -> new OutcomeOption(
                        text(outcome, "code", name),
                        CriterionResult.valueOf(text(outcome, "result", name)),
                        optionalText(outcome, "severity").map(Severity::valueOf),
                        text(outcome, "description", name))));
    }

    private static <T> List<T> list(JsonNode array, Function<JsonNode, T> read) {
        if (!array.isArray()) {
            throw new IllegalArgumentException("expected a list but found " + array.getNodeType());
        }
        List<T> items = new ArrayList<>();
        for (JsonNode item : array) {
            items.add(read.apply(item));
        }
        return items;
    }

    private static String text(JsonNode node, String field, String name) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new IllegalArgumentException("'" + field + "' is missing in " + name);
        }
        return value.stringValue();
    }

    private static Optional<String> optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? Optional.empty() : Optional.of(value.stringValue());
    }
}
