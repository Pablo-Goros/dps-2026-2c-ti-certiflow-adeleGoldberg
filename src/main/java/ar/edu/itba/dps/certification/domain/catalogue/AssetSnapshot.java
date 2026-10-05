package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record AssetSnapshot(
        AssetId assetId,
        AssetType assetType,
        String name,
        Map<String, String> characteristics,
        String location,
        ResponsiblePartyRef responsible,
        Set<Subsystem> subsystems,
        Instant capturedAt, JurisdictionId jurisdiction) {

    public AssetSnapshot {
        Validate.required(jurisdiction, "jurisdiction");
        Validate.required(assetId, "asset id");
        Validate.required(assetType, "asset type id");
        name = Validate.requiredText(name, "asset name");
        characteristics = assetType.validateCharacteristics(characteristics);
        location = Validate.requiredText(location, "location");
        Validate.required(responsible, "responsible");
        subsystems = assetType.validateSubsystems(subsystems);
        Validate.required(capturedAt, "capture instant");
    }

    public AssetSnapshot(AssetId assetId, AssetType assetType, String name,
            Map<String, String> characteristics, String location, ResponsiblePartyRef responsible,
            Instant capturedAt, JurisdictionId jurisdiction) {
        this(assetId, assetType, name, characteristics, location, responsible,
                Validate.required(assetType, "asset type id").subsystems(), capturedAt, jurisdiction);
    }
}
