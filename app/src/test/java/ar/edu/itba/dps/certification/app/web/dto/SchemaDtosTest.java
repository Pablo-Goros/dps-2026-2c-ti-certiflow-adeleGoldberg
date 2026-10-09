package ar.edu.itba.dps.certification.app.web.dto;

import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.Band;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.CriterionDto;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.Evidence;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.Outcome;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.Rule;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.SectionDto;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaDtosTest {

    private static final Outcome OK = new Outcome("OK", CriterionResult.APPROVED, null, "fine");
    private static final Outcome BAD = new Outcome("BAD", CriterionResult.REJECTED, Severity.HIGH, "broken");

    private static SectionDto section(Rule rule) {
        return new SectionDto("Safety", 1, List.of(new CriterionDto("C1", rule, "pressure system",
                List.of(new Evidence(EvidenceType.DOCUMENT, "manual", true, 1)))));
    }

    @Test
    void aYesNoRuleSurvivesTheRoundTrip() {
        var rule = new Rule("YES_NO", OK, BAD, null, null, null, null, null);

        var back = SectionDto.of(section(rule).toDomain());

        assertThat(back.criteria().get(0).rule()).isEqualTo(rule);
        assertThat(back.criteria().get(0).subsystem()).isEqualTo("pressure system");
        assertThat(back.criteria().get(0).evidence()).hasSize(1);
    }

    @Test
    void anOptionsRuleSurvivesTheRoundTrip() {
        var rule = new Rule("OPTIONS", null, null, Map.of("clean", OK, "dirty", BAD), null, null, null, null);

        var back = SectionDto.of(section(rule).toDomain());

        assertThat(back.criteria().get(0).rule().options()).isEqualTo(rule.options());
    }

    @Test
    void aNumericRuleSurvivesTheRoundTrip() {
        var band = new Band(new BigDecimal("0"), true, new BigDecimal("10"), true, OK);
        var rule = new Rule("NUMERIC_RANGE", null, null, null, "c", new BigDecimal("-5"),
                new BigDecimal("50"), List.of(band));

        var back = SectionDto.of(section(rule).toDomain());

        assertThat(back.criteria().get(0).rule()).isEqualTo(rule);
    }

    @Test
    void anUnknownRuleTypeIsRejectedAsInvalidInput() {
        var rule = new Rule("MAGIC", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> section(rule).toDomain()).isInstanceOf(InvalidArgumentException.class);
    }

    @Test
    void aRuleMissingItsPartsIsRejectedAsInvalidInput() {
        var rule = new Rule("YES_NO", OK, null, null, null, null, null, null);

        assertThatThrownBy(() -> section(rule).toDomain()).isInstanceOf(InvalidArgumentException.class);
    }

    @Test
    void anApprovingOutcomeWithASeverityIsRefusedByTheCore() {
        var invalid = new Outcome("X", CriterionResult.APPROVED, Severity.LOW, "contradiction");
        var rule = new Rule("YES_NO", invalid, BAD, null, null, null, null, null);

        assertThatThrownBy(() -> section(rule).toDomain()).isInstanceOf(RuntimeException.class);
    }
}
