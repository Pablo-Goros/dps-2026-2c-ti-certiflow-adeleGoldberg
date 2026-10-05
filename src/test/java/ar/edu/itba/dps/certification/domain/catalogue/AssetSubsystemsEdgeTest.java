package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetSubsystemsEdgeTest {

    private static final Subsystem ELECTRICAL = Subsystem.of("electrical installation");
    private static final Subsystem PRESSURE = Subsystem.of("pressure system");
    private static final Subsystem UNKNOWN = Subsystem.of("fire protection");
    private static final ResponsiblePartyRef OWNER =
            new ResponsiblePartyRef(PartyId.of("owner-1"), "Owner");

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("a subsystem without a name is refused")
    void aSubsystemNeedsAName(String blank) {
        assertThatThrownBy(() -> Subsystem.of(blank))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("subsystem name");
    }

    @Test
    @DisplayName("a null subsystem name is refused")
    void aNullSubsystemNameIsRefused() {
        assertThatThrownBy(() -> Subsystem.of(null))
                .isInstanceOf(InvalidArgumentException.class);
    }

    @Test
    @DisplayName("a subsystem name is trimmed, so the same part is one value")
    void subsystemNamesAreTrimmed() {
        assertThat(Subsystem.of("  pressure system  ")).isEqualTo(PRESSURE);
    }

    @Test
    @DisplayName("an asset cannot declare a part its type does not define")
    void anUndefinedPartIsRefused() {
        assertThatThrownBy(() -> AssetType.FACILITY.validateSubsystems(Set.of(UNKNOWN)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must be predefined");
    }

    @Test
    @DisplayName("an asset of a type that is certified whole cannot declare parts")
    void aWholeOnlyTypeRefusesParts() {
        assertThatThrownBy(() -> AssetType.LABORATORY.validateSubsystems(Set.of(ELECTRICAL)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("certified as a whole");
    }

    @Test
    @DisplayName("declaring no part is allowed and means the asset is certified as a whole")
    void declaringNoPartIsAllowed() {
        assertThat(AssetType.FACILITY.validateSubsystems(Set.of())).isEmpty();
        assertThat(AssetType.LABORATORY.validateSubsystems(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("null parts are refused, which is not the same as declaring none")
    void nullPartsAreRefused() {
        assertThatThrownBy(() -> AssetType.FACILITY.validateSubsystems(null))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("asset subsystems");
    }

    @Test
    @DisplayName("declared parts come back in the order the type declares them")
    void declaredPartsKeepTheTypeOrder() {
        Set<Subsystem> reversed = new LinkedHashSet<>();
        reversed.add(PRESSURE);
        reversed.add(ELECTRICAL);

        assertThat(AssetType.FACILITY.validateSubsystems(reversed))
                .containsExactly(ELECTRICAL, PRESSURE);
    }

    @ParameterizedTest
    @EnumSource(AssetType.class)
    @DisplayName("every type agrees with itself about whether it divides into parts")
    void everyTypeIsConsistentAboutDividing(AssetType type) {
        assertThat(type.dividesIntoSubsystems()).isEqualTo(!type.subsystems().isEmpty());
        assertThat(type.validateSubsystems(type.subsystems())).isEqualTo(type.subsystems());
    }

    @Test
    @DisplayName("an asset refuses a part its type does not define")
    void anAssetRefusesAnUndefinedPart() {
        assertThatThrownBy(() -> new Asset(AssetId.of("asset-1"), "Central", AssetType.FACILITY,
                Map.of("room", "1"), OWNER, "B1", Set.of(UNKNOWN)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must be predefined");
    }

    @Test
    @DisplayName("an asset registered without naming its parts gets every part of its type")
    void anAssetWithoutDeclaredPartsGetsThemAll() {
        Asset asset = new Asset(AssetId.of("asset-1"), "Central", AssetType.FACILITY,
                Map.of("room", "1"), OWNER, "B1");

        assertThat(asset.subsystems()).isEqualTo(AssetType.FACILITY.subsystems());
    }

    @Test
    @DisplayName("the snapshot carries the parts the asset had when it was captured")
    void theSnapshotCarriesTheAssetParts() {
        Asset asset = new Asset(AssetId.of("asset-1"), "Central", AssetType.FACILITY,
                Map.of("room", "1"), OWNER, "B1", Set.of(ELECTRICAL));

        AssetSnapshot snapshot = asset.captureSnapshot(Instant.parse("2026-03-01T10:00:00Z"));

        assertThat(snapshot.subsystems()).containsExactly(ELECTRICAL);
    }

    @Test
    @DisplayName("a snapshot refuses parts the asset type does not define")
    void aSnapshotRefusesAnUndefinedPart() {
        assertThatThrownBy(() -> new AssetSnapshot(AssetId.of("asset-1"), AssetType.FACILITY,
                "Central", Map.of("room", "1"), "B1", OWNER, Set.of(UNKNOWN),
                Instant.parse("2026-03-01T10:00:00Z")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must be predefined");
    }
}
