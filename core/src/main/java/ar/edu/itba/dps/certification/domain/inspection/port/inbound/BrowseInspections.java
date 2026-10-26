package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;
import java.util.Optional;

/** Input contract implemented by the application use case. */
public interface BrowseInspections {
    Optional<Inspection> find(InspectionId id);

    List<Inspection> search(Optional<AssetId> asset, Optional<PartyId> inspector,
            Optional<InspectionStatus> status);

    Optional<SchemaVersion> frozenVersion(Inspection inspection);
}
