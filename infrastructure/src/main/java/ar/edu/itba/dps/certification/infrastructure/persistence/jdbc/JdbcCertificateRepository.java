package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Certificates are indexed by asset, backing inspection, scope and expiry. A unique index on
 * (backing inspection, scope) guarantees at most one certificate of each scope per inspection even
 * when two requests try to issue it at the same time.
 *
 * <p>Instants are indexed as epoch milliseconds only to narrow the search; the exact comparison is
 * always made again on the rebuilt aggregates, so sub-millisecond precision never changes a result.
 */
public final class JdbcCertificateRepository extends DocumentRepository<Certificate> implements CertificateRepository {

    private static final String EXPIRED = CertificateStatus.EXPIRED.name();

    public JdbcCertificateRepository(JdbcTransactions db, StateCodec codec) {
        super(db, codec, Certificate.class, "certificate");
    }

    @Override
    public void save(Certificate certificate) {
        Map<String, Object> indexed = columns();
        indexed.put("asset_id", certificate.assetId().value());
        indexed.put("backing_inspection_id", certificate.backingInspectionId().value());
        indexed.put("scope_key", scopeKey(certificate.scope()));
        indexed.put("status", certificate.status().name());
        indexed.put("expires_at_ms", certificate.validity().expiresAt().toEpochMilli());
        store(certificate.id().value(), certificate, indexed);
    }

    @Override
    public Optional<Certificate> findById(CertificateId id) {
        return findByKey(id.value());
    }

    @Override
    public List<Certificate> findByBackingInspection(InspectionId inspectionId) {
        return findMany("WHERE t.backing_inspection_id = ?", inspectionId.value());
    }

    @Override
    public Optional<Certificate> findByBackingInspection(InspectionId inspectionId, CertificateScope scope) {
        return findOne("WHERE t.backing_inspection_id = ? AND t.scope_key = ?",
                inspectionId.value(), scopeKey(scope));
    }

    @Override
    public Optional<Certificate> findNonExpiredForAsset(AssetId assetId, CertificateScope scope) {
        return findOne("WHERE t.asset_id = ? AND t.scope_key = ? AND t.status <> ?",
                assetId.value(), scopeKey(scope), EXPIRED);
    }

    @Override
    public Optional<Certificate> findLatestForAsset(AssetId assetId, CertificateScope scope) {
        return findMany("WHERE t.asset_id = ? AND t.scope_key = ?", assetId.value(), scopeKey(scope)).stream()
                .max(Comparator.comparing(certificate -> certificate.validity().issuedAt()));
    }

    @Override
    public List<Certificate> findDueForExpiry(Instant moment) {
        return findMany("WHERE t.status <> ? AND t.expires_at_ms <= ?", EXPIRED, ceilingMillis(moment)).stream()
                .filter(certificate -> certificate.validity().expiredAt(moment))
                .toList();
    }

    @Override
    public List<Certificate> findAll() {
        return findMany("");
    }

    static String scopeKey(CertificateScope scope) {
        return scope.coveredSubsystem().map(subsystem -> "SUBSYSTEM:" + subsystem.name()).orElse("GLOBAL");
    }

    private static long ceilingMillis(Instant moment) {
        long millis = moment.toEpochMilli();
        return Instant.ofEpochMilli(millis).isBefore(moment) ? millis + 1 : millis;
    }
}
