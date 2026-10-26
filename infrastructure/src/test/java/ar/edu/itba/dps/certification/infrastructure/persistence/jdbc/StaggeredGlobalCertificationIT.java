package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.adapter.catalogue.CatalogueAssetDirectory;
import ar.edu.itba.dps.certification.application.certification.usecase.DeriveGlobalCertificate;
import ar.edu.itba.dps.certification.domain.catalogue.*;
import ar.edu.itba.dps.certification.domain.certification.*;
import ar.edu.itba.dps.certification.domain.certification.derivation.AllSubsystemsMustBeInForce;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.*;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import ar.edu.itba.dps.certification.support.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StaggeredGlobalCertificationIT {
    @Test void latestPartialsFromDifferentInspectionsAndSchemasAreCombinedAfterDatabaseReload() {
        var source = TestDatabase.dataSource();
        var db = JdbcPersistence.open(source);
        var clock = TestClock.at("2026-03-01T10:00:00Z");
        var asset = new Asset(AssetId.of("asset"), "Facility", AssetType.FACILITY, Map.of(),
                new ResponsiblePartyRef(PartyId.of("owner"), "Owner"), "Here",
                Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE), TestPolicies.REFERENCE);
        db.assets().save(asset);
        var first = CertificationScenarios.approvedInspection(InspectionId.of("first"), asset.id(),
                new SchemaVersionId(SchemaId.of("schema"), 1), asset.subsystems(), clock.now());
        var old = CertificationScenarios.issue(CertificateId.of("old"), first, CertificateScope.of(DomainWorld.ELECTRICAL),
                TestPolicies.reference(), clock.now(), null);
        var pressure = CertificationScenarios.issue(CertificateId.of("pressure"), first, CertificateScope.of(DomainWorld.PRESSURE),
                TestPolicies.reference(), clock.now().plus(Duration.ofDays(30)), null);
        clock.advanceDays(370);
        old.expireIfDue(clock.now());
        var second = CertificationScenarios.approvedInspection(InspectionId.of("second"), asset.id(),
                new SchemaVersionId(SchemaId.of("schema"), 2), asset.subsystems(), clock.now());
        var renewed = CertificationScenarios.issue(CertificateId.of("renewed"), second, CertificateScope.of(DomainWorld.ELECTRICAL),
                TestPolicies.reference(), clock.now(), old);
        db.certificates().save(old);
        db.certificates().save(pressure);
        db.certificates().save(renewed);
        db.inspections().save(first);
        db.inspections().save(second);

        var reopened = JdbcPersistence.open(source);
        var derive = new DeriveGlobalCertificate(new CatalogueAssetDirectory(reopened.assets(), clock),
                reopened.certificates(), reopened.inspections(), new AllSubsystemsMustBeInForce(), clock, reopened.transactions());
        var global = derive.deriveFor(asset.id()).derivedCertificate().orElseThrow();
        assertThat(global.derivedFrom()).containsExactly(renewed.id(), pressure.id());
        assertThat(global.provenance()).extracting(p -> p.backingInspectionId()).containsExactly(second.id(), first.id());
        assertThat(global.validity().expiresAt()).isEqualTo(pressure.validity().expiresAt());
        assertThat(derive.deriveFor(first.id()).derivedCertificate()).contains(global);
        assertThat(derive.deriveFor(second.id()).derivedCertificate()).contains(global);
    }
}
