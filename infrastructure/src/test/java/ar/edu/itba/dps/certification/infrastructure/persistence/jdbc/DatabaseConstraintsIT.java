package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.infrastructure.persistence.DuplicateKeyException;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import ar.edu.itba.dps.certification.support.CertificationScenarios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Business uniqueness rules that the database enforces on its own, whatever the application does. */
class DatabaseConstraintsIT {
    @Test
    void closingReleasesTheUniqueOpenInspectionKeyWithoutRemovingHistory() {
        var database = TestDatabase.create();
        var asset = AssetId.of("exclusive-asset");
        var first = new Inspection(InspectionId.of("first"), asset, PartyId.of("inspector"), LocalDate.of(2026, 3, 5));
        var next = new Inspection(InspectionId.of("next"), asset, PartyId.of("inspector"), LocalDate.of(2027, 3, 5));
        database.inspections().save(first);
        assertThatThrownBy(() -> database.inspections().save(next)).isInstanceOf(DuplicateKeyException.class);
        var closed = CertificationScenarios.approvedInspection(first.id(), asset,
                new SchemaVersionId(SchemaId.of("schema"), 1), Set.of(), Instant.parse("2026-03-05T10:00:00Z"));
        database.inspections().save(closed);
        database.inspections().save(next);
        assertThat(database.inspections().findAll()).hasSize(2);
        assertThat(database.inspections().findNonClosedByAsset(asset).orElseThrow().id()).isEqualTo(next.id());
    }


    private final StateCodec codec = new StateCodec();
    private final Scenario scenario = new Scenario();
    private JdbcPersistence database;

    @BeforeEach
    void setUp() {
        database = TestDatabase.create();
        scenario.system.schemas.findAll().forEach(database.schemas()::save);
        scenario.system.certificates.findAll().forEach(database.certificates()::save);
    }

    @Test
    @DisplayName("an asset type can be covered by only one schema")
    void aTypeBelongsToASingleSchema() {
        FullSystem rival = new FullSystem();
        rival.person("burns an identifier so the schema below gets its own");
        rival.person("and another one");
        InspectionSchema competing = rival.createSchema.create("Competing laboratory schema", Set.of(AssetType.LABORATORY));

        assertThatThrownBy(() -> database.schemas().save(competing)).isInstanceOf(DuplicateKeyException.class);

        assertThat(database.schemas().findByApplicableAssetType(AssetType.LABORATORY)).isPresent();
        assertThat(database.schemas().findAll()).hasSize(2);
    }

    @Test
    @DisplayName("transferring a type between schemas works in the order the use case saves them")
    void aTransferredTypeMovesToTheOtherSchema() {
        JdbcPersistence fresh = TestDatabase.create();
        FullSystem system = new FullSystem();
        InspectionSchema source = system.createSchema.create("Source", Set.of(AssetType.LABORATORY, AssetType.EQUIPMENT));
        InspectionSchema target = system.createSchema.create("Target", Set.of(AssetType.FACILITY));
        system.openDraft.open(target.id());
        system.editDraft.addSection(target.id(), Section.of("Safety", 1, DomainWorld.temperatureCriterion()));
        system.editDraft.addSection(target.id(), DomainWorld.sectionCovering("Parts", 2, AssetType.FACILITY));
        system.publishSchemaVersion.publish(target.id());
        fresh.schemas().save(source);
        fresh.schemas().save(target);

        system.schemaApplicability.transfer(source, target, AssetType.LABORATORY, Optional.of(source.id()), system.clock.now());
        fresh.schemas().save(source);
        fresh.schemas().save(target);

        assertThat(fresh.schemas().findByApplicableAssetType(AssetType.LABORATORY).orElseThrow().id())
                .isEqualTo(target.id());
        assertThat(fresh.schemas().findByApplicableAssetType(AssetType.EQUIPMENT).orElseThrow().id())
                .isEqualTo(source.id());
    }

    @Test
    @DisplayName("an inspection yields at most one certificate of each scope")
    void aCertificateScopeIsIssuedOncePerInspection() {
        Certificate stored = database.certificates().findAll().getFirst();
        String document = codec.write(stored);
        String sameInspectionAndScopeButAnotherId =
                document.replace("\"id\":{\"value\":\"" + stored.id().value() + "\"}", "\"id\":{\"value\":\"duplicate\"}");
        Certificate duplicate = codec.read(sameInspectionAndScopeButAnotherId, Certificate.class);
        assertThat(duplicate.id()).isNotEqualTo(stored.id());

        assertThatThrownBy(() -> database.certificates().save(duplicate)).isInstanceOf(DuplicateKeyException.class);

        assertThat(database.certificates().findAll()).hasSize(3);
    }

    @Test
    @DisplayName("a failed save leaves nothing behind when it was part of a larger unit of work")
    void aRejectedWriteRollsBackItsUnitOfWork() {
        Certificate stored = database.certificates().findAll().getFirst();
        Certificate duplicate = codec.read(codec.write(stored)
                .replace("\"id\":{\"value\":\"" + stored.id().value() + "\"}", "\"id\":{\"value\":\"duplicate\"}"),
                Certificate.class);

        assertThatThrownBy(() -> database.transactions().execute(() -> {
            database.parties().save(scenario.organization);
            database.certificates().save(duplicate);
        })).isInstanceOf(DuplicateKeyException.class);

        assertThat(database.parties().findAll()).isEmpty();
    }
}
