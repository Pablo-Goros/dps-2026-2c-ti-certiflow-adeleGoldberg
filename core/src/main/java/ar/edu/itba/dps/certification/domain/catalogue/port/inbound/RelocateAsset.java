package ar.edu.itba.dps.certification.domain.catalogue.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;

/** Input contract implemented by the application use case. */
public interface RelocateAsset {
    Asset relocate(AssetId assetId, String newLocation);
}
