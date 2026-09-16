package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.schema.rule.MappedOptionsRule;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Map;

class MappedOptionsRuleTest {

    private final MappedOptionsRule rule = new MappedOptionsRule(Map.of(
            "clean", RuleOutcome.approved("HK_OK", "area is clean"),
            "untidy", RuleOutcome.observed("HK_UNTIDY", Severity.MEDIUM, "area is untidy"),
            "hazardous", RuleOutcome.rejected("HK_HAZARD", Severity.HIGH, "area is unsafe")));

    @Test
    @DisplayName("each declared option maps to its assigned outcome")
    void everyDeclaredOptionHasItsOwnOutcome() {
        assertThat(rule.evaluate(OptionAnswer.of("clean")).result())
                .isEqualTo(CriterionResult.APPROVED);
        assertThat(rule.evaluate(OptionAnswer.of("untidy")).result())
                .isEqualTo(CriterionResult.OBSERVED);
        assertThat(rule.evaluate(OptionAnswer.of("hazardous")).result())
                .isEqualTo(CriterionResult.REJECTED);
    }

    @Test
    @DisplayName("an option the criterion never declared is refused at capture")
    void anUndeclaredOptionIsRefused() {
        assertThat(rule.admissibilityViolation(OptionAnswer.of("sparkling")))
                .hasValueSatisfying(violation -> assertThat(violation).contains("is not admitted"));
    }

    @Test
    @DisplayName("an answer of the wrong shape is refused at capture")
    void anAnswerOfTheWrongShapeIsRefused() {
        assertThat(rule.admissibilityViolation(Measurement.of("5", "c"))).isPresent();
        assertThat(rule.admissibilityViolation(YesNoAnswer.yes())).isPresent();
    }

    @Test
    @DisplayName("an inadmissible answer cannot be evaluated")
    void anInadmissibleAnswerCannotBeEvaluated() {
        assertThatThrownBy(() -> rule.evaluate(OptionAnswer.of("sparkling")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("inadmissible");
    }

    @Test
    @DisplayName("the rule is total by construction, so it never blocks publication")
    void theRuleIsTotalByConstruction() {
        assertThat(rule.publicationViolations()).isEmpty();
        assertThat(rule.declaredOutcomes()).hasSize(3);
        assertThat(rule.declaredOutcomes()).anyMatch(outcome -> outcome.severity() != null);
    }

    @Test
    @DisplayName("a rule with no options is refused")
    void aRuleWithoutOptionsIsRefused() {
        assertThatThrownBy(() -> new MappedOptionsRule(Map.of()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("at least one option");
    }
}
