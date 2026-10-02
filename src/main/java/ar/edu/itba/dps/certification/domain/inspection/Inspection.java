package ar.edu.itba.dps.certification.domain.inspection;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.evaluation.CriterionEvaluator;
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
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
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
    private SchemaVersion frozenSchemaVersion;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();
    private final CriterionEvaluator evaluator =
            new CriterionEvaluator();
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

    public void start(PartyId actor, SchemaVersion version, AssetSnapshot snapshot, Instant at) {
        requireStatus(InspectionStatus.ASSIGNED, "start");
        requireAssignedInspector(actor, "start");
        Validate.required(version, "schema version");
        Validate.required(snapshot, "asset snapshot");
        Validate.required(at, "start instant");
        Validate.ensure(snapshot.assetId().equals(assetId),
                "the snapshot belongs to a different asset");
        this.frozenSchemaVersion = version;
        this.frozenSchemaVersionId = version.id();
        this.assetSnapshot = snapshot;
        this.startedAt = at;
        this.status = InspectionStatus.IN_PROGRESS;
        for (Criterion criterion : version.criteria()) {
            records.put(criterion.id(), new CriterionRecord(criterion.id()));
        }
    }

    public void recordAnswer(PartyId actor, CriterionId criterionId, Answer answer) {
        requireInProgress("record an answer");
        requireAssignedInspector(actor, "record an answer");
        CriterionRecord record = requireRecord(criterionId);
        Validate.required(answer, "answer");
        frozenSchemaVersion.requireCriterion(criterionId).rule().admissibilityViolation(answer)
                .ifPresent(violation -> { throw new DomainException("answer refused for criterion "
                        + criterionId + ": " + violation); });
        record.recordAnswer(answer);
    }

    public void removeAnswer(PartyId actor, CriterionId criterionId) {
        requireInProgress("remove an answer");
        requireAssignedInspector(actor, "remove an answer");
        requireRecord(criterionId).clearAnswer();
    }

    /**
     * Attaches evidence against a requirement of the frozen version. The aggregate resolves the
     * requirement and its type, so a caller cannot attach evidence the version does not ask for.
     */
    public EvidenceRecord attachEvidence(PartyId actor, CriterionId criterionId, String requirementLabel,
            String evidenceId, String reference, Instant at) {
        requireInProgress("attach evidence");
        requireAssignedInspector(actor, "attach evidence");
        CriterionRecord record = requireRecord(criterionId);
        Validate.requiredText(requirementLabel, "evidence requirement label");
        var requirement = frozenSchemaVersion.requireCriterion(criterionId).evidenceRequirements().stream()
                .filter(declared -> declared.label().equals(requirementLabel.strip()))
                .findFirst().orElseThrow(() -> new DomainException("criterion " + criterionId
                        + " declares no evidence requirement labelled '" + requirementLabel + "'"));
        EvidenceRecord evidence = new EvidenceRecord(evidenceId, requirement.label(), requirement.type(),
                reference, at);
        record.attach(evidence);
        return evidence;
    }

    public EvidenceRecord removeEvidence(PartyId actor, CriterionId criterionId, String evidenceId) {
        requireInProgress("remove evidence");
        requireAssignedInspector(actor, "remove evidence");
        return requireRecord(criterionId).detach(evidenceId);
    }

    /** The note is authored by the acting inspector; the aggregate builds it so authorship cannot be forged. */
    public InspectionNote recordNote(PartyId actor, String noteId, Optional<CriterionId> criterionId,
            String text, Instant at) {
        requireInProgress("record a note");
        requireAssignedInspector(actor, "record a note");
        InspectionNote note = new InspectionNote(noteId, criterionId, text, actor, at);
        note.criterionId().ifPresent(this::requireRecord);
        Validate.ensure(notes.stream().noneMatch(existing -> existing.id().equals(note.id())),
                "note " + note.id() + " already exists");
        notes.add(note);
        return note;
    }

    public InspectionNote correctNote(PartyId actor, String noteId, String text) {
        requireInProgress("correct a note");
        requireAssignedInspector(actor, "correct a note");
        InspectionNote previous = requireNote(noteId);
        notes.set(notes.indexOf(previous), previous.withText(text));
        return previous;
    }

    public InspectionNote removeNote(PartyId actor, String noteId) {
        requireInProgress("remove a note");
        requireAssignedInspector(actor, "remove a note");
        InspectionNote removed = requireNote(noteId);
        notes.remove(removed);
        return removed;
    }

    public InspectionClosureResult close(PartyId actor, Instant at) {
        Validate.required(at, "closure instant");
        requireAssignedInspector(actor, "close");
        if (status.closed()) {
            return new InspectionClosureResult(id, closedAt, true, currentEvaluations());
        }
        requireInProgress("close");
        Validate.ensure(!at.isBefore(startedAt), "closure cannot precede the inspection start");
        Map<CriterionId, CriterionEvaluation> evaluations = new LinkedHashMap<>();
        for (Criterion criterion : frozenSchemaVersion.criteria()) {
            evaluations.put(criterion.id(), evaluator.evaluate(criterion, requireRecord(criterion.id()), at));
        }
        evaluations.forEach((criterionId, evaluation) -> requireRecord(criterionId).recordClosureEvaluation(evaluation));
        this.status = InspectionStatus.CLOSED;
        this.closedAt = at;
        return new InspectionClosureResult(id, at, false, evaluations);
    }

    public Rectification rectify(RectificationId rectificationId,
            PartyId author, Instant at, String reason, List<Correction> corrections) {
        Validate.ensure(status.closed(), "only a closed inspection can be rectified");
        requireAssignedInspector(author, "rectify");
        Validate.required(rectificationId, "rectification id");
        Validate.required(at, "rectification instant");
        Validate.requiredText(reason, "rectification reason");
        Validate.requiredNonEmpty(corrections, "corrections");
        Validate.ensure(!at.isBefore(closedAt), "rectification cannot precede closure");
        Validate.ensure(rectifications.stream().noneMatch(existing -> existing.id().equals(rectificationId)),
                "rectification " + rectificationId + " is already recorded");
        corrections = List.copyOf(corrections);
        corrections.forEach(correction -> rejectIfInapplicable(frozenSchemaVersion, correction));

        List<RectificationChange> changes = new ArrayList<>();
        for (Correction correction : corrections) {
            changes.add(apply(correction));
        }
        Rectification rectification =
                new Rectification(rectificationId, author, at, reason, changes);
        rectifications.add(rectification);
        for (CriterionId criterionId : rectification.affectedCriteria()) {
            CriterionRecord record = requireRecord(criterionId);
            CriterionEvaluation previous = record.currentEvaluation().orElseThrow();
            CriterionEvaluation current = evaluator.evaluate(frozenSchemaVersion.requireCriterion(criterionId),
                    record, at).asRectificationOf(rectificationId, at);
            if (previous.result() != current.result() || !previous.reasons().equals(current.reasons())) {
                recordEvaluationProducedBy(rectification, criterionId, current);
                // Announced whenever the evaluation changes, not only the result: a rejection that
                // comes back with different reasons is a new non-conformity that no verified
                // correction covers, and the certificate it backs must react to it.
                pendingEvents.add(new CriterionResultRevised(id, criterionId, previous.result(),
                        current.result(), rectificationId, reason, at));
            }
        }
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

    private void recordEvaluationProducedBy(Rectification rectification, CriterionId criterionId,
            CriterionEvaluation evaluation) {
        Validate.ensure(status.closed(), "only a closed inspection carries rectified evaluations");
        Validate.required(rectification, "rectification");
        Validate.required(criterionId, "criterion id");
        Validate.required(evaluation, "criterion evaluation");

        Validate.ensure(rectifications.stream().anyMatch(existing -> existing.id().equals(rectification.id())),
                "rectification " + rectification.id() + " is not recorded on inspection " + id);
        Validate.ensure(rectification.affectedCriteria().contains(criterionId),
                "rectification " + rectification.id() + " did not affect criterion " + criterionId);
        Validate.ensure(evaluation.rectificationId().filter(rectification.id()::equals).isPresent(),
                "evaluation must reference rectification " + rectification.id());

        CriterionRecord record = requireRecord(criterionId);
        Validate.ensure(record.evaluations().stream()
                        .noneMatch(existing -> existing.rectificationId()
                                .filter(rectification.id()::equals)
                                .isPresent()),
                "rectification " + rectification.id()
                        + " already produced an evaluation for criterion " + criterionId);

        record.appendRectifiedEvaluation(evaluation);
    }

    public List<DomainEvent> pendingEvents() {
        return List.copyOf(pendingEvents);
    }

    public void acknowledgeEvent(DomainEvent event) {
        pendingEvents.remove(event);
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

    private void requireAssignedInspector(PartyId actor, String operation) {
        Validate.required(actor, "acting party");
        Validate.ensure(actor.equals(inspector),
                "only the assigned inspector " + inspector + " may " + operation + " inspection " + id
                        + ", not " + actor);
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
