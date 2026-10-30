package ar.edu.itba.dps.certification.application.assistant.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.assistant.Suggestion;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionDecision;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionId;
import ar.edu.itba.dps.certification.domain.assistant.port.ConsultationRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.port.inbound.RecordObservedNonConformity;
import ar.edu.itba.dps.certification.domain.inspection.record.ObservedNonConformity;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.Optional;

/**
 * Accepting or editing records the suggestion through {@link RecordObservedNonConformity}, the same
 * operation the inspector has at hand, inside the decision's transaction: the assistant has no path
 * of its own to any consequence.
 */
public final class DecideSuggestion implements ar.edu.itba.dps.certification.domain.assistant.port.inbound.DecideSuggestion {
    private final Transactions transactions;

    private final ConsultationRepository consultations;
    private final InspectionRepository inspections;
    private final RecordObservedNonConformity recordObservation;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public DecideSuggestion(ConsultationRepository consultations, InspectionRepository inspections,
            RecordObservedNonConformity recordObservation, Clock clock, AuditRecorder audit, ActorProvider actors,
            Transactions transactions) {
        this.transactions = transactions;
        this.consultations = consultations;
        this.inspections = inspections;
        this.recordObservation = recordObservation;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    public Consultation accept(InspectionId inspectionId, ConsultationId consultationId, SuggestionId suggestionId) {
        return transactions.execute(() -> {
            var actor = actors.requireUser();
            Consultation consultation = consultations.require(consultationId);
            Suggestion suggestion = consultation.requireDecidable(suggestionId, inspections.require(inspectionId),
                    actor.partyId());
            ObservedNonConformity observation = recordObservation.record(inspectionId, suggestion.criterionId(),
                    suggestion.result(), suggestion.severity(), suggestion.description(), Optional.empty());
            Suggestion decided = consultation.accept(suggestionId, observation.id(), inspections.require(inspectionId),
                    actor.partyId(), clock.now());
            return saved(actor, consultation, decided);
        });
    }

    public Consultation edit(InspectionId inspectionId, ConsultationId consultationId, SuggestionId suggestionId,
            CriterionId criterionId, CriterionResult result, Severity severity, String description, String note) {
        return transactions.execute(() -> {
            var actor = actors.requireUser();
            Consultation consultation = consultations.require(consultationId);
            consultation.requireEditable(suggestionId, result, note, inspections.require(inspectionId), actor.partyId());
            ObservedNonConformity observation = recordObservation.record(inspectionId, criterionId, result, severity,
                    description, Optional.empty());
            Suggestion decided = consultation.edit(suggestionId, observation.id(), criterionId, result, severity,
                    description, note, inspections.require(inspectionId), actor.partyId(), clock.now());
            return saved(actor, consultation, decided);
        });
    }

    public Consultation discard(InspectionId inspectionId, ConsultationId consultationId, SuggestionId suggestionId,
            Optional<String> reason) {
        return transactions.execute(() -> {
            var actor = actors.requireUser();
            Consultation consultation = consultations.require(consultationId);
            Suggestion decided = consultation.discard(suggestionId, reason, inspections.require(inspectionId),
                    actor.partyId(), clock.now());
            return saved(actor, consultation, decided);
        });
    }

    private Consultation saved(Actor actor, Consultation consultation, Suggestion decided) {
        consultations.save(consultation);
        audit.recordAs(actor, AuditedElementRef.inspection(consultation.inspectionId().value()),
                AuditAction.ASSISTANCE_SUGGESTION_DECIDED,
                AuditDetail.decision("suggestion " + decided.id() + " of consultation " + consultation.id() + ": "
                        + decided.criterionId() + " " + decided.result() + " " + decided.severity() + " from "
                        + consultation.version().describe(), outcomeOf(decided.decision().orElseThrow())));
        return consultation;
    }

    private static String outcomeOf(SuggestionDecision decision) {
        return switch (decision) {
            case SuggestionDecision.Accepted accepted ->
                    "accepted as observed non-conformity " + accepted.observationId();
            case SuggestionDecision.Edited edited -> "edited to " + edited.criterionId() + " " + edited.result() + " "
                    + edited.severity() + " as observed non-conformity " + edited.observationId() + ": " + edited.note();
            case SuggestionDecision.Discarded discarded ->
                    "discarded" + discarded.reason().map(reason -> ": " + reason).orElse("");
        };
    }
}
