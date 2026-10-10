package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.List;
import java.util.Optional;

/** Read side of the certificates. */
public final class BrowseCertificates {

    private final CertificateRepository certificates;

    public BrowseCertificates(CertificateRepository certificates) {
        this.certificates = certificates;
    }

    public Optional<Certificate> find(CertificateId id) {
        return certificates.findById(id);
    }

    /** Every filter is optional; the ones given must all match. */
    public List<Certificate> search(Optional<AssetId> asset, Optional<InspectionId> backingInspection,
            Optional<CertificateStatus> status) {
        List<Certificate> found = backingInspection.isPresent()
                ? certificates.findByBackingInspection(backingInspection.get())
                : certificates.findAll();
        return found.stream()
                .filter(certificate -> asset.map(certificate.assetId()::equals).orElse(true))
                .filter(certificate -> status.map(wanted -> certificate.status() == wanted).orElse(true))
                .toList();
    }
}
