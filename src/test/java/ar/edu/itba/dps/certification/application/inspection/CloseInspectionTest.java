package ar.edu.itba.dps.certification.application.inspection;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionClosureResult;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

class CloseInspectionTest {

    private DomainWorld world;
    private Party inspector;
    private Party originalResponsible;
    private Asset asset;
    private InspectionId inspectionId;

    @BeforeEach
    void setUp() {
        world = new DomainWorld();
        AssetType laboratory = AssetType.LABORATORY;
        world.publishLaboratorySchema(laboratory);
        inspector = world.person("Ana Perez");
        originalResponsible = world.organization("Favaloro Foundation");
        asset = world.asset("Laboratory A", laboratory, originalResponsible, "Building 1");
        inspectionId = world.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05"))
                .id();
        world.startInspection.start(inspectionId);
    }

    @Test
    @DisplayName("closing twice does not re-evaluate, does not duplicate findings and keeps the first closing instant")
    void closingTwiceIsIdempotent() {
        answerEverythingSatisfactorily();

        InspectionClosureResult first = world.closeInspection.close(inspectionId);
        Instant firstClosedAt = first.closedAt();
        world.clock.advance(Duration.ofHours(6));
        InspectionClosureResult second = world.closeInspection.close(inspectionId);

        assertThat(first.alreadyClosed()).isFalse();
        assertThat(second.alreadyClosed()).isTrue();
        assertThat(second.closedAt()).isEqualTo(firstClosedAt);
        assertThat(world.findings.closures).hasSize(1);
        assertThat(world.inspections.require(inspectionId).requireRecord(DomainWorld.TEMPERATURE)
                .evaluations()).hasSize(1);
    }

    @Test
    @DisplayName("an inspection that was never started cannot be closed")
    void cannotCloseAnInspectionThatNeverStarted() {
        Asset other = world.asset("Laboratory B", AssetType.LABORATORY,
                originalResponsible, "Building 2");
        InspectionId assignedOnly = world.assignInspection
                .assign(other.id(), inspector.id(), LocalDate.parse("2026-03-06"))
                .id();

        assertThatThrownBy(() -> world.closeInspection.close(assignedOnly))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("has not been started");
    }

    @Test
    @DisplayName("the act keeps the responsible frozen at start while the finding takes the one current at close")
    void theFrozenResponsibleAndTheResponsibleAtCloseAreDifferentValues() {
        Party newResponsible = world.organization("Merk Laboratories");
        world.changeAssetResponsible.change(asset.id(), newResponsible.id());
        answerWithARejection();

        world.closeInspection.close(inspectionId);

        Inspection inspection = world.inspections.require(inspectionId);
        assertThat(inspection.assetSnapshot().orElseThrow().responsible().partyId())
                .isEqualTo(originalResponsible.id());
        assertThat(world.findings.closures).singleElement()
                .satisfies(call -> assertThat(call.responsible()).isEqualTo(newResponsible.id()));
    }

    @Test
    @DisplayName("a later change of responsible does not reassign findings already created")
    void aLaterResponsibleChangeDoesNotReassignExistingFindings() {
        answerWithARejection();
        world.closeInspection.close(inspectionId);
        Party laterResponsible = world.organization("Third Party");

        world.changeAssetResponsible.change(asset.id(), laterResponsible.id());

        assertThat(world.findings.closures).singleElement()
                .satisfies(call -> assertThat(call.responsible()).isEqualTo(originalResponsible.id()));
    }

    @Test
    @DisplayName("only the criteria that are not approved reach the finding registry")
    void onlyNonApprovedCriteriaProduceFindings() {
        answerWithARejection();

        InspectionClosureResult result = world.closeInspection.close(inspectionId);

        assertThat(result.evaluations()).hasSize(2);
        assertThat(result.nonApproved()).containsOnlyKeys(DomainWorld.TEMPERATURE);
        assertThat(result.evaluations().get(DomainWorld.DOCUMENTATION).result())
                .isEqualTo(CriterionResult.APPROVED);
    }

    private void answerEverythingSatisfactorily() {
        world.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE, Measurement.of("5", "c"));
        world.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        world.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL,
                "file://manual.pdf");
    }

    private void answerWithARejection() {
        world.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE, Measurement.of("30", "c"));
        world.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        world.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL,
                "file://manual.pdf");
    }
}
