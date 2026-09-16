package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.application.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

public final class ChangeAssetResponsible {

    private final AssetRepository assets;
    private final PartyRepository parties;
    private final AuditRecorder audit;

    public ChangeAssetResponsible(AssetRepository assets, PartyRepository parties,
            AuditRecorder audit) {
        this.assets = assets;
        this.audit = audit;
        this.parties = parties;
    }

    public Asset change(AssetId assetId, PartyId newResponsible) {
        Asset asset = assets.require(assetId);
        Party party = parties.require(newResponsible);
        FieldChange change = asset.assignResponsible(party.reference());
        assets.save(asset);
        audit.record(AuditedElementRef.asset(asset.id().value()),
                AuditAction.ASSET_RESPONSIBLE_CHANGED, AuditDetail.dataChanged(change));
        return asset;
    }
}
