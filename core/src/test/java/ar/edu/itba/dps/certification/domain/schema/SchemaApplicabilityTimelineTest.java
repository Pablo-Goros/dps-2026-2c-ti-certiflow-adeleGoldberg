package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.support.DomainWorld;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaApplicabilityTimelineTest {
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant FUTURE = NOW.plusSeconds(30 * 86400);
    private final SchemaApplicability applicability = new SchemaApplicability();

    @Test
    void completeFutureVersionCannotHideIncompleteCurrentVersion() {
        InspectionSchema schema = schema("target", Set.of(AssetType.EQUIPMENT));
        publish(schema, false, NOW, NOW);
        publish(schema, true, NOW, FUTURE);

        assertThatThrownBy(() -> applicability.applyTo(schema, AssetType.FACTORY, Optional.empty(), NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("evaluates no criterion for");
        assertThat(schema.applicableAssetTypes()).containsExactly(AssetType.EQUIPMENT);

        // Once v2 is effective, superseded v1 must not prevent the change.
        applicability.applyTo(schema, AssetType.FACTORY, Optional.empty(), FUTURE);
        assertThat(schema.appliesTo(AssetType.FACTORY)).isTrue();
    }

    @Test
    void completeCurrentVersionCannotHideIncompleteScheduledVersion() {
        InspectionSchema schema = schema("target", Set.of(AssetType.EQUIPMENT));
        publish(schema, true, NOW, NOW);
        publish(schema, false, NOW, FUTURE);

        assertThatThrownBy(() -> applicability.applyTo(schema, AssetType.FACTORY, Optional.empty(), NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("evaluates no criterion for");
        assertThat(schema.appliesTo(AssetType.FACTORY)).isFalse();

        schema.withdrawScheduledVersion(2, NOW, "Incomplete subsystem coverage");
        applicability.applyTo(schema, AssetType.FACTORY, Optional.empty(), NOW);
        assertThat(schema.appliesTo(AssetType.FACTORY)).isTrue();
    }

    @Test
    void transferRequiresAnEffectiveReplacementAndPreservesBothOwnersOnRejection() {
        InspectionSchema source = schema("source", Set.of(AssetType.EQUIPMENT, AssetType.FACTORY));
        InspectionSchema target = schema("target", Set.of(AssetType.LABORATORY));
        publish(target, true, NOW, FUTURE);

        assertThatThrownBy(() -> applicability.transfer(source, target, AssetType.FACTORY,
                Optional.of(source.id()), NOW)).isInstanceOf(DomainException.class)
                .hasMessageContaining("currently in effect");
        assertThat(source.appliesTo(AssetType.FACTORY)).isTrue();
        assertThat(target.appliesTo(AssetType.FACTORY)).isFalse();

        applicability.transfer(source, target, AssetType.FACTORY, Optional.of(source.id()), FUTURE);
        assertThat(source.appliesTo(AssetType.FACTORY)).isFalse();
        assertThat(target.appliesTo(AssetType.FACTORY)).isTrue();
    }

    private InspectionSchema schema(String id, Set<AssetType> types) {
        return applicability.create(SchemaId.of(id), id, types, Set.of());
    }

    private void publish(InspectionSchema schema, boolean coversParts, Instant at, Instant effectiveFrom) {
        schema.openDraft();
        if (!schema.requireDraft().sections().isEmpty()) schema.removeSection("Inspection");
        schema.addSection(coversParts ? DomainWorld.sectionCovering("Inspection", 1, AssetType.FACTORY)
                : Section.of("Inspection", 1, DomainWorld.temperatureCriterion()));
        assertThat(schema.publish(at, effectiveFrom).published()).isTrue();
    }
}
