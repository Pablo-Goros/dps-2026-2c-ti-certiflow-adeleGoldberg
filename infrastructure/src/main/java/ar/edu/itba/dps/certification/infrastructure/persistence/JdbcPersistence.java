package ar.edu.itba.dps.certification.infrastructure.persistence;

import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcAssetRepository;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcAuditTrail;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcCertificateRepository;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcFindingRepository;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcInspectionRepository;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcPartyRepository;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcSchemaRepository;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcTransactions;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * Composition point of the persistence adapters: given a database it creates or upgrades the
 * schema and exposes every port implementation sharing one transaction manager, so a unit of work
 * wrapped with {@link #transactions()} spans all of them.
 */
public final class JdbcPersistence {

    private final JdbcTransactions transactions;
    private final JdbcPartyRepository parties;
    private final JdbcAssetRepository assets;
    private final JdbcSchemaRepository schemas;
    private final JdbcInspectionRepository inspections;
    private final JdbcFindingRepository findings;
    private final JdbcCertificateRepository certificates;
    private final JdbcAuditTrail auditTrail;

    private JdbcPersistence(DataSource dataSource) {
        StateCodec codec = new StateCodec();
        this.transactions = new JdbcTransactions(dataSource);
        this.parties = new JdbcPartyRepository(transactions, codec);
        this.assets = new JdbcAssetRepository(transactions, codec);
        this.schemas = new JdbcSchemaRepository(transactions, codec);
        this.inspections = new JdbcInspectionRepository(transactions, codec);
        this.findings = new JdbcFindingRepository(transactions, codec);
        this.certificates = new JdbcCertificateRepository(transactions, codec);
        this.auditTrail = new JdbcAuditTrail(transactions, codec);
    }

    /** Applies the pending migrations and returns the adapters over {@code dataSource}. */
    public static JdbcPersistence migrateAndOpen(DataSource dataSource) {
        migrate(dataSource);
        return open(dataSource);
    }

    /** Returns the adapters over a database whose schema is already in place. */
    public static JdbcPersistence open(DataSource dataSource) {
        return new JdbcPersistence(dataSource);
    }

    public static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public JdbcTransactions transactions() {
        return transactions;
    }

    public JdbcPartyRepository parties() {
        return parties;
    }

    public JdbcAssetRepository assets() {
        return assets;
    }

    public JdbcSchemaRepository schemas() {
        return schemas;
    }

    public JdbcInspectionRepository inspections() {
        return inspections;
    }

    public JdbcFindingRepository findings() {
        return findings;
    }

    public JdbcCertificateRepository certificates() {
        return certificates;
    }

    public JdbcAuditTrail auditTrail() {
        return auditTrail;
    }
}
