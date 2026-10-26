package ar.edu.itba.dps.certification.domain.catalogue.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

/** Input contract implemented by the application use case. */
public interface ChangeAssetResponsible {
    Asset change(AssetId assetId, PartyId newResponsible);
}
