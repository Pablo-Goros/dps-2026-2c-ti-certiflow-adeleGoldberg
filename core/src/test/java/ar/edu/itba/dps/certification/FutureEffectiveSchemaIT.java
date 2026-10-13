package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.application.schema.usecase.ChangeSchemaApplicability;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FutureEffectiveSchemaIT {

    private FullSystem system;
    private Party owner;
    private Party inspector;

    @BeforeEach
    void setup() {
        system = new FullSystem();
        owner = system.person("Owner");
        inspector = system.person("Inspector");
        system.actAs(inspector);
    }

    @Test
    @DisplayName("F2: future effective schema version is selected only when clock reaches effective date, freezing previous version for earlier inspections")
    void futureEffectiveSchemaVersionLifecycle() {
        // 1. Create schema and publish Version 1 with immediate effect (at T0)
        InspectionSchema schema = system.createSchema.create("Lab Schema", Set.of(AssetType.LABORATORY));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Safety V1", 1, DomainWorld.documentationCriterion()));
        SchemaVersion v1 = system.publishSchemaVersion.publish(schema.id()).publishedVersion();
        Instant t0 = system.clock.now();

        // 2. Open a draft and publish Version 2 with effectiveFrom 10 days in the future
        Instant t10DaysFuture = t0.plus(Duration.ofDays(10));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Temperature Section", 2, DomainWorld.temperatureCriterion()));
        SchemaVersion v2 = system.publishSchemaVersion.publish(schema.id(), t10DaysFuture).publishedVersion();

        assertThat(v1.number()).isEqualTo(1);
        assertThat(v2.number()).isEqualTo(2);

        // 3. Register assets of type LABORATORY
        Asset asset1 = system.registerAsset.register("Lab Alpha", AssetType.LABORATORY, owner.id(), "Building A", Map.of(), JurisdictionId.of("NATIONAL"));
        Asset asset2 = system.registerAsset.register("Lab Beta", AssetType.LABORATORY, owner.id(), "Building B", Map.of(), JurisdictionId.of("NATIONAL"));
        Asset asset3 = system.registerAsset.register("Lab Gamma", AssetType.LABORATORY, owner.id(), "Building C", Map.of(), JurisdictionId.of("NATIONAL"));

        // 4. Start Inspection 1 at T0 -> Must pick Version 1
        var assignment1 = system.assignInspection.assign(asset1.id(), inspector.id(), system.clock.today());
        Inspection insp1 = system.startInspection.start(assignment1.id());
        assertThat(insp1.requireFrozenSchemaVersionId().number()).isEqualTo(1);

        // 5. Advance clock 5 days (T0 + 5 days) -> Must still pick Version 1
        system.clock.advance(Duration.ofDays(5));
        var assignment2 = system.assignInspection.assign(asset2.id(), inspector.id(), system.clock.today());
        Inspection insp2 = system.startInspection.start(assignment2.id());
        assertThat(insp2.requireFrozenSchemaVersionId().number()).isEqualTo(1);

        // 6. Advance clock 6 more days (T0 + 11 days, past effective date) -> Must pick Version 2
        system.clock.advance(Duration.ofDays(6));
        var assignment3 = system.assignInspection.assign(asset3.id(), inspector.id(), system.clock.today());
        Inspection insp3 = system.startInspection.start(assignment3.id());
        assertThat(insp3.requireFrozenSchemaVersionId().number()).isEqualTo(2);

        // 7. Verify immutability of previously started inspections
        assertThat(insp1.requireFrozenSchemaVersionId().number()).isEqualTo(1);
        assertThat(insp2.requireFrozenSchemaVersionId().number()).isEqualTo(1);
        assertThat(insp3.requireFrozenSchemaVersionId().number()).isEqualTo(2);

        // 8. Verify querying effective version for dates in the past and future via SchemaCatalog
        assertThat(system.schemaCatalog.effectiveVersionFor(AssetType.LABORATORY, t0))
                .map(SchemaVersion::number).contains(1);
        assertThat(system.schemaCatalog.effectiveVersionFor(AssetType.LABORATORY, t0.plus(Duration.ofDays(5))))
                .map(SchemaVersion::number).contains(1);
        assertThat(system.schemaCatalog.effectiveVersionFor(AssetType.LABORATORY, t10DaysFuture))
                .map(SchemaVersion::number).contains(2);
    }

    @Test
    @DisplayName("new applicability requires every published version, including the currently effective one, to cover its parts")
    void addingApplicabilityDoesNotExposeAnUncoveredEarlierVersion() {
        InspectionSchema schema = system.createSchema.create("Generic schema", Set.of(AssetType.EQUIPMENT));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Common", 1, DomainWorld.documentationCriterion()));
        SchemaVersion v1 = system.publishSchemaVersion.publish(schema.id()).publishedVersion();

        Instant future = system.clock.now().plus(Duration.ofDays(10));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Electrical", 2, DomainWorld.electricalCriterion()));
        system.editDraft.addSection(schema.id(), Section.of("Pressure", 3, DomainWorld.pressureCriterion()));
        SchemaVersion v2 = system.publishSchemaVersion.publish(schema.id(), future).publishedVersion();

        assertThat(v1.declaredSubsystems()).isEmpty();
        assertThat(v2.declaredSubsystems()).containsExactlyInAnyOrderElementsOf(AssetType.FACTORY.subsystems());
        var change = new ChangeSchemaApplicability(system.schemas, system.schemaApplicability, system.audit);

        assertThatThrownBy(() -> change.applyTo(schema.id(), AssetType.FACTORY))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("version 1")
                .hasMessageContaining("electrical installation")
                .hasMessageContaining("pressure system");
        assertThat(system.schemas.require(schema.id()).applicableAssetTypes()).containsExactly(AssetType.EQUIPMENT);
    }

    @Test
    @DisplayName("starting an inspection refuses a frozen schema that does not evaluate every present subsystem")
    void startingWithAnUndercoveredFrozenVersionIsRefusedBeforeMutation() {
        InspectionSchema schema = system.createSchema.create("Generic schema", Set.of(AssetType.EQUIPMENT));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Common", 1, DomainWorld.documentationCriterion()));
        SchemaVersion undercovered = system.publishSchemaVersion.publish(schema.id()).publishedVersion();
        Asset factory = system.asset("Factory", AssetType.FACTORY, owner);
        Inspection inspection = new Inspection(InspectionId.of("undercovered-inspection"), factory.id(),
                inspector.id(), system.clock.today());

        assertThatThrownBy(() -> inspection.start(inspector.id(), undercovered,
                factory.captureSnapshot(system.clock.now()), system.clock.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not evaluate")
                .hasMessageContaining("electrical installation");
        assertThat(inspection.status().name()).isEqualTo("ASSIGNED");
        assertThat(inspection.frozenSchemaVersionId()).isEmpty();
    }

    @Test
    @DisplayName("transferring applicability also protects earlier versions of the replacement schema")
    void applicabilityTransferDoesNotExposeAnUncoveredEarlierVersion() {
        InspectionSchema source = system.createSchema.create("Factory source",
                Set.of(AssetType.FACTORY, AssetType.LABORATORY));
        system.openDraft.open(source.id());
        system.editDraft.addSection(source.id(), Section.of("Electrical", 1, DomainWorld.electricalCriterion()));
        system.editDraft.addSection(source.id(), Section.of("Pressure", 2, DomainWorld.pressureCriterion()));
        system.publishSchemaVersion.publish(source.id());

        InspectionSchema target = system.createSchema.create("Equipment target", Set.of(AssetType.EQUIPMENT));
        system.openDraft.open(target.id());
        system.editDraft.addSection(target.id(), Section.of("Common", 1, DomainWorld.documentationCriterion()));
        system.publishSchemaVersion.publish(target.id());
        system.openDraft.open(target.id());
        system.editDraft.addSection(target.id(), Section.of("Electrical", 2, DomainWorld.electricalCriterion()));
        system.editDraft.addSection(target.id(), Section.of("Pressure", 3, DomainWorld.pressureCriterion()));
        system.publishSchemaVersion.publish(target.id(), system.clock.now().plus(Duration.ofDays(10)));
        var change = new ChangeSchemaApplicability(system.schemas, system.schemaApplicability, system.audit);

        assertThatThrownBy(() -> change.transferTo(source.id(), target.id(), AssetType.FACTORY))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("version 1")
                .hasMessageContaining("electrical installation")
                .hasMessageContaining("pressure system");
        assertThat(system.schemas.require(source.id()).applicableAssetTypes())
                .containsExactlyInAnyOrder(AssetType.FACTORY, AssetType.LABORATORY);
        assertThat(system.schemas.require(target.id()).applicableAssetTypes()).containsExactly(AssetType.EQUIPMENT);
    }
}
