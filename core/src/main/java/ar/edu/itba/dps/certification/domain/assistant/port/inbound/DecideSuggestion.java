package ar.edu.itba.dps.certification.domain.assistant.port.inbound;

import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.util.Optional;

/** Input contract implemented by the application use case. Who decides comes from the authenticated actor. */
public interface DecideSuggestion {
    Consultation accept(InspectionId inspectionId, ConsultationId consultationId, SuggestionId suggestionId);

    Consultation edit(InspectionId inspectionId, ConsultationId consultationId, SuggestionId suggestionId,
            CriterionId criterionId, CriterionResult result, Severity severity, String description, String note);

    Consultation discard(InspectionId inspectionId, ConsultationId consultationId, SuggestionId suggestionId,
            Optional<String> reason);
}
