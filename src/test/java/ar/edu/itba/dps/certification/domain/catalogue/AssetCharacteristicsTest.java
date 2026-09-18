package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetCharacteristicsTest {

    private static final ResponsiblePartyRef OWNER =
            new ResponsiblePartyRef(PartyId.of("party-1"), "Favaloro Foundation");

    @Test
    @DisplayName("a characteristic without a name is not a characteristic")
    void aCharacteristicNeedsAName() {
        assertThatThrownBy(() -> asset(Map.of("   ", "12")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("characteristic name");
    }

    @Test
    @DisplayName("a characteristic without a value is not a characteristic")
    void aCharacteristicNeedsAValue() {
        assertThatThrownBy(() -> asset(Map.of("room", "")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("value of characteristic 'room'");
    }

    @Test
    @DisplayName("the snapshot applies the same rule as the asset it freezes")
    void theSnapshotAppliesTheSameRule() {
        assertThatThrownBy(() -> new AssetSnapshot(AssetId.of("asset-1"), AssetType.LABORATORY,
                "Laboratory A", Map.of("room", "  "), "Building 1", OWNER,
                Instant.parse("2026-03-01T10:00:00Z")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("value of characteristic 'room'");
    }

    @Test
    @DisplayName("an asset with no characteristics at all is accepted")
    void anAssetWithoutCharacteristicsIsAccepted() {
        assertThat(asset(Map.of()).characteristics()).isEmpty();
    }

    private Asset asset(Map<String, String> characteristics) {
        return new Asset(AssetId.of("asset-1"), "Laboratory A", AssetType.LABORATORY,
                characteristics, OWNER, "Building 1");
    }
}
