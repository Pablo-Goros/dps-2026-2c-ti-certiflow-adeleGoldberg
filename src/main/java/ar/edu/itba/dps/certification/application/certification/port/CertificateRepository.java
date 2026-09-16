package ar.edu.itba.dps.certification.application.certification.port;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository {

    void save(Certificate certificate);

    Optional<Certificate> findById(CertificateId id);

    default Certificate require(CertificateId id) {
        return findById(id)
                .orElseThrow(() -> new DomainException("certificate " + id + " does not exist"));
    }

    Optional<Certificate> findByBackingInspection(InspectionId inspectionId);

    Optional<Certificate> findNonExpiredForAsset(AssetId assetId);

    Optional<Certificate> findLatestForAsset(AssetId assetId);

    List<Certificate> findDueForExpiry(Instant moment);

    List<Certificate> findAll();
}
