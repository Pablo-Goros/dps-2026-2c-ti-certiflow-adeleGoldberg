package ar.edu.itba.dps.certification.infrastructure.assistant;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.domain.assistant.CriterionContext;
import ar.edu.itba.dps.certification.domain.assistant.Observation;
import ar.edu.itba.dps.certification.domain.assistant.OutcomeOption;
import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The documents of the {@code suggest-findings} prompts, shared by every adapter so all providers
 * see the same input: the request written as JSON, and the answer read back as typed findings.
 *
 * <p>Reading checks only the shape that {@code output.schema.json} describes. Whether a finding makes
 * sense for the inspection (its criterion, an approving result, an empty text) is decided by the
 * domain, which keeps the refused ones with their reason.
 */
public final class SuggestFindingsFormat {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SuggestFindingsFormat() {
    }

    /** The request as the model reads it. The same request is always the same text. */
    public static String input(AssistanceRequest request) {
        ObjectNode root = JSON.createObjectNode();
        root.put("assetType", request.assetType().name());
        ArrayNode subsystems = root.putArray("subsystems");
        request.subsystems().forEach(subsystem -> subsystems.add(subsystem.name()));
        ArrayNode criteria = root.putArray("criteria");
        for (CriterionContext criterion : request.criteria()) {
            ObjectNode line = criteria.addObject();
            line.put("id", criterion.id().value());
            line.put("section", criterion.section());
            putOptional(line, "subsystem", criterion.subsystem().map(Subsystem::name));
            putOptional(line, "answer", criterion.answer());
            criterion.provisional().ifPresentOrElse(provisional -> {
                ObjectNode evaluation = line.putObject("provisionalEvaluation");
                evaluation.put("result", provisional.result().name());
                putOptional(evaluation, "severity", provisional.severity().map(Enum::name));
                ArrayNode reasons = evaluation.putArray("reasons");
                provisional.reasons().forEach(reasons::add);
            }, () -> line.putNull("provisionalEvaluation"));
            ArrayNode recorded = line.putArray("observedNonConformities");
            criterion.observedNonConformities().forEach(recorded::add);
            ArrayNode evidence = line.putArray("evidence");
            criterion.evidence().forEach(evidence::add);
            ArrayNode outcomes = line.putArray("outcomes");
            for (OutcomeOption option : criterion.outcomes()) {
                ObjectNode outcome = outcomes.addObject();
                outcome.put("code", option.code());
                outcome.put("result", option.result().name());
                putOptional(outcome, "severity", option.severity().map(Enum::name));
                outcome.put("description", option.description());
            }
        }
        ArrayNode observations = root.putArray("observations");
        for (Observation observation : request.observations()) {
            ObjectNode note = observations.addObject();
            putOptional(note, "criterionId", observation.criterionId().map(CriterionId::value));
            note.put("text", observation.text());
        }
        return JSON.writeValueAsString(root);
    }

    /**
     * Reads an answer of the model. A Markdown code fence around the JSON is tolerated, and so are
     * letter case in the result and severity and properties the schema does not list.
     *
     * @throws AssistanceUnavailableException {@code MALFORMED} when the text is not an answer of the schema
     */
    public static List<SuggestedFinding> findings(String answer) {
        JsonNode root;
        try {
            root = JSON.readTree(withoutFence(answer == null ? "" : answer));
        } catch (JacksonException e) {
            throw malformed("the answer is not JSON");
        }
        JsonNode findings = root.path("findings");
        if (!findings.isArray()) {
            throw malformed("the answer has no findings list");
        }
        List<SuggestedFinding> read = new ArrayList<>();
        for (JsonNode item : findings) {
            read.add(finding(item, "finding " + (read.size() + 1)));
        }
        return read;
    }

    private static SuggestedFinding finding(JsonNode item, String where) {
        if (!item.isObject()) {
            throw malformed(where + " is not an object");
        }
        String criterionId = text(item, "criterionId", where).strip();
        if (criterionId.isEmpty()) {
            throw malformed(where + " has an empty criterionId");
        }
        return new SuggestedFinding(CriterionId.of(criterionId), constant(CriterionResult.class, item, "result", where),
                constant(Severity.class, item, "severity", where), text(item, "description", where).strip(),
                text(item, "rationale", where).strip());
    }

    private static <E extends Enum<E>> E constant(Class<E> type, JsonNode item, String field, String where) {
        String value = text(item, field, where).strip().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw malformed(where + " has an unknown " + field + " '" + value + "'");
        }
    }

    private static String text(JsonNode item, String field, String where) {
        JsonNode value = item.get(field);
        if (value == null || !value.isString()) {
            throw malformed(where + " has no text " + field);
        }
        return value.stringValue();
    }

    private static String withoutFence(String answer) {
        String text = answer.strip();
        int firstLineEnd = text.indexOf('\n');
        int closing = text.lastIndexOf("```");
        if (text.startsWith("```") && firstLineEnd > 0 && closing > firstLineEnd) {
            return text.substring(firstLineEnd + 1, closing).strip();
        }
        return text;
    }

    private static void putOptional(ObjectNode node, String field, Optional<String> value) {
        value.ifPresentOrElse(text -> node.put(field, text), () -> node.putNull(field));
    }

    private static AssistanceUnavailableException malformed(String problem) {
        return new AssistanceUnavailableException(AssistanceUnavailableException.Reason.MALFORMED,
                "the answer does not follow the schema: " + problem);
    }
}
