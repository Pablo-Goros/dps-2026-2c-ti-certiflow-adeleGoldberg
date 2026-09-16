package ar.edu.itba.dps.certification.domain.report;

import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
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
            ReportedValue<String> answer,
            List<String> evidenceReferences,
            List<String> missingEvidence,
            Optional<ReportedValue<String>> result,
            Optional<Severity> severity,
            List<String> reasons) {

        public ActCriterionLine {
            Validate.required(criterionId, "criterion id");
            Validate.required(answer, "answer");
            evidenceReferences = List.copyOf(Validate.required(evidenceReferences, "evidence"));
            missingEvidence = List.copyOf(Validate.required(missingEvidence, "missing evidence"));
            Validate.required(result, "result");
            reasons = List.copyOf(Validate.required(reasons, "reasons"));
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
