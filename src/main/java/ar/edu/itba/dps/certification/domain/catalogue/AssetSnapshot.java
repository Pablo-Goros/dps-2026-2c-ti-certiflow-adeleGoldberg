package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Map;

public record AssetSnapshot(
        AssetId assetId,
        AssetType assetType,
        String name,
        Map<String, String> characteristics,
        String location,
        ResponsiblePartyRef responsible,
        Instant capturedAt) {

    public AssetSnapshot {
        Validate.required(assetId, "asset id");
        Validate.required(assetType, "asset type id");
        name = Validate.requiredText(name, "asset name");
        characteristics = Validate.requiredTextEntries(characteristics, "characteristic");
        location = Validate.requiredText(location, "location");
        Validate.required(responsible, "responsible");
        Validate.required(capturedAt, "capture instant");
    }
}
