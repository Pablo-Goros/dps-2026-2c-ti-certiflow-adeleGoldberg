package ar.edu.itba.dps.certification.domain.certification.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalCertificateDerivation;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

/** Input contract implemented by the application use case. */
public interface DeriveGlobalCertificate {
    GlobalCertificateDerivation deriveFor(AssetId assetId);

    GlobalCertificateDerivation deriveFor(InspectionId inspectionId);
}
