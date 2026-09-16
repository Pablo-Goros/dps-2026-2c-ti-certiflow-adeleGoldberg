package ar.edu.itba.dps.certification.domain.schema.rule;

import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EvaluationRule {

    Optional<String> admissibilityViolation(Answer answer);

    RuleOutcome evaluate(Answer answer);

    Set<RuleOutcome> declaredOutcomes();

    List<String> publicationViolations();

}
