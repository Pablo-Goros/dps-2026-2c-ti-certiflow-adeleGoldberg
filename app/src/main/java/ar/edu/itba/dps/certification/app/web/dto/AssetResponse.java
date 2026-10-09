package ar.edu.itba.dps.certification.app.web.dto;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;

import java.util.List;
import java.util.Map;

public record AssetResponse(
        String id,
        String name,
        AssetType assetType,
        Map<String, String> characteristics,
        String responsibleId,
        String responsibleName,
        String location,
        List<String> subsystems,
        String jurisdiction) {

    public static AssetResponse of(Asset asset) {
        return new AssetResponse(
                asset.id().value(),
                asset.name(),
                asset.assetType(),
                asset.characteristics(),
                asset.responsible().partyId().value(),
                asset.responsible().displayName(),
                asset.location(),
                asset.subsystems().stream().map(subsystem -> subsystem.name()).toList(),
                asset.jurisdiction().value());
    }
}
