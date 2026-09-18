package ar.edu.itba.dps.certification.domain.evaluation;

import ar.edu.itba.dps.certification.domain.schema.rule.YesNoRule;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceRequirement;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.inspection.CriterionRecord;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.Instant;
import java.util.List;

class CriterionEvaluatorTest {

    private final CriterionEvaluator evaluator = new CriterionEvaluator();
    private final Instant evaluatedAt = Instant.parse("2026-03-10T12:00:00Z");
    private DomainWorld world;
    private SchemaVersion version;

    @BeforeEach
    void setUp() {
        world = new DomainWorld();
        AssetType laboratory = AssetType.LABORATORY;
        version = world.publishLaboratorySchema(laboratory);
    }

    @Test
    @DisplayName("severity comes from the concrete result, so one criterion yields different severities")
    void severityVariesWithTheRecordedMeasurement() {
        CriterionEvaluation warm = evaluate(DomainWorld.TEMPERATURE, Measurement.of("20", "c"));
        CriterionEvaluation hot = evaluate(DomainWorld.TEMPERATURE, Measurement.of("30", "c"));

        assertThat(warm.result()).isEqualTo(CriterionResult.OBSERVED);
        assertThat(warm.severity()).isEqualTo(Severity.LOW);

        assertThat(hot.result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(hot.severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    @DisplayName("an approved criterion carries no reasons and no severity")
    void approvedCriterionCarriesNothingElse() {
        CriterionEvaluation evaluation = evaluate(DomainWorld.TEMPERATURE, Measurement.of("5", "c"));

        assertThat(evaluation.result()).isEqualTo(CriterionResult.APPROVED);
        assertThat(evaluation.reasons()).isEmpty();
        assertThat(evaluation.optionalSeverity()).isEmpty();
    }

    @Test
    @DisplayName("a missing answer rejects the criterion with the reserved outcome")
    void missingAnswerRejects() {
        CriterionRecord record = new CriterionRecord(DomainWorld.TEMPERATURE);

        CriterionEvaluation evaluation = evaluator.evaluate(
                version.requireCriterion(DomainWorld.TEMPERATURE), record, evaluatedAt);

        assertThat(evaluation.result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(evaluation.reasons()).singleElement()
                .isInstanceOf(EvaluationReason.MissingAnswer.class);
        assertThat(evaluation.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("missing mandatory evidence forces rejection and keeps the rule verdict as a motive")
    void missingEvidenceForcesRejectionWithoutDiscardingTheRuleVerdict() {
        CriterionRecord record = new CriterionRecord(DomainWorld.DOCUMENTATION,
                YesNoAnswer.no(), List.of());

        CriterionEvaluation evaluation = evaluator.evaluate(
                version.requireCriterion(DomainWorld.DOCUMENTATION), record, evaluatedAt);

        assertThat(evaluation.result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(evaluation.reasons()).hasSize(2);
        assertThat(evaluation.reasons()).anySatisfy(reason ->
                assertThat(reason).isInstanceOf(EvaluationReason.RuleVerdict.class));
        assertThat(evaluation.reasons()).anySatisfy(reason ->
                assertThat(reason).isInstanceOf(EvaluationReason.MissingEvidence.class));
        assertThat(evaluation.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("an observed rule verdict stands on its own once its evidence is present")
    void satisfiedEvidenceLeavesTheRuleVerdictAlone() {
        CriterionRecord record = new CriterionRecord(DomainWorld.DOCUMENTATION,
                YesNoAnswer.no(), List.of(new EvidenceRecord("ev-1", DomainWorld.SAFETY_MANUAL,
                        EvidenceType.DOCUMENT, "file://manual.pdf", evaluatedAt)));

        CriterionEvaluation evaluation = evaluator.evaluate(
                version.requireCriterion(DomainWorld.DOCUMENTATION), record, evaluatedAt);

        assertThat(evaluation.result()).isEqualTo(CriterionResult.OBSERVED);
        assertThat(evaluation.reasons()).singleElement()
                .isInstanceOf(EvaluationReason.RuleVerdict.class);
        assertThat(evaluation.severity()).isEqualTo(Severity.LOW);
    }

    @Test
    @DisplayName("each mandatory evidence requirement is counted on its own")
    void everyMandatoryRequirementIsCountedSeparately() {
        Criterion criterion = new Criterion(DomainWorld.DOCUMENTATION,
                new YesNoRule(RuleOutcome.approved("DOC_OK", "documentation is current"),
                        RuleOutcome.observed("DOC_PARTIAL", Severity.LOW, "incomplete")),
                List.of(EvidenceRequirement.mandatory(EvidenceType.DOCUMENT, "safety manual"),
                        EvidenceRequirement.mandatory(EvidenceType.PHOTOGRAPH, "signage photo")));
        CriterionRecord record = new CriterionRecord(DomainWorld.DOCUMENTATION, YesNoAnswer.yes(),
                List.of(new EvidenceRecord("ev-1", "safety manual", EvidenceType.DOCUMENT,
                        "file://manual.pdf", evaluatedAt)));

        CriterionEvaluation evaluation = evaluator.evaluate(criterion, record, evaluatedAt);

        assertThat(evaluation.result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(evaluation.reasons()).singleElement()
                .isInstanceOfSatisfying(EvaluationReason.MissingEvidence.class, missing ->
                        assertThat(missing.shortfall().label()).isEqualTo("signage photo"));
    }

    private CriterionEvaluation evaluate(
            CriterionId criterionId,
            Answer answer) {
        CriterionRecord record = new CriterionRecord(criterionId, answer, List.of());
        return evaluator.evaluate(version.requireCriterion(criterionId), record, evaluatedAt);
    }
}
