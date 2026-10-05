package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.adapter.schema.PublishedSchemaCatalog;
import ar.edu.itba.dps.certification.application.catalogue.usecase.SearchAssets;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionSummary;
import ar.edu.itba.dps.certification.application.inspection.usecase.ReassignInspection;
import ar.edu.itba.dps.certification.application.schema.usecase.ChangeSchemaApplicability;
import ar.edu.itba.dps.certification.application.schema.usecase.DiscardDraft;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationUseCasesCoverageTest {

    private FullSystem system;
    private Party inspector;
    private Party responsible;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        inspector = system.person("Ana Perez");
        responsible = system.organization("Favaloro Foundation");
        system.actAs(inspector);
    }

    @Test
    @DisplayName("assets can be searched by every catalogue criterion")
    void assetsCanBeSearchedByEveryCatalogueCriterion() {
        Asset laboratory = system.asset("Laboratory A", AssetType.LABORATORY, responsible);
        Party otherResponsible = system.organization("Merk Laboratories");
        Asset equipment = system.registerAsset.register("Cooling Unit", AssetType.EQUIPMENT,
                otherResponsible.id(), "Building 2",
                Map.of("room", "12", "brand", "Acme", "model", "X1", "serialNumber", "SN-1"));
        SearchAssets search = new SearchAssets(system.catalogue.assets);

        assertThat(search.byId(laboratory.id())).contains(laboratory);
        assertThat(search.byId(AssetId.of("missing"))).isEmpty();
        assertThat(search.byType(AssetType.EQUIPMENT)).containsExactly(equipment);
        assertThat(search.byResponsible(responsible.id())).containsExactly(laboratory);
        assertThat(search.byNameContaining("Cooling")).containsExactly(equipment);
        assertThat(search.all()).containsExactly(laboratory, equipment);
    }

    @Test
    @DisplayName("an assigned inspection can be reassigned before it starts")
    void anAssignedInspectionCanBeReassignedBeforeItStarts() {
        system.publishLaboratorySchema(AssetType.LABORATORY);
        Asset asset = system.asset("Laboratory A", AssetType.LABORATORY, responsible);
        Inspection inspection = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05"));
        Party replacementInspector = system.person("Laura Gomez");
        ReassignInspection reassign = new ReassignInspection(system.inspections,
                system.catalogue.parties, system.audit);

        Inspection reassigned = reassign.reassign(inspection.id(), replacementInspector.id(),
                LocalDate.parse("2026-03-07"));

        assertThat(reassigned.inspector()).isEqualTo(replacementInspector.id());
        assertThat(reassigned.expectedDate()).isEqualTo(LocalDate.parse("2026-03-07"));
        assertThat(system.auditTrail.all()).anySatisfy(entry ->
                assertThat(entry.action().name()).isEqualTo("INSPECTION_REASSIGNED"));
    }

    @Test
    @DisplayName("a started inspection cannot be reassigned")
    void aStartedInspectionCannotBeReassigned() {
        system.publishLaboratorySchema(AssetType.LABORATORY);
        Asset asset = system.asset("Laboratory A", AssetType.LABORATORY, responsible);
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        Party replacementInspector = system.person("Laura Gomez");
        ReassignInspection reassign = new ReassignInspection(system.inspections,
                system.catalogue.parties, system.audit);

        assertThatThrownBy(() -> reassign.reassign(inspectionId, replacementInspector.id(),
                LocalDate.parse("2026-03-07")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("while it is IN_PROGRESS");
    }

    @Test
    @DisplayName("inspection summaries expose assigned, started and closed milestones")
    void inspectionSummariesExposeMilestones() {
        system.publishLaboratorySchema(AssetType.LABORATORY);
        Asset asset = system.asset("Laboratory A", AssetType.LABORATORY, responsible);
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();

        InspectionSummary assigned = system.inspections.summaryOf(inspectionId);
        assertThat(assigned.closed()).isFalse();
        assertThatThrownBy(assigned::requireSchemaVersionId)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("has not been started");

        system.startInspection.start(inspectionId);
        InspectionSummary started = InspectionSummary.of(system.inspections.require(inspectionId));
        assertThat(started.schemaVersionId()).isPresent();
        assertThat(started.asset()).isPresent();

        system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE,
                ar.edu.itba.dps.certification.domain.shared.answer.Measurement.of("5", "c"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION,
                ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(inspectionId);

        InspectionSummary closed = system.inspections.summaryOf(inspectionId);
        assertThat(closed.closed()).isTrue();
        assertThat(closed.requireSchemaVersionId()).isEqualTo(started.requireSchemaVersionId());
    }

    @Test
    @DisplayName("schema applicability can be transferred and drafts can be discarded")
    void schemaApplicabilityCanBeTransferredAndDraftsCanBeDiscarded() {
        ChangeSchemaApplicability change = new ChangeSchemaApplicability(system.schemas,
                system.schemaApplicability, system.audit);
        DiscardDraft discardDraft = new DiscardDraft(system.schemas, system.audit);

        InspectionSchema source = system.createSchema.create("Shared inspection",
                Set.of(AssetType.LABORATORY, AssetType.FACTORY));
        system.openDraft.open(source.id());
        system.editDraft.addSection(source.id(), Section.of("Safety", 1,
                DomainWorld.temperatureCriterion()));
        system.editDraft.addSection(source.id(),
                DomainWorld.sectionCovering("Parts", 2, AssetType.FACTORY));
        assertThat(system.publishSchemaVersion.publish(source.id()).published()).isTrue();

        InspectionSchema target = system.createSchema.create("Facility inspection",
                Set.of(AssetType.FACILITY));
        system.openDraft.open(target.id());
        system.editDraft.addSection(target.id(), Section.of("Safety", 1,
                DomainWorld.documentationCriterion()));
        system.editDraft.addSection(target.id(),
                DomainWorld.sectionCovering("Parts", 2, AssetType.FACILITY));
        assertThat(system.publishSchemaVersion.publish(target.id()).published()).isTrue();

        InspectionSchema updatedTarget = change.transferTo(source.id(), target.id(), AssetType.FACTORY);

        assertThat(system.schemas.require(source.id()).appliesTo(AssetType.FACTORY)).isFalse();
        assertThat(updatedTarget.appliesTo(AssetType.FACTORY)).isTrue();

        assertThatThrownBy(() -> change.stopApplyingTo(target.id(), AssetType.FACILITY))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("transfer it instead");

        system.openDraft.open(target.id());
        assertThat(system.schemas.require(target.id()).draft()).isPresent();
        discardDraft.discard(target.id());
        assertThat(system.schemas.require(target.id()).draft()).isEmpty();
    }

    @Test
    @DisplayName("the published schema catalog rejects an unknown schema version")
    void schemaCatalogRejectsUnknownVersions() {
        system.publishLaboratorySchema(AssetType.LABORATORY);
        PublishedSchemaCatalog catalog = new PublishedSchemaCatalog(system.schemas);

        assertThatThrownBy(() -> catalog.requireVersion(
                new SchemaVersionId(SchemaId.of("missing-schema"), 1)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("schema missing-schema is not registered");

        SchemaId schemaId = system.schemas.findByApplicableAssetType(AssetType.LABORATORY)
                .orElseThrow().id();
        assertThatThrownBy(() -> catalog.requireVersion(new SchemaVersionId(schemaId, 99)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("repository default require methods report absent entities")
    void repositoryRequireMethodsReportAbsentEntities() {
        assertThatThrownBy(() -> system.catalogue.assets.require(AssetId.of("asset-missing")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("asset asset-missing is not registered");
        assertThatThrownBy(() -> system.catalogue.parties.require(PartyId.of("party-missing")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("party party-missing is not registered");
        assertThatThrownBy(() -> system.inspections.require(InspectionId.of("inspection-missing")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("inspection inspection-missing does not exist");
    }
}
