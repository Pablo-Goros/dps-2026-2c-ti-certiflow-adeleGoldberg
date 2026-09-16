package ar.edu.itba.dps.certification.application.inspection.port;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;

import java.util.List;
import java.util.Optional;

public interface InspectionRepository extends InspectionQuery {

    void save(Inspection inspection);

    Optional<Inspection> findNonClosedByAsset(AssetId assetId);

    List<Inspection> findClosedByAsset(AssetId assetId);

    List<Inspection> findAll();
}
