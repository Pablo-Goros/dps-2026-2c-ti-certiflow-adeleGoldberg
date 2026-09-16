package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;

public final class RelocateAsset {

    private final AssetRepository assets;
    private final AuditRecorder audit;

    public RelocateAsset(AssetRepository assets, AuditRecorder audit) {
        this.assets = assets;
        this.audit = audit;
    }

    public Asset relocate(AssetId assetId, String newLocation) {
        Asset asset = assets.require(assetId);
        FieldChange change = asset.relocate(newLocation);
        assets.save(asset);
        audit.record(AuditedElementRef.asset(asset.id().value()), AuditAction.ASSET_RELOCATED,
                AuditDetail.dataChanged(change));
        return asset;
    }
}
