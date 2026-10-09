package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceRequirement;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceShortfall;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericBand;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericRangeRule;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaValueEdgeTest {

    @Test
    @DisplayName("evidence requirements distinguish mandatory and optional evidence")
    void evidenceRequirementsDistinguishMandatoryAndOptionalEvidence() {
        EvidenceRequirement optional = new EvidenceRequirement(EvidenceType.PHOTOGRAPH, "photo", false, 2);
        EvidenceRequirement mandatory = EvidenceRequirement.mandatory(EvidenceType.DOCUMENT, "manual");

        assertThat(optional.satisfiedBy(0)).isTrue();
        assertThat(mandatory.satisfiedBy(0)).isFalse();
        EvidenceShortfall shortfall = mandatory.shortfall(0);
        assertThat(shortfall.describe()).isEqualTo("required 1 DOCUMENT (manual), presented 0");
        assertThatThrownBy(() -> new EvidenceRequirement(EvidenceType.DOCUMENT, "manual", true, 0))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("criteria report evidence shortfalls and option answers describe themselves")
    void criteriaReportShortfallsAndOptionAnswersDescribeThemselves() {
        Criterion criterion = new Criterion(CriterionId.of("DOC"), validRule(), List.of(
                new EvidenceRequirement(EvidenceType.DOCUMENT, "manual", true, 2),
                new EvidenceRequirement(EvidenceType.PHOTOGRAPH, "photo", false, 1)));

        assertThat(criterion.shortfalls(Map.of("manual", 1))).singleElement()
                .satisfies(shortfall -> assertThat(shortfall.describe())
                        .isEqualTo("required 2 DOCUMENT (manual), presented 1"));
        assertThat(Criterion.of("TEMP", validRule()).evidenceRequirements()).isEmpty();
        assertThat(OptionAnswer.of(" clean ").optionKey()).isEqualTo("clean");
        assertThat(OptionAnswer.of("clean").describe()).isEqualTo("clean");
    }

    @Test
    @DisplayName("publication results and schema versions reject inconsistent states")
    void publicationResultsAndSchemaVersionsRejectInconsistentStates() {
        SchemaVersion version = versionWith(Criterion.of("PH", validRule()));
        PublicationResult published = PublicationResult.published(version);
        PublicationResult refused = PublicationResult.refused(List.of("invalid"));

        assertThat(published.published()).isTrue();
        assertThat(published.publishedVersion()).isEqualTo(version);
        assertThatThrownBy(refused::publishedVersion)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("the draft was refused: invalid");
        assertThatThrownBy(() -> new PublicationResult(Optional.of(version), List.of("invalid")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("either yields a version");
    }

    @Test
    @DisplayName("schema versions sort sections and reject duplicate criteria")
    void schemaVersionsSortSectionsAndRejectDuplicateCriteria() {
        Criterion criterion = Criterion.of("PH", validRule());
        SchemaVersion version = new SchemaVersion(new SchemaVersionId(SchemaId.of("schema-1"), 1),
                List.of(Section.of("Second", 2, criterion),
                        Section.of("First", 1, Criterion.of("TEMP", validRule()))),
                Instant.parse("2026-03-01T10:00:00Z"));

        assertThat(version.sections()).extracting(Section::name).containsExactly("First", "Second");
        assertThat(version.criteria()).extracting(Criterion::id)
                .containsExactly(CriterionId.of("TEMP"), CriterionId.of("PH"));
        assertThat(version.findCriterion(CriterionId.of("missing"))).isEmpty();
        assertThatThrownBy(() -> version.requireCriterion(CriterionId.of("missing")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not belong");
        assertThatThrownBy(() -> new SchemaVersion(new SchemaVersionId(SchemaId.of("schema-1"), 2),
                List.of(Section.of("First", 1, criterion), Section.of("Duplicate", 2, criterion)),
                Instant.parse("2026-03-01T10:00:00Z")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("unique criterion ids");
    }

    @Test
    @DisplayName("inspection schemas cover draft, version lookup and identity edge cases")
    void inspectionSchemasCoverDraftVersionLookupAndIdentityEdges() {
        InspectionSchema schema = new InspectionSchema(SchemaId.of("schema-1"), "Inspection",
                Set.of(AssetType.LABORATORY, AssetType.FACTORY));
        InspectionSchema sameId = new InspectionSchema(SchemaId.of("schema-1"), "Other",
                Set.of(AssetType.FACTORY));

        schema.applyTo(AssetType.EQUIPMENT);
        assertThat(schema.appliesTo(AssetType.EQUIPMENT)).isTrue();
        assertThatThrownBy(() -> schema.applyTo(AssetType.EQUIPMENT))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already applies");
        schema.stopApplyingTo(AssetType.FACTORY);
        assertThat(schema.appliesTo(AssetType.FACTORY)).isFalse();
        assertThatThrownBy(() -> schema.stopApplyingTo(AssetType.FACTORY))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not apply");
        assertThatThrownBy(schema::requireDraft)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("has no open draft");
        schema.openDraft();
        assertThatThrownBy(schema::openDraft)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already has an open draft");
        schema.addSection(Section.of("Safety", 1, Criterion.of("PH", validRule())));
        SchemaVersion published = schema.publish(Instant.parse("2026-03-01T10:00:00Z")).publishedVersion();

        assertThat(schema.publishedVersions()).containsExactly(published);
        assertThat(schema.findVersion(new SchemaVersionId(SchemaId.of("other"), 1))).isEmpty();
        assertThat(schema).isEqualTo(sameId);
        assertThat(schema).isNotEqualTo("schema-1");
        assertThat(schema.hashCode()).isEqualTo(SchemaId.of("schema-1").hashCode());
        assertThat(schema.toString()).isEqualTo("Inspection (schema-1)");
    }

    @Test
    @DisplayName("schema applicability covers creation, duplicate owners and invalid transfers")
    void schemaApplicabilityCoversCreationDuplicatesAndInvalidTransfers() {
        SchemaApplicability applicability = new SchemaApplicability();
        InspectionSchema source = applicability.create(SchemaId.of("source"), "Source",
                Set.of(AssetType.LABORATORY, AssetType.FACTORY), Set.of());
        InspectionSchema target = applicability.create(SchemaId.of("target"), "Target",
                Set.of(AssetType.FACILITY), Set.of(AssetType.LABORATORY, AssetType.FACTORY));

        assertThat(source.applicableAssetTypes()).containsExactlyInAnyOrder(AssetType.LABORATORY, AssetType.FACTORY);
        assertThatThrownBy(() -> applicability.create(SchemaId.of("duplicate"), "Duplicate",
                Set.of(AssetType.LABORATORY), Set.of(AssetType.LABORATORY)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already covered");
        assertThatThrownBy(() -> applicability.applyTo(target, AssetType.LABORATORY,
                Optional.of(source.id())))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already covered");
        assertThatThrownBy(() -> applicability.stopApplyingTo(target, AssetType.LABORATORY))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not apply");
        assertThatThrownBy(() -> applicability.transfer(source, source, AssetType.LABORATORY,
                Optional.of(source.id())))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("another schema");
        assertThatThrownBy(() -> applicability.transfer(source, target, AssetType.EQUIPMENT,
                Optional.of(source.id())))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("source schema does not apply");
        assertThatThrownBy(() -> applicability.transfer(source, target, AssetType.FACTORY,
                Optional.empty()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("registered owner");
        assertThatThrownBy(() -> applicability.transfer(source, target, AssetType.FACTORY,
                Optional.of(source.id())))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("replacement schema must have a published version");
    }

    private SchemaVersion versionWith(Criterion criterion) {
        return new SchemaVersion(new SchemaVersionId(SchemaId.of("schema-1"), 1),
                List.of(Section.of("Safety", 1, criterion)), Instant.parse("2026-03-01T10:00:00Z"));
    }

    private NumericRangeRule validRule() {
        return new NumericRangeRule("ph", BigDecimal.ZERO, BigDecimal.TEN, List.of(
                new NumericBand(BigDecimal.ZERO, true, BigDecimal.TEN, true,
                        RuleOutcome.approved("OK", "ok"))));
    }

    @Test
    @DisplayName("a criterion needs a subsystem option, even an empty one")
    void aCriterionNeedsASubsystemOption() {
        assertThatThrownBy(() -> new Criterion(CriterionId.of("PH"), validRule(), List.of(), null))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("subsystem");
        assertThatThrownBy(() -> Criterion.of("PH", validRule(), null))
                .isInstanceOf(InvalidArgumentException.class);
    }

    @Test
    @DisplayName("asking whether a criterion weighs on nothing is refused, not answered")
    void weighingOnNullIsRefused() {
        Criterion criterion = Criterion.of("PH", validRule());
        assertThatThrownBy(() -> criterion.weighsOn(null))
                .isInstanceOf(InvalidArgumentException.class);
        assertThatThrownBy(() -> criterion.appliesToAssetHaving(null))
                .isInstanceOf(InvalidArgumentException.class);
    }

    @Test
    @DisplayName("a transversal criterion weighs on every part and on an asset with none")
    void aTransversalCriterionWeighsOnEverything() {
        Criterion transversal = Criterion.of("PH", validRule());
        Subsystem electrical = Subsystem.of("electrical installation");

        assertThat(transversal.weighsOn(electrical)).isTrue();
        assertThat(transversal.appliesToAssetHaving(Set.of(electrical))).isTrue();
        assertThat(transversal.appliesToAssetHaving(Set.of())).isTrue();
    }

    @Test
    @DisplayName("a criterion of one part does not apply to an asset without that part")
    void aPartCriterionDoesNotApplyElsewhere() {
        Subsystem electrical = Subsystem.of("electrical installation");
        Subsystem pressure = Subsystem.of("pressure system");
        Criterion ofElectrical = Criterion.of("PH", validRule(), electrical);

        assertThat(ofElectrical.appliesToAssetHaving(Set.of(electrical))).isTrue();
        assertThat(ofElectrical.appliesToAssetHaving(Set.of(pressure))).isFalse();
        assertThat(ofElectrical.appliesToAssetHaving(Set.of())).isTrue();
    }

    @Test
    @DisplayName("a version answers which part a criterion weighs on, and refuses a null part")
    void aVersionAnswersAboutParts() {
        Subsystem electrical = Subsystem.of("electrical installation");
        SchemaVersion version = new SchemaVersion(
                new SchemaVersionId(SchemaId.of("s"), 1),
                List.of(Section.of("Safety", 1, Criterion.of("PH", validRule(), electrical))),
                Instant.parse("2026-03-01T10:00:00Z"));

        assertThat(version.declaredSubsystems()).containsExactly(electrical);
        assertThat(version.criterionWeighsOn(CriterionId.of("PH"), electrical)).isTrue();
        assertThat(version.criterionWeighsOn(CriterionId.of("MISSING"), electrical)).isTrue();
        assertThatThrownBy(() -> version.criterionWeighsOn(CriterionId.of("PH"), null))
                .isInstanceOf(InvalidArgumentException.class);
    }
}
