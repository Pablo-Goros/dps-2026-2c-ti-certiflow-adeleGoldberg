package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceRequirement;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericBand;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericRangeRule;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

class SchemaPublicationTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-03-01T10:00:00Z");

    @Test
    @DisplayName("bands that leave a gap in the admissible domain block publication")
    void aGapBetweenBandsIsRejected() {
        List<String> violations = violationsOf(numericRule(
                band("0", true, "5", true, "LOW_OK", CriterionResult.APPROVED),
                band("6", true, "10", true, "HIGH_BAD", CriterionResult.REJECTED)));

        assertThat(violations).anyMatch(violation -> violation.contains("leave a gap"));
    }

    @Test
    @DisplayName("bands that overlap block publication")
    void anOverlapBetweenBandsIsRejected() {
        List<String> violations = violationsOf(numericRule(
                band("0", true, "6", true, "LOW_OK", CriterionResult.APPROVED),
                band("5", true, "10", true, "HIGH_BAD", CriterionResult.REJECTED)));

        assertThat(violations).anyMatch(violation -> violation.contains("overlap"));
    }

    @Test
    @DisplayName("two adjacent bands that both include the shared boundary block publication")
    void aSharedBoundaryIncludedTwiceIsRejected() {
        List<String> violations = violationsOf(numericRule(
                band("0", true, "5", true, "LOW_OK", CriterionResult.APPROVED),
                band("5", true, "10", true, "HIGH_BAD", CriterionResult.REJECTED)));

        assertThat(violations).anyMatch(violation -> violation.contains("both include"));
    }

    @Test
    @DisplayName("two adjacent bands that both exclude the shared boundary block publication")
    void aSharedBoundaryExcludedTwiceIsRejected() {
        List<String> violations = violationsOf(numericRule(
                band("0", true, "5", false, "LOW_OK", CriterionResult.APPROVED),
                band("5", false, "10", true, "HIGH_BAD", CriterionResult.REJECTED)));

        assertThat(violations).anyMatch(violation -> violation.contains("both exclude"));
    }

    @Test
    @DisplayName("bands that do not reach the declared bounds block publication")
    void notCoveringTheDeclaredDomainIsRejected() {
        List<String> violations = violationsOf(numericRule(
                band("1", true, "5", false, "LOW_OK", CriterionResult.APPROVED),
                band("5", true, "9", true, "HIGH_BAD", CriterionResult.REJECTED)));

        assertThat(violations).anyMatch(violation -> violation.contains("start at the admissible minimum"));
        assertThat(violations).anyMatch(violation -> violation.contains("end at the admissible maximum"));
    }

    @Test
    @DisplayName("validation reports every violation at once instead of failing on the first")
    void allViolationsAreReportedTogether() {
        List<String> violations = violationsOf(numericRule(
                band("1", true, "5", true, "LOW_OK", CriterionResult.APPROVED),
                band("5", true, "9", true, "HIGH_BAD", CriterionResult.REJECTED)));

        assertThat(violations).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("version numbers are sequential and assigned at publication")
    void versionsAreNumberedSequentially() {
        InspectionSchema schema = aSchema();
        SchemaVersion first = publishValid(schema);
        SchemaVersion second = publishValid(schema);

        assertThat(first.number()).isEqualTo(1);
        assertThat(second.number()).isEqualTo(2);
        assertThat(schema.latestPublishedVersion()).contains(second);
        assertThat(schema.findVersion(1)).contains(first);
    }

    @Test
    @DisplayName("publishing closes the draft, so a further publication needs a new one")
    void publishingClosesTheDraft() {
        InspectionSchema schema = aSchema();
        publishValid(schema);

        assertThat(schema.draft()).isEmpty();
    }

    private SchemaVersion publishValid(InspectionSchema schema) {
        schema.openDraft();
        if (schema.requireDraft().sections().isEmpty()) {
            schema.requireDraft().addSection(Section.of("Safety", 1, criterion(validNumericRule())));
        }
        return schema.publish(PUBLISHED_AT).publishedVersion();
    }

    private List<String> violationsOf(NumericRangeRule rule) {
        InspectionSchema schema = aSchema();
        schema.openDraft();
        schema.requireDraft().addSection(Section.of("Safety", 1, criterion(rule)));
        PublicationResult result = schema.publish(PUBLISHED_AT);
        assertThat(result.published()).isFalse();
        return result.violations();
    }

    @Test
    @DisplayName("a criterion cannot be built with two evidence requirements sharing a label")
    void ambiguousEvidenceLabelsAreRejected() {
        assertThatThrownBy(() -> new Criterion(CriterionId.of("PH"), validNumericRule(),
                List.of(EvidenceRequirement.mandatory(EvidenceType.DOCUMENT, "proof"),
                        EvidenceRequirement.mandatory(EvidenceType.PHOTOGRAPH, "proof"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("same label");
    }

    @Test
    @DisplayName("a section with no criteria is not a section")
    void aSectionMustHoldAtLeastOneCriterion() {
        assertThatThrownBy(() -> Section.of("Safety", 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("criteria of section 'Safety'");
    }

    @Test
    @DisplayName("a draft with no sections cannot be published")
    void anEmptyDraftIsNotPublishable() {
        InspectionSchema schema = aSchema();
        schema.openDraft();

        PublicationResult result = schema.publish(PUBLISHED_AT);

        assertThat(result.published()).isFalse();
        assertThat(result.violations()).containsExactly("a version must declare at least one section");
        assertThat(schema.draft()).isPresent();
    }

    private InspectionSchema aSchema() {
        return new InspectionSchema(SchemaId.of("schema-1"), "Laboratory inspection",
                Set.of(AssetType.LABORATORY));
    }

    private Criterion criterion(NumericRangeRule rule) {
        return new Criterion(CriterionId.of("PH"), rule, List.of());
    }

    private NumericRangeRule validNumericRule() {
        return numericRule(
                band("0", true, "5", false, "LOW_OK", CriterionResult.APPROVED),
                band("5", true, "10", true, "HIGH_BAD", CriterionResult.REJECTED));
    }

    private NumericRangeRule numericRule(NumericBand... bands) {
        return new NumericRangeRule("ph", new BigDecimal("0"), new BigDecimal("10"), List.of(bands));
    }

    private static NumericBand band(String lower, boolean lowerInclusive, String upper,
            boolean upperInclusive, String criterionId, CriterionResult result) {
        return new NumericBand(new BigDecimal(lower), lowerInclusive, new BigDecimal(upper),
                upperInclusive, result.approved()
                        ? RuleOutcome.approved(criterionId, criterionId)
                        : RuleOutcome.rejected(criterionId, Severity.CRITICAL, criterionId));
    }
}
