package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalCertificateDerivation;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalCertificatePolicy;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalDerivationContext;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

/** Derivation is a current projection of the asset, independent of the issuance factory. */
public final class DeriveGlobalCertificate {
    private final AssetDirectory assets;
    private final CertificateRepository certificates;
    private final InspectionQuery inspections;
    private final GlobalCertificatePolicy policy;
    private final Clock clock;

    public DeriveGlobalCertificate(AssetDirectory assets, CertificateRepository certificates,
            InspectionQuery inspections, GlobalCertificatePolicy policy, Clock clock) {
        this.assets = assets;
        this.certificates = certificates;
        this.inspections = inspections;
        this.policy = policy;
        this.clock = clock;
    }

    public GlobalCertificateDerivation deriveFor(AssetId assetId) {
        var at = clock.now();
        var asset = assets.captureSnapshot(assetId);
        var partials = asset.subsystems().stream()
                .flatMap(part -> certificates.findLatestForAsset(assetId, CertificateScope.of(part)).stream()).toList();
        return policy.deriveFrom(new GlobalDerivationContext(asset, partials, at));
    }

    /** Existing inspection links resolve the current global state of their asset. */
    public GlobalCertificateDerivation deriveFor(InspectionId inspectionId) {
        return deriveFor(inspections.require(inspectionId).assetId());
    }
}
