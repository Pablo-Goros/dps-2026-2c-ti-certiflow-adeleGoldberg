package ar.edu.itba.dps.certification.application.catalogue.port;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

public interface AssetDirectory {

        AssetType assetTypeOf(AssetId assetId);

        PartyId currentResponsible(AssetId assetId);

        AssetSnapshot captureSnapshot(AssetId assetId);
}
