package ar.edu.itba.dps.certification.domain.schema.rule;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MappedOptionsRule implements EvaluationRule {

    private final Map<String, RuleOutcome> outcomeByOption;

    public MappedOptionsRule(Map<String, RuleOutcome> outcomeByOption) {
        Validate.required(outcomeByOption, "option outcomes");
        Validate.ensure(!outcomeByOption.isEmpty(), "at least one option must be declared");
        this.outcomeByOption = Map.copyOf(outcomeByOption);
    }

    @Override
    public Optional<String> admissibilityViolation(Answer answer) {
        if (!(answer instanceof OptionAnswer option)) {
            return Optional.of("an option answer is required");
        }
        if (!outcomeByOption.containsKey(option.optionKey())) {
            return Optional.of("option '" + option.optionKey() + "' is not admitted; admitted options are "
                    + String.join(", ", outcomeByOption.keySet()));
        }
        return Optional.empty();
    }

    @Override
    public RuleOutcome evaluate(Answer answer) {
        Optional<String> violation = admissibilityViolation(answer);
        if (violation.isPresent()) {
            throw new DomainException("cannot evaluate an inadmissible answer: " + violation.get());
        }
        return outcomeByOption.get(((OptionAnswer) answer).optionKey());
    }

    @Override
    public Set<RuleOutcome> declaredOutcomes() {
        return Set.copyOf(outcomeByOption.values());
    }

    @Override
    public List<String> publicationViolations() {
        return List.of();
    }
}
