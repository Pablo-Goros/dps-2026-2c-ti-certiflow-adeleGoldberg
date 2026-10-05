package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;

public final class CertificateIssuer {

    public Certificate issue(CertificateId id, AssetId assetId, InspectionId backingInspectionId,
            SchemaVersionId schemaVersionId, CertificateScope scope, ValidityPeriod validity,
            CertificateId previousCertificateId) {
        return new Certificate(id, assetId, backingInspectionId, schemaVersionId, scope, validity,
                previousCertificateId);
    }
}
