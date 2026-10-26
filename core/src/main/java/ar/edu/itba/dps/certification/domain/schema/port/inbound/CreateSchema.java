package ar.edu.itba.dps.certification.domain.schema.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;

import java.util.Set;

/** Input contract implemented by the application use case. */
public interface CreateSchema {
    InspectionSchema create(String name, Set<AssetType> applicableAssetTypes);
}
