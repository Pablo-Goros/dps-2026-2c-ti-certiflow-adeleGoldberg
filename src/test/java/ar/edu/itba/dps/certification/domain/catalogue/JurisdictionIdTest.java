package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class JurisdictionIdTest {
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t"})
    void invalidIdentifiers(String value) { assertThatThrownBy(() -> JurisdictionId.of(value)).isInstanceOf(InvalidArgumentException.class); }
    @Test void openIdentifierAndHistoricalSnapshot() {
        var jurisdiction = JurisdictionId.of("  new-jurisdiction  ");
        assertThat(jurisdiction).isEqualTo(JurisdictionId.of("new-jurisdiction"));
        var asset = new Asset(AssetId.of("asset"), "Lab", AssetType.LABORATORY, Map.of(),
                new ResponsiblePartyRef(PartyId.of("owner"), "owner"), "old", jurisdiction);
        var snapshot = asset.captureSnapshot(Instant.EPOCH);
        asset.relocate("new");
        assertThat(snapshot.jurisdiction()).isEqualTo(jurisdiction);
        assertThat(asset.jurisdiction()).isEqualTo(jurisdiction);
        assertThat(snapshot.location()).isEqualTo("old");
        assertThatThrownBy(() -> new Asset(asset.id(), "Lab", asset.assetType(), Map.of(), asset.responsible(), "here", null))
                .isInstanceOf(InvalidArgumentException.class);
    }
}
