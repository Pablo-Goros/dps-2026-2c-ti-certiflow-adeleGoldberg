package ar.edu.itba.dps.certification.application.catalogue;

import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

public final class CatalogueAssetDirectory implements AssetDirectory {

    private final AssetRepository assets;
    private final Clock clock;

    public CatalogueAssetDirectory(AssetRepository assets, Clock clock) {
        this.assets = assets;
        this.clock = clock;
    }

    @Override
    public AssetType assetTypeOf(AssetId assetId) {
        return assets.require(assetId).assetType();
    }

    @Override
    public PartyId currentResponsible(AssetId assetId) {
        return assets.require(assetId).responsible().partyId();
    }

    @Override
    public AssetSnapshot captureSnapshot(AssetId assetId) {
        Asset asset = assets.require(assetId);
        return asset.captureSnapshot(clock.now());
    }
}
