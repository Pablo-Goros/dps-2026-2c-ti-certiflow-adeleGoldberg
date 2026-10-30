package ar.edu.itba.dps.certification.app.web.dto;

import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.CriterionContext;
import ar.edu.itba.dps.certification.domain.assistant.RejectedSuggestion;
import ar.edu.itba.dps.certification.domain.assistant.Suggestion;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionDecision;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.time.Instant;
import java.util.List;

/** Request and response bodies of the inspection assistant. */
public final class AssistanceDtos {

    private AssistanceDtos() {
    }

    public record ConsultationResponse(String id, String inspectionId, String requestedBy, Instant requestedAt,
            AssistantDto assistant, List<SuggestionDto> suggestions, List<RejectedDto> rejected) {

        public static ConsultationResponse of(Consultation consultation) {
            var version = consultation.version();
            return new ConsultationResponse(consultation.id().value(), consultation.inspectionId().value(),
                    consultation.requestedBy().value(), consultation.requestedAt(),
                    new AssistantDto(version.adapter(), version.model(), version.prompt()),
                    consultation.suggestions().stream().map(suggestion -> SuggestionDto.of(consultation, suggestion)).toList(),
                    consultation.rejected().stream().map(RejectedDto::of).toList());
        }
    }

    /** What produced the suggestions: the adapter, the model that answered and the prompt version. */
    public record AssistantDto(String adapter, String model, String prompt) {
    }

    public record SuggestionDto(String id, String criterionId, String section, CriterionResult result,
            Severity severity, String description, String rationale, DecisionDto decision) {

        static SuggestionDto of(Consultation consultation, Suggestion suggestion) {
            String section = consultation.request().criterion(suggestion.criterionId())
                    .map(CriterionContext::section).orElse(null);
            return new SuggestionDto(suggestion.id().value(), suggestion.criterionId().value(), section,
                    suggestion.result(), suggestion.severity(), suggestion.description(), suggestion.rationale(),
                    suggestion.decision().map(DecisionDto::of).orElse(null));
        }
    }

    /**
     * The inspector's decision. {@code observationId} names the observed non-conformity that accepting
     * or editing recorded; the edited values and the note come only with {@code EDITED}, and the
     * reason only with {@code DISCARDED}.
     */
    public record DecisionDto(String verdict, String by, Instant at, String observationId, String criterionId,
            CriterionResult result, Severity severity, String description, String note, String reason) {

        static DecisionDto of(SuggestionDecision decision) {
            return switch (decision) {
                case SuggestionDecision.Accepted accepted -> new DecisionDto(accepted.verdict(), accepted.by().value(),
                        accepted.at(), accepted.observationId(), null, null, null, null, null, null);
                case SuggestionDecision.Edited edited -> new DecisionDto(edited.verdict(), edited.by().value(),
                        edited.at(), edited.observationId(), edited.criterionId().value(), edited.result(),
                        edited.severity(), edited.description(), edited.note(), null);
                case SuggestionDecision.Discarded discarded -> new DecisionDto(discarded.verdict(),
                        discarded.by().value(), discarded.at(), null, null, null, null, null, null,
                        discarded.reason().orElse(null));
            };
        }
    }

    /** Output the domain refused, kept with its reason. */
    public record RejectedDto(String criterionId, CriterionResult result, Severity severity, String description,
            String rationale, String reason) {

        static RejectedDto of(RejectedSuggestion rejected) {
            var output = rejected.output();
            return new RejectedDto(output.criterionId().value(), output.result(), output.severity(),
                    output.description(), output.rationale(), rejected.reason());
        }
    }

    public enum Verdict { ACCEPTED, EDITED, DISCARDED }

    /**
     * {@code ACCEPTED} needs nothing else; {@code EDITED} needs criterionId, result, severity,
     * description and note; {@code DISCARDED} takes an optional reason, and an empty one counts as none.
     */
    public record DecisionRequest(Verdict verdict, String criterionId, CriterionResult result, Severity severity,
            String description, String note, String reason) {
    }
}
