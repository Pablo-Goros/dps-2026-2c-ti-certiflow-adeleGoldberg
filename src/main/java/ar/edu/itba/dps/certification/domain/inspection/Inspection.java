package ar.edu.itba.dps.certification.domain.inspection;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Rectification;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationChange;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Inspection {

    private final InspectionId id;
    private final AssetId assetId;
    private final Map<CriterionId, CriterionRecord> records = new LinkedHashMap<>();
    private final List<InspectionNote> notes = new ArrayList<>();
    private final List<Rectification> rectifications = new ArrayList<>();
    private PartyId inspector;
    private LocalDate expectedDate;
    private InspectionStatus status;
    private SchemaVersionId frozenSchemaVersionId;
    private AssetSnapshot assetSnapshot;
    private Instant startedAt;
    private Instant closedAt;

    public Inspection(InspectionId id, AssetId assetId, PartyId inspector, LocalDate expectedDate) {
        this.id = Validate.required(id, "inspection id");
        this.assetId = Validate.required(assetId, "asset id");
        this.inspector = Validate.required(inspector, "inspector");
        this.expectedDate = Validate.required(expectedDate, "expected date");
        this.status = InspectionStatus.ASSIGNED;
    }

    public InspectionId id() {
        return id;
    }

    public AssetId assetId() {
        return assetId;
    }

    public PartyId inspector() {
        return inspector;
    }

    public LocalDate expectedDate() {
        return expectedDate;
    }

    public InspectionStatus status() {
        return status;
    }

    public Optional<SchemaVersionId> frozenSchemaVersionId() {
        return Optional.ofNullable(frozenSchemaVersionId);
    }

    public SchemaVersionId requireFrozenSchemaVersionId() {
        if (frozenSchemaVersionId == null) {
            throw new DomainException("inspection " + id + " has not been started");
        }
        return frozenSchemaVersionId;
    }

    public Optional<AssetSnapshot> assetSnapshot() {
        return Optional.ofNullable(assetSnapshot);
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public void reassign(PartyId newInspector, LocalDate newExpectedDate) {
        requireStatus(InspectionStatus.ASSIGNED, "reassign");
        this.inspector = Validate.required(newInspector, "inspector");
        this.expectedDate = Validate.required(newExpectedDate, "expected date");
    }

    public void start(SchemaVersion version, AssetSnapshot snapshot, Instant at) {
        requireStatus(InspectionStatus.ASSIGNED, "start");
        Validate.required(version, "schema version");
        Validate.required(snapshot, "asset snapshot");
        Validate.required(at, "start instant");
        Validate.ensure(snapshot.assetId().equals(assetId),
                "the snapshot belongs to a different asset");
        this.frozenSchemaVersionId = version.id();
        this.assetSnapshot = snapshot;
        this.startedAt = at;
        this.status = InspectionStatus.IN_PROGRESS;
        for (Criterion criterion : version.criteria()) {
            records.put(criterion.id(), new CriterionRecord(criterion.id()));
        }
    }

    public void recordAnswer(CriterionId criterionId, Answer answer) {
        requireInProgress("record an answer");
        requireRecord(criterionId).recordAnswer(answer);
    }

    public void removeAnswer(CriterionId criterionId) {
        requireInProgress("remove an answer");
        requireRecord(criterionId).clearAnswer();
    }

    public void attachEvidence(CriterionId criterionId, EvidenceRecord evidence) {
        requireInProgress("attach evidence");
        requireRecord(criterionId).attach(evidence);
    }

    public EvidenceRecord removeEvidence(CriterionId criterionId, String evidenceId) {
        requireInProgress("remove evidence");
        return requireRecord(criterionId).detach(evidenceId);
    }

    public void recordNote(InspectionNote note) {
        requireInProgress("record a note");
        Validate.required(note, "note");
        note.criterionId().ifPresent(this::requireRecord);
        Validate.ensure(notes.stream().noneMatch(existing -> existing.id().equals(note.id())),
                "note " + note.id() + " already exists");
        notes.add(note);
    }

    public InspectionNote correctNote(String noteId, String text) {
        requireInProgress("correct a note");
        InspectionNote previous = requireNote(noteId);
        notes.set(notes.indexOf(previous), previous.withText(text));
        return previous;
    }

    public InspectionNote removeNote(String noteId) {
        requireInProgress("remove a note");
        InspectionNote removed = requireNote(noteId);
        notes.remove(removed);
        return removed;
    }

    public InspectionClosureResult close(Instant at, Map<CriterionId, CriterionEvaluation> evaluations) {
        Validate.required(at, "closure instant");
        Validate.required(evaluations, "evaluations");
        if (status.closed()) {
            return new InspectionClosureResult(id, closedAt, true, currentEvaluations());
        }
        requireInProgress("close");
        Validate.ensure(evaluations.keySet().equals(records.keySet()),
                "every criterion of the frozen version must be evaluated exactly once at close");
        evaluations.forEach((criterionId, evaluation) -> requireRecord(criterionId).recordClosureEvaluation(evaluation));
        this.status = InspectionStatus.CLOSED;
        this.closedAt = at;
        return new InspectionClosureResult(id, at, false, evaluations);
    }

    public Rectification rectify(SchemaVersion version, RectificationId rectificationId,
            PartyId author, Instant at, String reason, List<Correction> corrections) {
        Validate.ensure(status.closed(), "only a closed inspection can be rectified");
        Validate.required(author, "rectification author");
        Validate.ensure(author.equals(inspector),
                "only the assigned inspector may rectify inspection " + id);
        Validate.required(rectificationId, "rectification id");
        Validate.required(at, "rectification instant");
        Validate.requiredText(reason, "rectification reason");
        Validate.requiredNonEmpty(corrections, "corrections");
        Validate.required(version, "schema version");
        Validate.ensure(version.id().equals(frozenSchemaVersionId),
                "a rectification must be checked against the version frozen at start");
        corrections.forEach(correction -> rejectIfInapplicable(version, correction));

        List<RectificationChange> changes = new ArrayList<>();
        for (Correction correction : corrections) {
            changes.add(apply(correction));
        }
        Rectification rectification =
                new Rectification(rectificationId, author, at, reason, changes);
        rectifications.add(rectification);
        return rectification;
    }

    private void rejectIfInapplicable(SchemaVersion version, Correction correction) {
        switch (correction) {
            case Correction.AnswerCorrection answerCorrection -> {
                requireRecord(answerCorrection.criterionId());
                version.requireCriterion(answerCorrection.criterionId()).rule()
                        .admissibilityViolation(answerCorrection.answer())
                        .ifPresent(violation -> {
                            throw new DomainException("answer refused for criterion "
                                    + answerCorrection.criterionId() + ": " + violation);
                        });
            }
            case Correction.EvidenceReferenceCorrection evidenceCorrection ->
                    requireRecord(evidenceCorrection.criterionId())
                            .requireEvidence(evidenceCorrection.evidenceId());
            case Correction.NoteCorrection noteCorrection -> requireNote(noteCorrection.noteId());
        }
    }

    private RectificationChange apply(Correction correction) {
        return switch (correction) {
            case Correction.AnswerCorrection answerCorrection -> {
                CriterionRecord record = requireRecord(answerCorrection.criterionId());
                String previous = record.answer().map(Answer::describe).orElse(null);
                record.recordAnswer(answerCorrection.answer());
                yield new RectificationChange.AnswerCorrected(answerCorrection.criterionId(), previous,
                        answerCorrection.answer().describe());
            }
            case Correction.EvidenceReferenceCorrection evidenceCorrection -> {
                CriterionRecord record = requireRecord(evidenceCorrection.criterionId());
                String previous = record.requireEvidence(evidenceCorrection.evidenceId()).reference();
                record.replaceReference(evidenceCorrection.evidenceId(), evidenceCorrection.reference());
                yield new RectificationChange.EvidenceReferenceChanged(evidenceCorrection.criterionId(),
                        evidenceCorrection.evidenceId(), previous, evidenceCorrection.reference());
            }
            case Correction.NoteCorrection noteCorrection -> {
                InspectionNote existing = requireNote(noteCorrection.noteId());
                notes.set(notes.indexOf(existing), existing.withText(noteCorrection.text()));
                yield new RectificationChange.NoteCorrected(noteCorrection.noteId(), existing.text(),
                        noteCorrection.text());
            }
        };
    }

    public void appendRectifiedEvaluation(CriterionId criterionId, CriterionEvaluation evaluation) {
        Validate.ensure(status.closed(), "only a closed inspection carries rectified evaluations");
        requireRecord(criterionId).appendRectifiedEvaluation(evaluation);
    }

    public CriterionRecord requireRecord(CriterionId criterionId) {
        CriterionRecord record = records.get(criterionId);
        if (record == null) {
            throw new DomainException("criterion " + criterionId + " does not belong to inspection " + id);
        }
        return record;
    }

    public InspectionNote requireNote(String noteId) {
        return notes.stream()
                .filter(note -> note.id().equals(noteId))
                .findFirst()
                .orElseThrow(() -> new DomainException("note " + noteId + " does not exist"));
    }

    public List<InspectionNote> notes() {
        return List.copyOf(notes);
    }

    public List<Rectification> rectifications() {
        return List.copyOf(rectifications);
    }

    public Map<CriterionId, CriterionEvaluation> currentEvaluations() {
        Map<CriterionId, CriterionEvaluation> current = new LinkedHashMap<>();
        records.forEach((criterionId, record) -> record.currentEvaluation()
                .ifPresent(evaluation -> current.put(criterionId, evaluation)));
        return Map.copyOf(current);
    }

    private void requireInProgress(String operation) {
        requireStatus(InspectionStatus.IN_PROGRESS, operation);
    }

    private void requireStatus(InspectionStatus expected, String operation) {
        if (status != expected) {
            throw new DomainException("cannot " + operation + " inspection " + id + " while it is "
                    + status);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Inspection that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Inspection " + id + " (" + status + ")";
    }
}
