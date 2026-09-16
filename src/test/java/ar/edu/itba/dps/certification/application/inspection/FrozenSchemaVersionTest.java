package ar.edu.itba.dps.certification.application.inspection;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.PublicationResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.LocalDate;

class FrozenSchemaVersionTest {

    private DomainWorld world;
    private AssetType laboratory;
    private Party inspector;
    private Party responsible;

    @BeforeEach
    void setUp() {
        world = new DomainWorld();
        laboratory = AssetType.LABORATORY;
        inspector = world.person("Ana Perez");
        responsible = world.organization("Favaloro Foundation");
    }

    @Test
    @DisplayName("an inspection keeps the version published when it started, even after a newer one appears")
    void aNewerVersionDoesNotReachAnInspectionAlreadyStarted() {
        SchemaVersion first = world.publishLaboratorySchema(laboratory);
        Asset asset = world.asset("Laboratory A", laboratory, responsible, "Building 1");
        InspectionId inspectionId = world.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        world.startInspection.start(inspectionId);

        SchemaVersion second = publishASecondVersion();

        assertThat(second.number()).isEqualTo(2);
        Inspection inspection = world.inspections.require(inspectionId);
        assertThat(inspection.requireFrozenSchemaVersionId()).isEqualTo(first.id());
        assertThat(inspection.requireFrozenSchemaVersionId()).isNotEqualTo(second.id());
    }

    @Test
    @DisplayName("closing after a new version is published still evaluates with the frozen version")
    void closingUsesTheFrozenVersionAndNotTheLatest() {
        world.publishLaboratorySchema(laboratory);
        Asset asset = world.asset("Laboratory A", laboratory, responsible, "Building 1");
        InspectionId inspectionId = world.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        world.startInspection.start(inspectionId);
        world.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE, Measurement.of("20", "c"));
        world.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        world.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL,
                "file://manual.pdf");

        publishASecondVersion();
        var result = world.closeInspection.close(inspectionId);

        assertThat(result.evaluations().get(DomainWorld.TEMPERATURE).result())
                .isEqualTo(CriterionResult.OBSERVED);
        assertThat(result.evaluations().get(DomainWorld.TEMPERATURE).severity())
                .isEqualTo(Severity.LOW);
    }

    @Test
    @DisplayName("an inspection cannot start when the asset type has no published version")
    void cannotStartWithoutAPublishedVersion() {
        AssetType uncovered = AssetType.EQUIPMENT;
        Asset asset = world.asset("Boiler 1", uncovered, responsible, "Building 3");
        InspectionId inspectionId = world.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();

        assertThatThrownBy(() -> world.startInspection.start(inspectionId))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no published schema version");
    }

    @Test
    @DisplayName("an asset cannot have two inspections open at the same time")
    void onlyOneOpenInspectionPerAsset() {
        world.publishLaboratorySchema(laboratory);
        Asset asset = world.asset("Laboratory A", laboratory, responsible, "Building 1");
        world.assignInspection.assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05"));

        assertThatThrownBy(() -> world.assignInspection.assign(asset.id(), inspector.id(),
                LocalDate.parse("2026-03-09")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already has inspection");
    }

    private SchemaVersion publishASecondVersion() {
        InspectionSchema schema = world.schemas.findByApplicableAssetType(laboratory).orElseThrow();
        world.openDraft.open(schema.id());
        world.editDraft.removeSection(schema.id(), "Safety");
        world.editDraft.addSection(schema.id(),
                Section.of("Safety", 1, DomainWorld.temperatureCriterion(Severity.CRITICAL)));
        PublicationResult result = world.publishSchemaVersion.publish(schema.id());
        return result.publishedVersion();
    }
}
