package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryCertificateRepository implements CertificateRepository {

    private final Map<CertificateId, Certificate> stored = new LinkedHashMap<>();

    @Override
    public void save(Certificate certificate) {
        stored.put(certificate.id(), certificate);
    }

    @Override
    public Optional<Certificate> findById(CertificateId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public List<Certificate> findByBackingInspection(InspectionId inspectionId) {
        return stored.values().stream()
                .filter(certificate -> certificate.backingInspectionId().equals(inspectionId))
                .toList();
    }

    @Override
    public Optional<Certificate> findByBackingInspection(InspectionId inspectionId,
            CertificateScope scope) {
        return findByBackingInspection(inspectionId).stream()
                .filter(certificate -> certificate.covers(scope))
                .findFirst();
    }

    @Override
    public Optional<Certificate> findNonExpiredForAsset(AssetId assetId, CertificateScope scope) {
        return ofAsset(assetId, scope)
                .filter(certificate -> !certificate.status().expired())
                .findFirst();
    }

    @Override
    public Optional<Certificate> findLatestForAsset(AssetId assetId, CertificateScope scope) {
        return ofAsset(assetId, scope)
                .max(Comparator.comparing(certificate -> certificate.validity().issuedAt()));
    }

    @Override
    public List<Certificate> findDueForExpiry(Instant moment) {
        return stored.values().stream()
                .filter(certificate -> !certificate.status().expired())
                .filter(certificate -> certificate.validity().expiredAt(moment))
                .toList();
    }

    @Override
    public List<Certificate> findAll() {
        return new ArrayList<>(stored.values());
    }

    private java.util.stream.Stream<Certificate> ofAsset(AssetId assetId, CertificateScope scope) {
        return stored.values().stream()
                .filter(certificate -> certificate.assetId().equals(assetId))
                .filter(certificate -> certificate.covers(scope));
    }
}
