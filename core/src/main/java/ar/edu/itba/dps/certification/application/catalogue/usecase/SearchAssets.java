package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.List;
import java.util.Optional;

public final class SearchAssets implements ar.edu.itba.dps.certification.domain.catalogue.port.inbound.SearchAssets {
    private final Transactions transactions;

    private final AssetRepository assets;

    public SearchAssets(AssetRepository assets, Transactions transactions) {
        this.transactions = transactions;
        this.assets = assets;
    }

    public Optional<Asset> byId(AssetId assetId) {
        return transactions.execute(() -> {
            return assets.findById(assetId);
        });
    }

    public List<Asset> byType(AssetType assetType) {
        return transactions.execute(() -> {
            return assets.findByType(assetType);
        });
    }

    public List<Asset> byResponsible(PartyId responsible) {
        return transactions.execute(() -> {
            return assets.findByResponsible(responsible);
        });
    }

    public List<Asset> byNameContaining(String nameFragment) {
        return transactions.execute(() -> {
            return assets.findByNameContaining(nameFragment);
        });
    }

    public List<Asset> all() {
        return transactions.execute(() -> {
            return assets.findAll();
        });
    }
}
