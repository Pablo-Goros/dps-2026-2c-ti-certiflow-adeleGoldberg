package ar.edu.itba.dps.certification.domain.schema.rule;

import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;

public record RuleOutcome(String code, CriterionResult result, Severity severity, String description) {

    public RuleOutcome {
        code = Validate.requiredText(code, "outcome code");
        Validate.required(result, "criterion result");
        description = Validate.requiredText(description, "outcome description");
        Validate.ensure(result.approved() == (severity == null),
                "an approving outcome carries no severity and a non-approving one must carry it");
    }

    public static RuleOutcome approved(String code, String description) {
        return new RuleOutcome(code, CriterionResult.APPROVED, null, description);
    }

    public static RuleOutcome observed(String code, Severity severity, String description) {
        return new RuleOutcome(code, CriterionResult.OBSERVED, severity, description);
    }

    public static RuleOutcome rejected(String code, Severity severity, String description) {
        return new RuleOutcome(code, CriterionResult.REJECTED, severity, description);
    }
}
