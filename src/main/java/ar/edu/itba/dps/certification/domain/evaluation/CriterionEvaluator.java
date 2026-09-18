package ar.edu.itba.dps.certification.domain.evaluation;

import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.CriterionRecord;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceShortfall;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CriterionEvaluator {

    public CriterionEvaluation evaluate(Criterion criterion, CriterionRecord record,
            SchemaVersion version, Instant evaluatedAt) {
        Validate.required(criterion, "criterion");
        Validate.required(record, "criterion record");
        Validate.required(version, "schema version");
        Validate.required(evaluatedAt, "evaluation instant");

        List<EvaluationReason> reasons = new ArrayList<>();
        CriterionResult result = CriterionResult.APPROVED;

        Optional<Answer> answer = record.answer();
        if (answer.isEmpty()) {
            reasons.add(new EvaluationReason.MissingAnswer());
            result = CriterionResult.REJECTED;
        } else {
            RuleOutcome outcome = applyRule(criterion, answer.get());
            if (!outcome.result().approved()) {
                reasons.add(new EvaluationReason.RuleVerdict(outcome));
                result = result.worstOf(outcome.result());
            }
        }

        for (EvidenceShortfall shortfall : criterion.shortfalls(record.evidenceCountByRequirement())) {
            reasons.add(new EvaluationReason.MissingEvidence(shortfall));
            result = result.worstOf(CriterionResult.REJECTED);
        }

        if (result.approved()) {
            return CriterionEvaluation.approved(evaluatedAt);
        }
        return CriterionEvaluation.nonApproved(result, reasons, severityOf(reasons), evaluatedAt);
    }

    private RuleOutcome applyRule(Criterion criterion, Answer answer) {
        Optional<String> violation = criterion.rule().admissibilityViolation(answer);
        if (violation.isPresent()) {
            throw new DomainException("criterion " + criterion.id()
                    + " holds an answer its rule does not admit: " + violation.get());
        }
        return criterion.rule().evaluate(answer);
    }

    private Severity severityOf(List<EvaluationReason> reasons) {
        return reasons.stream()
                .map(EvaluationReason::severity)
                .reduce(Severity::maxOf)
                .orElseThrow(() -> new DomainException("a non-approved criterion must carry a reason"));
    }
}
