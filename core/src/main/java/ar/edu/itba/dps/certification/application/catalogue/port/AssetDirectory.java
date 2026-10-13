package ar.edu.itba.dps.certification.application.catalogue.port;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

public interface AssetDirectory {

    /** Must be called inside the assignment transaction before checking for an open inspection. */
    void lockForInspectionAssignment(AssetId assetId);

    JurisdictionId jurisdictionOf(AssetId assetId);

    AssetType assetTypeOf(AssetId assetId);

    PartyId currentResponsible(AssetId assetId);

    AssetSnapshot captureSnapshot(AssetId assetId);
}
