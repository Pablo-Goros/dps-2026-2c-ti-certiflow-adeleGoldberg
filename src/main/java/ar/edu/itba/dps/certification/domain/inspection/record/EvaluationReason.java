package ar.edu.itba.dps.certification.domain.inspection.record;

import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceShortfall;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.shared.Validate;

public sealed interface EvaluationReason {

    Severity MISSING_MANDATORY_DATA = Severity.HIGH;

    CriterionResult result();

    Severity severity();

    String describe();

    record RuleVerdict(RuleOutcome outcome) implements EvaluationReason {

        public RuleVerdict {
            Validate.required(outcome, "rule outcome");
            Validate.ensure(!outcome.result().approved(),
                    "an approving outcome is not a reason for a non-approved criterion");
        }

        @Override
        public CriterionResult result() {
            return outcome.result();
        }

        @Override
        public Severity severity() {
            return outcome.severity();
        }

        @Override
        public String describe() {
            return outcome.description();
        }
    }

    record MissingAnswer() implements EvaluationReason {

        @Override
        public CriterionResult result() {
            return CriterionResult.REJECTED;
        }

        @Override
        public Severity severity() {
            return MISSING_MANDATORY_DATA;
        }

        @Override
        public String describe() {
            return "no answer was recorded for a criterion that requires one";
        }
    }

    record MissingEvidence(EvidenceShortfall shortfall) implements EvaluationReason {

        public MissingEvidence {
            Validate.required(shortfall, "evidence shortfall");
        }

        @Override
        public CriterionResult result() {
            return CriterionResult.REJECTED;
        }

        @Override
        public Severity severity() {
            return MISSING_MANDATORY_DATA;
        }

        @Override
        public String describe() {
            return shortfall.describe();
        }
    }
}
