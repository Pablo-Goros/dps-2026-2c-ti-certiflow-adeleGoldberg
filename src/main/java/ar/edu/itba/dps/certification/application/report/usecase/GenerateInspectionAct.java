package ar.edu.itba.dps.certification.application.report.usecase;

import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.CriterionRecord;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Rectification;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationChange;
import ar.edu.itba.dps.certification.domain.report.InspectionAct;
import ar.edu.itba.dps.certification.domain.report.ReportedValue;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GenerateInspectionAct {

    private static final String NOT_RECORDED = "(not recorded)";

    private final InspectionQuery inspections;
    private final SchemaCatalog schemas;

    public GenerateInspectionAct(InspectionQuery inspections, SchemaCatalog schemas) {
        this.inspections = inspections;
        this.schemas = schemas;
    }

    public InspectionAct generate(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        List<InspectionAct.ActSection> sections = inspection.frozenSchemaVersionId()
                .map(schemas::requireVersion)
                .map(version -> sectionsOf(inspection, version))
                .orElseGet(List::of);
        return new InspectionAct(
                inspection.id(),
                inspection.status(),
                inspection.assetSnapshot(),
                inspection.inspector(),
                inspection.expectedDate(),
                inspection.startedAt(),
                inspection.closedAt(),
                inspection.frozenSchemaVersionId(),
                sections,
                notesOf(inspection),
                rectificationsOf(inspection));
    }

    private List<InspectionAct.ActSection> sectionsOf(Inspection inspection, SchemaVersion version) {
        List<InspectionAct.ActSection> sections = new ArrayList<>();
        for (Section section : version.sections()) {
            List<InspectionAct.ActCriterionLine> lines = new ArrayList<>();
            for (Criterion criterion : section.criteria()) {
                lines.add(lineOf(inspection, criterion));
            }
            sections.add(new InspectionAct.ActSection(section.name(), lines));
        }
        return sections;
    }

    private InspectionAct.ActCriterionLine lineOf(Inspection inspection, Criterion criterion) {
        CriterionRecord record = inspection.requireRecord(criterion.id());
        Optional<CriterionEvaluation> current = record.currentEvaluation();
        return new InspectionAct.ActCriterionLine(
                criterion.id(),
                answerOf(inspection, record),
                record.evidence().stream().map(EvidenceRecord::reference).toList(),
                criterion.shortfalls(record.evidenceCountByRequirement()).stream()
                        .map(shortfall -> shortfall.describe()).toList(),
                resultOf(record),
                current.flatMap(CriterionEvaluation::optionalSeverity),
                current.map(evaluation -> evaluation.reasons().stream()
                        .map(EvaluationReason::describe).toList()).orElseGet(List::of));
    }

    private ReportedValue<String> answerOf(Inspection inspection, CriterionRecord record) {
        String current = record.answer().map(Answer::describe).orElse(NOT_RECORDED);
        String original = null;
        Rectification latest = null;
        for (Rectification rectification : inspection.rectifications()) {
            for (RectificationChange change : rectification.changes()) {
                if (change instanceof RectificationChange.AnswerCorrected corrected
                        && corrected.criterionId().equals(record.criterionId())) {
                    if (latest == null) {
                        original = corrected.previousValue() == null
                                ? NOT_RECORDED : corrected.previousValue();
                    }
                    latest = rectification;
                }
            }
        }
        if (latest == null) {
            return ReportedValue.original(current);
        }
        return ReportedValue.rectified(original, current, latest.id(), latest.reason());
    }

    private Optional<ReportedValue<String>> resultOf(CriterionRecord record) {
        Optional<CriterionEvaluation> original = record.originalEvaluation();
        Optional<CriterionEvaluation> current = record.currentEvaluation();
        if (original.isEmpty() || current.isEmpty()) {
            return Optional.empty();
        }
        if (original.get() == current.get()) {
            return Optional.of(ReportedValue.original(original.get().result().name()));
        }
        return Optional.of(ReportedValue.rectified(
                original.get().result().name(),
                current.get().result().name(),
                current.get().rectificationId().orElseThrow(),
                "re-evaluated after a rectification"));
    }

    private List<InspectionAct.ActNote> notesOf(Inspection inspection) {
        List<InspectionAct.ActNote> notes = new ArrayList<>();
        for (InspectionNote note : inspection.notes()) {
            notes.add(new InspectionAct.ActNote(note.criterionId(),
                    noteTextOf(inspection, note), note.author(), note.recordedAt()));
        }
        return notes;
    }

    private ReportedValue<String> noteTextOf(Inspection inspection, InspectionNote note) {
        String original = null;
        Rectification latest = null;
        for (Rectification rectification : inspection.rectifications()) {
            for (RectificationChange change : rectification.changes()) {
                if (change instanceof RectificationChange.NoteCorrected corrected
                        && corrected.noteId().equals(note.id())) {
                    if (latest == null) {
                        original = corrected.previousValue();
                    }
                    latest = rectification;
                }
            }
        }
        if (latest == null) {
            return ReportedValue.original(note.text());
        }
        return ReportedValue.rectified(original, note.text(), latest.id(), latest.reason());
    }

    private List<InspectionAct.RectificationEntry> rectificationsOf(Inspection inspection) {
        return inspection.rectifications().stream()
                .map(rectification -> new InspectionAct.RectificationEntry(
                        rectification.id(),
                        rectification.author(),
                        rectification.performedAt(),
                        rectification.reason(),
                        rectification.changes().stream()
                                .map(RectificationChange::describe).toList()))
                .toList();
    }
}
