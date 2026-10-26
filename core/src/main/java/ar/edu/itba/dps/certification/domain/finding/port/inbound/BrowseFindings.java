package ar.edu.itba.dps.certification.domain.finding.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.List;
import java.util.Optional;

/** Input contract implemented by the application use case. */
public interface BrowseFindings {
    Optional<Finding> find(FindingId id);

    List<Finding> search(Optional<InspectionId> inspection, Optional<AssetId> asset,
            boolean openActionsOnly);
}
