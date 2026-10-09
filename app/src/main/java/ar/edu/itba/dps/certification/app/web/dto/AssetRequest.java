package ar.edu.itba.dps.certification.app.web.dto;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;

import java.util.List;
import java.util.Map;

/** {@code subsystems} is optional: when omitted the asset declares all those of its type. */
public record AssetRequest(
        String name,
        AssetType assetType,
        String responsibleId,
        String location,
        Map<String, String> characteristics,
        String jurisdiction,
        List<String> subsystems) {
}
