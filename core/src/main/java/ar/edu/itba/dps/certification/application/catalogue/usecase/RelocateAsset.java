package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

public final class RelocateAsset implements ar.edu.itba.dps.certification.domain.catalogue.port.inbound.RelocateAsset {
    private final Transactions transactions;

    private final AssetRepository assets;
    private final AuditRecorder audit;

    public RelocateAsset(AssetRepository assets, AuditRecorder audit, Transactions transactions) {
        this.transactions = transactions;
        this.assets = assets;
        this.audit = audit;
    }

    public Asset relocate(AssetId assetId, String newLocation) {
        return transactions.execute(() -> {
            Asset asset = assets.require(assetId);
            var previous = asset.location();
            asset.relocate(newLocation);
            FieldChange change = FieldChange.of("location", previous, asset.location());
            assets.save(asset);
            audit.record(AuditedElementRef.asset(asset.id().value()), AuditAction.ASSET_RELOCATED,
                    AuditDetail.dataChanged(change));
            return asset;
        });
    }
}
