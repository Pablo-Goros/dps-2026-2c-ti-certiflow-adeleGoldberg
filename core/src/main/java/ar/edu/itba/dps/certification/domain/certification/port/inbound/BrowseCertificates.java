package ar.edu.itba.dps.certification.domain.certification.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.List;
import java.util.Optional;

/** Input contract implemented by the application use case. */
public interface BrowseCertificates {
    Optional<Certificate> find(CertificateId id);

    List<Certificate> search(Optional<AssetId> asset, Optional<InspectionId> backingInspection,
            Optional<CertificateStatus> status);
}
