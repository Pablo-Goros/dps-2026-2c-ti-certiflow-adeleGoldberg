package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.application.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;
import java.util.Optional;

public final class SearchAssets {

    private final AssetRepository assets;

    public SearchAssets(AssetRepository assets) {
        this.assets = assets;
    }

    public Optional<Asset> byId(AssetId assetId) {
        return assets.findById(assetId);
    }

    public List<Asset> byType(AssetType assetType) {
        return assets.findByType(assetType);
    }

    public List<Asset> byResponsible(PartyId responsible) {
        return assets.findByResponsible(responsible);
    }

    public List<Asset> byNameContaining(String nameFragment) {
        return assets.findByNameContaining(nameFragment);
    }

    public List<Asset> all() {
        return assets.findAll();
    }
}
