package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.web.dto.AssistanceDtos.ConsultationResponse;
import ar.edu.itba.dps.certification.app.web.dto.AssistanceDtos.DecisionRequest;
import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionId;
import ar.edu.itba.dps.certification.domain.assistant.port.inbound.BrowseConsultations;
import ar.edu.itba.dps.certification.domain.assistant.port.inbound.DecideSuggestion;
import ar.edu.itba.dps.certification.domain.assistant.port.inbound.RequestAssistance;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.inbound.BrowseInspections;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inspection assistant: consultations about an inspection in progress, and the inspector's
 * decision on each suggestion. Only present while {@code certiflow.assistant.enabled=true}, like the
 * use cases it calls.
 */
@RestController
@ConditionalOnProperty(name = "certiflow.assistant.enabled", havingValue = "true")
@RequestMapping("/api/inspections/{id}/consultations")
class AssistanceController {

    private final RequestAssistance requestAssistance;
    private final DecideSuggestion decideSuggestion;
    private final BrowseConsultations consultations;
    private final BrowseInspections inspections;

    AssistanceController(RequestAssistance requestAssistance, DecideSuggestion decideSuggestion,
            BrowseConsultations consultations, BrowseInspections inspections) {
        this.requestAssistance = requestAssistance;
        this.decideSuggestion = decideSuggestion;
        this.consultations = consultations;
        this.inspections = inspections;
    }

    @PostMapping
    ResponseEntity<ConsultationResponse> request(@PathVariable("id") String id) {
        existing(id);
        return ResponseEntity.status(201).body(ConsultationResponse.of(requestAssistance.request(InspectionId.of(id))));
    }

    /** Newest first. */
    @GetMapping
    List<ConsultationResponse> list(@PathVariable("id") String id) {
        existing(id);
        return consultations.of(InspectionId.of(id)).stream().map(ConsultationResponse::of).toList();
    }

    @PostMapping("/{consultationId}/suggestions/{suggestionId}/decision")
    ConsultationResponse decide(@PathVariable("id") String id, @PathVariable("consultationId") String consultationId,
            @PathVariable("suggestionId") String suggestionId, @RequestBody DecisionRequest decision) {
        existing(id);
        var consultation = consultationOf(id, consultationId);
        if (decision.verdict() == null) {
            throw new InvalidArgumentException("verdict is required: ACCEPTED, EDITED or DISCARDED");
        }
        var inspection = InspectionId.of(id);
        var suggestion = SuggestionId.of(suggestionId);
        Consultation decided = switch (decision.verdict()) {
            case ACCEPTED -> decideSuggestion.accept(inspection, consultation, suggestion);
            case EDITED -> decideSuggestion.edit(inspection, consultation, suggestion,
                    CriterionId.of(decision.criterionId()), decision.result(), decision.severity(),
                    decision.description(), decision.note());
            case DISCARDED -> decideSuggestion.discard(inspection, consultation, suggestion,
                    Optional.ofNullable(decision.reason()).filter(reason -> !reason.isBlank()));
        };
        return ConsultationResponse.of(decided);
    }

    private void existing(String id) {
        if (inspections.find(InspectionId.of(id)).isEmpty()) {
            throw new NotFoundException("inspection " + id + " does not exist");
        }
    }

    private ConsultationId consultationOf(String inspectionId, String consultationId) {
        return consultations.find(ConsultationId.of(consultationId))
                .filter(consultation -> consultation.inspectionId().value().equals(inspectionId))
                .map(Consultation::id)
                .orElseThrow(() -> new NotFoundException(
                        "consultation " + consultationId + " does not exist for inspection " + inspectionId));
    }
}
