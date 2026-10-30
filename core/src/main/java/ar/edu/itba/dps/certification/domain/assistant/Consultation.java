package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.inspection.record.ObservedNonConformity;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One request to the assistant: the exact input it saw, the version that answered, what it
 * suggested, what the domain refused and what the inspector decided. It never changes the
 * inspection; accepting a suggestion is recorded there by the inspector's own operation.
 */
public final class Consultation {

    private final ConsultationId id;
    private final InspectionId inspectionId;
    private final PartyId requestedBy;
    private final Instant requestedAt;
    private final AssistanceRequest request;
    private final AssistantVersion version;
    private final List<Suggestion> suggestions = new ArrayList<>();
    private final List<RejectedSuggestion> rejected = new ArrayList<>();

    private Consultation(ConsultationId id, InspectionId inspectionId, PartyId requestedBy, Instant requestedAt,
            AssistanceRequest request, AssistantVersion version) {
        this.id = Validate.required(id, "consultation id");
        this.inspectionId = Validate.required(inspectionId, "inspection id");
        this.requestedBy = Validate.required(requestedBy, "requesting inspector");
        this.requestedAt = Validate.required(requestedAt, "consultation instant");
        this.request = Validate.required(request, "assistance request");
        this.version = Validate.required(version, "assistant version");
    }

    /** Checked before calling the assistant, so a consultation that could not be recorded costs nothing. */
    public static void ensureConsultable(Inspection inspection, PartyId actor) {
        requireOpen(inspection, actor, "consult the assistant about");
    }

    public static Consultation record(ConsultationId id, Inspection inspection, PartyId requestedBy, Instant at,
            AssistanceRequest request, AssistantReply reply) {
        requireOpen(inspection, requestedBy, "consult the assistant about");
        Validate.required(request, "assistance request");
        Validate.required(reply, "assistant reply");
        for (CriterionContext criterion : request.criteria()) {
            Validate.ensure(inspection.requireRecord(criterion.id()).applicable(),
                    "criterion " + criterion.id() + " is not applicable in inspection " + inspection.id());
        }
        Consultation consultation = new Consultation(id, inspection.id(), requestedBy, at, request, reply.version());
        reply.findings().forEach(consultation::classify);
        return consultation;
    }

    private void classify(SuggestedFinding finding) {
        problemWith(finding).ifPresentOrElse(
                problem -> rejected.add(new RejectedSuggestion(finding, problem)),
                () -> suggestions.add(new Suggestion(SuggestionId.of("s" + (suggestions.size() + 1)),
                        finding.criterionId(), finding.result(), finding.severity(), finding.description(),
                        finding.rationale(), Optional.empty())));
    }

    private Optional<String> problemWith(SuggestedFinding finding) {
        if (request.criterion(finding.criterionId()).isEmpty()) {
            return Optional.of("criterion " + finding.criterionId() + " was not part of the consultation");
        }
        if (finding.result().approved()) {
            return Optional.of("an approving result is not a finding");
        }
        if (finding.description().isBlank() || finding.rationale().isBlank()) {
            return Optional.of("a suggestion needs a description and a rationale");
        }
        if (suggestions.stream().anyMatch(existing -> existing.criterionId().equals(finding.criterionId()))) {
            return Optional.of("criterion " + finding.criterionId() + " was already suggested in this consultation");
        }
        return Optional.empty();
    }

    /** Checked before any side effect, so a refused decision records nothing anywhere. */
    public Suggestion requireDecidable(SuggestionId suggestionId, Inspection inspection, PartyId actor) {
        Validate.required(inspection, "inspection");
        Validate.ensure(inspection.id().equals(inspectionId),
                "consultation " + id + " belongs to inspection " + inspectionId + ", not " + inspection.id());
        requireOpen(inspection, actor, "decide a suggestion about");
        Suggestion suggestion = requireSuggestion(suggestionId);
        Validate.ensure(!suggestion.decided(),
                "suggestion " + suggestionId + " of consultation " + id + " is already decided");
        return suggestion;
    }

    /** Like {@link #requireDecidable}, and also checks what only an edit carries, before any side effect. */
    public Suggestion requireEditable(SuggestionId suggestionId, CriterionResult result, String note,
            Inspection inspection, PartyId actor) {
        Suggestion suggestion = requireDecidable(suggestionId, inspection, actor);
        Validate.required(result, "edited result");
        Validate.ensure(!result.approved(), "an edited suggestion is still a non-conformity; discard it instead");
        Validate.requiredText(note, "edit note");
        return suggestion;
    }

    public Suggestion accept(SuggestionId suggestionId, String observationId, Inspection inspection, PartyId actor,
            Instant at) {
        Suggestion suggestion = requireDecidable(suggestionId, inspection, actor);
        var decision = new SuggestionDecision.Accepted(actor, at, observationId);
        requireRecordedAs(inspection, decision.observationId(), suggestion.criterionId(), suggestion.result(),
                suggestion.severity(), suggestion.description());
        return decide(suggestion, decision);
    }

    public Suggestion edit(SuggestionId suggestionId, String observationId, CriterionId criterionId,
            CriterionResult result, Severity severity, String description, String note, Inspection inspection,
            PartyId actor, Instant at) {
        Suggestion suggestion = requireDecidable(suggestionId, inspection, actor);
        var decision = new SuggestionDecision.Edited(actor, at, observationId, criterionId, result, severity,
                description, note);
        requireRecordedAs(inspection, decision.observationId(), decision.criterionId(), decision.result(),
                decision.severity(), decision.description());
        return decide(suggestion, decision);
    }

    public Suggestion discard(SuggestionId suggestionId, Optional<String> reason, Inspection inspection,
            PartyId actor, Instant at) {
        Suggestion suggestion = requireDecidable(suggestionId, inspection, actor);
        return decide(suggestion, new SuggestionDecision.Discarded(actor, at, reason));
    }

    private Suggestion decide(Suggestion suggestion, SuggestionDecision decision) {
        Suggestion decided = suggestion.decidedAs(decision);
        suggestions.set(suggestions.indexOf(suggestion), decided);
        return decided;
    }

    private static void requireRecordedAs(Inspection inspection, String observationId, CriterionId criterionId,
            CriterionResult result, Severity severity, String description) {
        ObservedNonConformity observation = inspection.requireRecord(criterionId).requireObservation(observationId);
        Validate.ensure(observation.result() == result && observation.severity() == severity
                        && observation.description().equals(description),
                "observed non-conformity " + observationId + " does not record what was decided");
    }

    private static void requireOpen(Inspection inspection, PartyId actor, String operation) {
        Validate.required(inspection, "inspection");
        Validate.required(actor, "acting party");
        Validate.ensure(inspection.status() == InspectionStatus.IN_PROGRESS,
                "cannot " + operation + " inspection " + inspection.id() + " while it is " + inspection.status());
        Validate.ensure(actor.equals(inspection.inspector()), "only the assigned inspector " + inspection.inspector()
                + " may " + operation + " inspection " + inspection.id() + ", not " + actor);
    }

    public Suggestion requireSuggestion(SuggestionId suggestionId) {
        return suggestions.stream()
                .filter(suggestion -> suggestion.id().equals(suggestionId))
                .findFirst()
                .orElseThrow(() -> new DomainException("suggestion " + suggestionId + " does not belong to consultation " + id));
    }

    public ConsultationId id() {
        return id;
    }

    public InspectionId inspectionId() {
        return inspectionId;
    }

    public PartyId requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public AssistanceRequest request() {
        return request;
    }

    public AssistantVersion version() {
        return version;
    }

    public List<Suggestion> suggestions() {
        return List.copyOf(suggestions);
    }

    public List<RejectedSuggestion> rejected() {
        return List.copyOf(rejected);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Consultation that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
