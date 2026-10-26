package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.domain.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.Map;
import java.util.Set;

public final class RegisterAsset implements ar.edu.itba.dps.certification.domain.catalogue.port.inbound.RegisterAsset {
    private final Transactions transactions;

    private final AssetRepository assets;
    private final PartyRepository parties;
    private final IdGenerator ids;
    private final AuditRecorder audit;

    public RegisterAsset(AssetRepository assets, PartyRepository parties, IdGenerator ids,
            AuditRecorder audit, Transactions transactions) {
        this.transactions = transactions;
        this.assets = assets;
        this.audit = audit;
        this.parties = parties;
        this.ids = ids;
    }

    public Asset register(String name, AssetType assetType, PartyId responsible, String location,
            Map<String, String> characteristics, JurisdictionId jurisdiction) {
        return transactions.execute(() -> {
            return register(name, assetType, responsible, location, characteristics,
                    assetType == null ? Set.of() : assetType.subsystems(), jurisdiction);
        });
    }

    public Asset register(String name, AssetType assetType, PartyId responsible, String location,
            Map<String, String> characteristics, Set<Subsystem> subsystems, JurisdictionId jurisdiction) {
        return transactions.execute(() -> {
            Party responsibleParty = parties.require(responsible);
            Asset asset = new Asset(new AssetId(ids.newIdentifier()), name, assetType, characteristics,
                    responsibleParty.reference(), location, subsystems, jurisdiction);
            assets.save(asset);
            audit.record(AuditedElementRef.asset(asset.id().value()), AuditAction.ASSET_REGISTERED,
                    AuditDetail.created(assetType + " '" + asset.name() + "' at " + asset.location()
                            + ", jurisdiction " + asset.jurisdiction()
                            + ", responsible " + asset.responsible()
                            + (asset.subsystems().isEmpty() ? ""
                                    : ", subsystems " + asset.subsystems())));
            return asset;
        });
    }
}
