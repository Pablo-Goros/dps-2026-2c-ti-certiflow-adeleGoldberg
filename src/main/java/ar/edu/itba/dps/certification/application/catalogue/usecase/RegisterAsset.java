package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.application.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.Map;

public final class RegisterAsset {

    private final AssetRepository assets;
    private final PartyRepository parties;
    private final IdGenerator ids;
    private final AuditRecorder audit;

    public RegisterAsset(AssetRepository assets, PartyRepository parties, IdGenerator ids,
            AuditRecorder audit) {
        this.assets = assets;
        this.audit = audit;
        this.parties = parties;
        this.ids = ids;
    }

    public Asset register(String name, AssetType assetType, PartyId responsible, String location,
            Map<String, String> characteristics) {
        Party responsibleParty = parties.require(responsible);
        Asset asset = new Asset(new AssetId(ids.newIdentifier()), name, assetType, characteristics,
                responsibleParty.reference(), location);
        assets.save(asset);
        audit.record(AuditedElementRef.asset(asset.id().value()), AuditAction.ASSET_REGISTERED,
                AuditDetail.created(assetType + " '" + asset.name() + "' at " + asset.location()
                        + ", responsible " + asset.responsible()));
        return asset;
    }
}
