package ar.edu.itba.dps.certification.domain.schema.rule;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class YesNoRule implements EvaluationRule {

    private final RuleOutcome whenAffirmative;
    private final RuleOutcome whenNegative;

    public YesNoRule(RuleOutcome whenAffirmative, RuleOutcome whenNegative) {
        this.whenAffirmative = Validate.required(whenAffirmative, "outcome for yes");
        this.whenNegative = Validate.required(whenNegative, "outcome for no");
    }

    @Override
    public Optional<String> admissibilityViolation(Answer answer) {
        return answer instanceof YesNoAnswer
                ? Optional.empty()
                : Optional.of("a yes/no answer is required");
    }

    @Override
    public RuleOutcome evaluate(Answer answer) {
        if (!(answer instanceof YesNoAnswer yesNo)) {
            throw new DomainException("cannot evaluate an inadmissible answer: a yes/no answer is required");
        }
        return yesNo.affirmative() ? whenAffirmative : whenNegative;
    }

    @Override
    public Set<RuleOutcome> declaredOutcomes() {
        return Set.of(whenAffirmative, whenNegative);
    }

    @Override
    public List<String> publicationViolations() {
        return List.of();
    }
}
