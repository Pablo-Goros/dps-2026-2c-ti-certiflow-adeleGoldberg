package ar.edu.itba.dps.certification.application.schema.port;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;

import java.util.List;
import java.util.Optional;

public interface SchemaRepository {

    void save(InspectionSchema schema);

    Optional<InspectionSchema> findById(SchemaId id);

    default InspectionSchema require(SchemaId id) {
        return findById(id).orElseThrow(() -> new DomainException("schema " + id + " is not registered"));
    }

    Optional<InspectionSchema> findByApplicableAssetType(AssetType assetType);

    List<InspectionSchema> findAll();
}
