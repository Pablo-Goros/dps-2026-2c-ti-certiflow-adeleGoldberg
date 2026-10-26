package ar.edu.itba.dps.certification.domain.catalogue.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.Map;
import java.util.Set;

/** Input contract implemented by the application use case. */
public interface RegisterAsset {
    Asset register(String name, AssetType assetType, PartyId responsible, String location,
            Map<String, String> characteristics, JurisdictionId jurisdiction);

    Asset register(String name, AssetType assetType, PartyId responsible, String location,
            Map<String, String> characteristics, Set<Subsystem> subsystems, JurisdictionId jurisdiction);
}
