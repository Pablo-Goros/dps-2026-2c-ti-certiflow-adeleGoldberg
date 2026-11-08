package ar.edu.itba.dps.certification.domain.report;

import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.inspection.record.SuggestionOrigin;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record InspectionAct(
        InspectionId inspectionId,
        InspectionStatus status,
        Optional<AssetSnapshot> asset,
        PartyId inspector,
        LocalDate expectedDate,
        Optional<Instant> startedAt,
        Optional<Instant> closedAt,
        Optional<SchemaVersionId> schemaVersionId,
        List<ActSection> sections,
        List<ActNote> notes,
        List<RectificationEntry> rectifications) {

    public InspectionAct {
        Validate.required(inspectionId, "inspection id");
        Validate.required(status, "status");
        Validate.required(asset, "asset snapshot");
        Validate.required(inspector, "inspector");
        Validate.required(expectedDate, "expected date");
        Validate.required(startedAt, "start instant");
        Validate.required(closedAt, "closure instant");
        Validate.required(schemaVersionId, "schema version id");
        sections = List.copyOf(Validate.required(sections, "sections"));
        notes = List.copyOf(Validate.required(notes, "notes"));
        rectifications = List.copyOf(Validate.required(rectifications, "rectifications"));
    }

    public boolean carriesRectifications() {
        return !rectifications.isEmpty();
    }

    public record ActSection(String name, List<ActCriterionLine> lines) {

        public ActSection {
            name = Validate.requiredText(name, "section name");
            lines = List.copyOf(Validate.required(lines, "criterion lines"));
        }
    }

    public record ActCriterionLine(
            CriterionId criterionId,
            boolean applicable,
            ReportedValue<String> answer,
            List<ReportedValue<String>> evidenceReferences,
            List<String> missingEvidence,
            Optional<ReportedValue<String>> result,
            Optional<Severity> severity,
            List<String> reasons,
            List<ActObservation> observations) {

        public ActCriterionLine {
            Validate.required(criterionId, "criterion id");
            Validate.required(answer, "answer");
            evidenceReferences = List.copyOf(Validate.required(evidenceReferences, "evidence"));
            missingEvidence = List.copyOf(Validate.required(missingEvidence, "missing evidence"));
            Validate.required(result, "result");
            Validate.required(severity, "severity");
            reasons = List.copyOf(Validate.required(reasons, "reasons"));
            observations = List.copyOf(Validate.required(observations, "observed non-conformities"));
        }
    }

    /**
     * A non-conformity the inspector recorded on the criterion. {@code origin} tells one the assistant
     * suggested, and whether the inspector accepted it as is or edited it, from one they wrote.
     */
    public record ActObservation(String id, CriterionResult result, Severity severity, String description,
            PartyId recordedBy, Instant recordedAt, Optional<SuggestionOrigin> origin) {

        public ActObservation {
            id = Validate.requiredText(id, "observed non-conformity id");
            Validate.required(result, "observed result");
            Validate.required(severity, "observed severity");
            description = Validate.requiredText(description, "observed non-conformity description");
            Validate.required(recordedBy, "author");
            Validate.required(recordedAt, "instant");
            Validate.required(origin, "origin");
        }
    }

    public record ActNote(Optional<CriterionId> criterionId, ReportedValue<String> text,
            PartyId author, Instant recordedAt) {

        public ActNote {
            Validate.required(criterionId, "criterion id");
            Validate.required(text, "note text");
            Validate.required(author, "author");
            Validate.required(recordedAt, "instant");
        }
    }

    public record RectificationEntry(RectificationId id, PartyId author, Instant performedAt,
            String reason, List<String> changes) {

        public RectificationEntry {
            Validate.required(id, "rectification id");
            Validate.required(author, "author");
            Validate.required(performedAt, "instant");
            reason = Validate.requiredText(reason, "reason");
            changes = List.copyOf(Validate.required(changes, "changes"));
        }
    }
}
