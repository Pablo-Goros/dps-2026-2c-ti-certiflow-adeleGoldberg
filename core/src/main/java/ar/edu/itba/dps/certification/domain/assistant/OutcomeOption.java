package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Optional;

public record OutcomeOption(String code, CriterionResult result, Optional<Severity> severity, String description) {

    public OutcomeOption {
        code = Validate.requiredText(code, "outcome code");
        Validate.required(result, "outcome result");
        Validate.required(severity, "outcome severity");
        description = Validate.requiredText(description, "outcome description");
    }

    public static OutcomeOption of(RuleOutcome outcome) {
        return new OutcomeOption(outcome.code(), outcome.result(), Optional.ofNullable(outcome.severity()),
                outcome.description());
    }
}
