package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.domain.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.domain.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcEventOutbox;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcTransactions;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opens the database (applying the migrations) and exposes each adapter under its port. */
@Configuration(proxyBeanMethods = false)
class PersistenceConfig {

    @Bean
    JdbcPersistence persistence(DataSource dataSource) {
        return JdbcPersistence.migrateAndOpen(dataSource);
    }

    @Bean
    JdbcTransactions transactions(JdbcPersistence persistence) {
        return persistence.transactions();
    }

    @Bean
    PartyRepository parties(JdbcPersistence persistence) {
        return persistence.parties();
    }

    @Bean
    AssetRepository assets(JdbcPersistence persistence) {
        return persistence.assets();
    }

    @Bean
    SchemaRepository schemas(JdbcPersistence persistence) {
        return persistence.schemas();
    }

    @Bean
    InspectionRepository inspections(JdbcPersistence persistence) {
        return persistence.inspections();
    }

    @Bean
    FindingRepository findings(JdbcPersistence persistence) {
        return persistence.findings();
    }

    @Bean
    CertificateRepository certificates(JdbcPersistence persistence) {
        return persistence.certificates();
    }

    @Bean
    AuditTrail auditTrail(JdbcPersistence persistence) {
        return persistence.auditTrail();
    }

    @Bean
    JdbcEventOutbox eventOutbox(JdbcPersistence persistence) {
        return persistence.eventOutbox();
    }
}
