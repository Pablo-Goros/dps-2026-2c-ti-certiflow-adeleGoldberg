package ar.edu.itba.dps.certification.domain.catalogue.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;
import java.util.Optional;

/** Input contract implemented by the application use case. */
public interface SearchAssets {
    Optional<Asset> byId(AssetId assetId);

    List<Asset> byType(AssetType assetType);

    List<Asset> byResponsible(PartyId responsible);

    List<Asset> byNameContaining(String nameFragment);

    List<Asset> all();
}
