package ar.edu.itba.dps.certification.domain.inspection;

import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CriterionRecord {

    private final CriterionId criterionId;
    private final List<EvidenceRecord> evidence = new ArrayList<>();
    private final List<CriterionEvaluation> evaluations = new ArrayList<>();
    private Answer answer;

    public CriterionRecord(CriterionId criterionId) {
        this.criterionId = Validate.required(criterionId, "criterion id");
    }

    public CriterionRecord(CriterionId criterionId, Answer answer,
            List<EvidenceRecord> capturedEvidence) {
        this(criterionId);
        this.answer = answer;
        this.evidence.addAll(Validate.required(capturedEvidence, "captured evidence"));
    }

    public CriterionId criterionId() {
        return criterionId;
    }

    public Optional<Answer> answer() {
        return Optional.ofNullable(answer);
    }

    void recordAnswer(Answer newAnswer) {
        answer = Validate.required(newAnswer, "answer");
    }

    void clearAnswer() {
        answer = null;
    }

    public List<EvidenceRecord> evidence() {
        return List.copyOf(evidence);
    }

    void attach(EvidenceRecord record) {
        Validate.required(record, "evidence record");
        Validate.ensure(evidence.stream().noneMatch(existing -> existing.id().equals(record.id())),
                "evidence " + record.id() + " is already attached");
        evidence.add(record);
    }

    EvidenceRecord detach(String evidenceId) {
        EvidenceRecord found = requireEvidence(evidenceId);
        evidence.remove(found);
        return found;
    }

    EvidenceRecord replaceReference(String evidenceId, String newReference) {
        EvidenceRecord found = requireEvidence(evidenceId);
        EvidenceRecord replacement = found.withReference(newReference);
        evidence.set(evidence.indexOf(found), replacement);
        return replacement;
    }

    public EvidenceRecord requireEvidence(String evidenceId) {
        return evidence.stream()
                .filter(record -> record.id().equals(evidenceId))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "evidence " + evidenceId + " is not attached to criterion " + criterionId));
    }

    public Map<String, Integer> evidenceCountByRequirement() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (EvidenceRecord record : evidence) {
            counts.merge(record.requirementLabel(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    void recordClosureEvaluation(CriterionEvaluation evaluation) {
        Validate.required(evaluation, "evaluation");
        Validate.ensure(!evaluation.fromRectification(),
                "a closure evaluation cannot carry a rectification id");
        Validate.ensure(evaluations.isEmpty(),
                "criterion " + criterionId + " has already been evaluated at close");
        evaluations.add(evaluation);
    }

    void appendRectifiedEvaluation(CriterionEvaluation evaluation) {
        Validate.required(evaluation, "evaluation");
        Validate.ensure(evaluation.fromRectification(),
                "a rectified evaluation must carry the rectification that produced it");
        Validate.ensure(!evaluations.isEmpty(),
                "criterion " + criterionId + " cannot be rectified before it has been evaluated");
        evaluations.add(evaluation);
    }

    public List<CriterionEvaluation> evaluations() {
        return List.copyOf(evaluations);
    }

    public Optional<CriterionEvaluation> originalEvaluation() {
        return evaluations.isEmpty() ? Optional.empty() : Optional.of(evaluations.getFirst());
    }

    public Optional<CriterionEvaluation> currentEvaluation() {
        return evaluations.isEmpty() ? Optional.empty() : Optional.of(evaluations.getLast());
    }
}
