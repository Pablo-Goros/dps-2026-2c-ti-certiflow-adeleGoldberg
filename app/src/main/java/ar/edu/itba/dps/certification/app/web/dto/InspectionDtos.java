package ar.edu.itba.dps.certification.app.web.dto;

import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.inspection.CriterionRecord;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** JSON shapes of the inspections and everything recorded on them. */
public final class InspectionDtos {

    private InspectionDtos() {
    }

    /** {@code type}: YES_NO (affirmative), OPTION (option) or MEASUREMENT (value, unit). */
    public record AnswerDto(String type, Boolean affirmative, String option, BigDecimal value, String unit) {

        static AnswerDto of(Answer answer) {
            if (answer instanceof YesNoAnswer yesNo) {
                return new AnswerDto("YES_NO", yesNo.affirmative(), null, null, null);
            }
            if (answer instanceof OptionAnswer option) {
                return new AnswerDto("OPTION", null, option.optionKey(), null, null);
            }
            if (answer instanceof Measurement measurement) {
                return new AnswerDto("MEASUREMENT", null, null, measurement.value(), measurement.unit());
            }
            throw new IllegalStateException("unsupported answer " + answer.getClass().getSimpleName());
        }

        public Answer toDomain() {
            Validate.required(type, "answer type");
            return switch (type) {
                case "YES_NO" -> new YesNoAnswer(Validate.required(affirmative, "affirmative"));
                case "OPTION" -> new OptionAnswer(option);
                case "MEASUREMENT" -> new Measurement(value, unit);
                default -> throw new InvalidArgumentException(
                        "answer type must be YES_NO, OPTION or MEASUREMENT, not " + type);
            };
        }
    }

    public record EvidenceDto(String id, String requirementLabel, EvidenceType type, String reference,
            Instant attachedAt) {

        public static EvidenceDto of(EvidenceRecord evidence) {
            return new EvidenceDto(evidence.id(), evidence.requirementLabel(), evidence.type(),
                    evidence.reference(), evidence.attachedAt());
        }
    }

    public record EvaluationDto(CriterionResult result, Severity severity, List<String> reasons,
            boolean fromRectification) {

        static EvaluationDto of(CriterionEvaluation evaluation) {
            return new EvaluationDto(evaluation.result(), evaluation.severity(),
                    evaluation.reasons().stream().map(EvaluationReason::describe).toList(),
                    evaluation.fromRectification());
        }
    }

    public record CriterionLine(String criterionId, String section, String subsystem, boolean applicable,
            AnswerDto answer, List<EvidenceDto> evidence, EvaluationDto evaluation) {
    }

    public record NoteDto(String id, String criterionId, String text, String authorId, Instant recordedAt) {

        public static NoteDto of(InspectionNote note) {
            return new NoteDto(note.id(), note.criterionId().map(CriterionId::value).orElse(null),
                    note.text(), note.author().value(), note.recordedAt());
        }
    }

    /** Lean row for lists. */
    public record InspectionRow(String id, String assetId, String inspectorId, LocalDate expectedDate,
            InspectionStatus status) {

        public static InspectionRow of(Inspection inspection) {
            return new InspectionRow(inspection.id().value(), inspection.assetId().value(),
                    inspection.inspector().value(), inspection.expectedDate(), inspection.status());
        }
    }

    /**
     * Full view. {@code criteria} is empty until the inspection starts (that is when the schema
     * version is frozen); {@code evaluation} is present once it is closed.
     */
    public record InspectionResponse(String id, String assetId, String inspectorId, LocalDate expectedDate,
            InspectionStatus status, String schemaVersion, Instant startedAt, Instant closedAt,
            List<String> subsystems, List<CriterionLine> criteria, List<NoteDto> notes,
            List<Object> rectifications) {

        public static InspectionResponse of(Inspection inspection, SchemaVersion version,
                java.util.function.Function<Object, Object> plain) {
            List<CriterionLine> lines = new ArrayList<>();
            if (version != null) {
                for (Section section : version.sections()) {
                    for (var criterion : section.criteria()) {
                        CriterionRecord record = inspection.requireRecord(criterion.id());
                        lines.add(new CriterionLine(
                                criterion.id().value(),
                                section.name(),
                                criterion.subsystem().map(Subsystem::name).orElse(null),
                                record.applicable(),
                                record.answer().map(AnswerDto::of).orElse(null),
                                record.evidence().stream().map(EvidenceDto::of).toList(),
                                record.currentEvaluation().map(EvaluationDto::of).orElse(null)));
                    }
                }
            }
            return new InspectionResponse(
                    inspection.id().value(),
                    inspection.assetId().value(),
                    inspection.inspector().value(),
                    inspection.expectedDate(),
                    inspection.status(),
                    inspection.frozenSchemaVersionId().map(Object::toString).orElse(null),
                    inspection.startedAt().orElse(null),
                    inspection.closedAt().orElse(null),
                    inspection.certifiableSubsystems().stream().map(Subsystem::name).toList(),
                    lines,
                    inspection.notes().stream().map(NoteDto::of).toList(),
                    inspection.rectifications().stream().map(plain).toList());
        }
    }

    public record AssignRequest(String assetId, String inspectorId, LocalDate expectedDate) {
    }

    public record ReassignRequest(String inspectorId, LocalDate expectedDate) {
    }

    public record EvidenceRequest(String criterionId, String requirementLabel, String reference) {
    }

    /** {@code criterionId} is optional: a note may refer to the inspection as a whole. */
    public record NoteRequest(String criterionId, String text) {
    }

    public record NoteTextRequest(String text) {
    }

    /** {@code type}: ANSWER (criterionId, answer), EVIDENCE_REFERENCE (criterionId, evidenceId, reference) or NOTE (noteId, text). */
    public record CorrectionDto(String type, String criterionId, AnswerDto answer, String evidenceId,
            String reference, String noteId, String text) {

        Correction toDomain() {
            Validate.required(type, "correction type");
            return switch (type) {
                case "ANSWER" -> new Correction.AnswerCorrection(CriterionId.of(criterionId),
                        Validate.required(answer, "corrected answer").toDomain());
                case "EVIDENCE_REFERENCE" -> new Correction.EvidenceReferenceCorrection(
                        CriterionId.of(criterionId), evidenceId, reference);
                case "NOTE" -> new Correction.NoteCorrection(noteId, text);
                default -> throw new InvalidArgumentException(
                        "correction type must be ANSWER, EVIDENCE_REFERENCE or NOTE, not " + type);
            };
        }
    }

    public record RectifyRequest(String reason, List<CorrectionDto> corrections) {

        public List<Correction> toDomain() {
            Validate.required(corrections, "corrections");
            return corrections.stream().map(CorrectionDto::toDomain).toList();
        }
    }
}
