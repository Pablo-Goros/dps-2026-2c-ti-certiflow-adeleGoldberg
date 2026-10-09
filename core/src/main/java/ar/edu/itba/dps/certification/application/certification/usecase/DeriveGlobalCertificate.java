package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.certification.CertificateFactory;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalCertificateDerivation;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

public final class DeriveGlobalCertificate {

    private final CertificateFactory factory;

    public DeriveGlobalCertificate(CertificateFactory factory) {
        this.factory = factory;
    }

    public GlobalCertificateDerivation deriveFor(InspectionId inspectionId) {
        return factory.deriveGlobal(inspectionId);
    }
}
