package ar.edu.itba.dps.certification.domain.schema.rule;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RuleBoundaryRegressionTest {
    private final RuleOutcome approved = RuleOutcome.approved("OK", "Approved");

    @Test
    void yesAndNoMayProduceTheSameDeclaredOutcome() {
        var rule = new YesNoRule(approved, approved);
        assertThat(rule.declaredOutcomes()).containsExactly(approved);
    }

    @Test
    void optionNamesUseTheSameNormalizationAsTheirAnswers() {
        var rule = new MappedOptionsRule(Map.of(" clean ", approved));
        assertThat(rule.evaluate(OptionAnswer.of(" clean "))).isEqualTo(approved);
        assertThat(rule.options()).containsOnlyKeys("clean");
    }

    @Test
    void normalizationCannotSilentlyMergeTwoOptions() {
        assertThatThrownBy(() -> new MappedOptionsRule(Map.of("clean", approved, " clean ", approved)))
                .isInstanceOf(DomainException.class).hasMessageContaining("duplicate option");
    }

    @Test
    void aBlankOptionCannotCreateAnUnanswerablePublishedRule() {
        assertThatThrownBy(() -> new MappedOptionsRule(Map.of(" ", approved)))
                .isInstanceOf(DomainException.class).hasMessageContaining("option key");
    }
}
