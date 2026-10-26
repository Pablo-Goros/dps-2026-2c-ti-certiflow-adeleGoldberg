package ar.edu.itba.dps.certification.domain.inspection;

import ar.edu.itba.dps.certification.domain.catalogue.*;
import ar.edu.itba.dps.certification.domain.inspection.rectification.*;
import ar.edu.itba.dps.certification.domain.schema.*;
import ar.edu.itba.dps.certification.domain.shared.*;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class InspectionBoundaryRegressionTest {
    private static final Instant AT = Instant.parse("2026-03-01T10:00:00Z");
    private static final PartyId INSPECTOR = PartyId.of("inspector");

    @Test
    void refusedReassignmentDoesNotPartiallyChangeTheInspector() {
        var inspection = assigned();
        assertThatThrownBy(() -> inspection.reassign(PartyId.of("other"), null))
                .isInstanceOf(InvalidArgumentException.class);
        assertThat(inspection.inspector()).isEqualTo(INSPECTOR);
        assertThat(inspection.expectedDate()).isEqualTo(LocalDate.of(2026, 3, 5));
    }

    @Test
    void anInapplicableCriterionCannotBeRectifiedOrPartiallyModifyTheInspection() {
        var inspection = assigned();
        var version = new SchemaVersion(new SchemaVersionId(SchemaId.of("schema"), 1),
                List.of(Section.of("Parts", 1, DomainWorld.electricalCriterion(), DomainWorld.pressureCriterion())), AT);
        var snapshot = new AssetSnapshot(inspection.assetId(), AssetType.FACTORY, "Factory", Map.of(),
                "Here", new ResponsiblePartyRef(PartyId.of("owner"), "Owner"),
                Set.of(DomainWorld.ELECTRICAL), AT, JurisdictionId.of("REFERENCE"));
        inspection.start(INSPECTOR, version, snapshot, AT);
        inspection.recordAnswer(INSPECTOR, DomainWorld.ELECTRICAL_WIRING, OptionAnswer.of("clean"));
        inspection.close(INSPECTOR, AT);

        assertThatThrownBy(() -> inspection.rectify(RectificationId.of("rectification"), INSPECTOR,
                AT, "Correct an absent subsystem", List.of(new Correction.AnswerCorrection(
                        DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean")))))
                .isInstanceOf(DomainException.class).hasMessageContaining("does not have");
        assertThat(inspection.requireRecord(DomainWorld.PRESSURE_VALVES).answer()).isEmpty();
        assertThat(inspection.rectifications()).isEmpty();
        assertThat(inspection.pendingEvents()).isEmpty();
    }

    private Inspection assigned() {
        return new Inspection(InspectionId.of("inspection"), AssetId.of("asset"), INSPECTOR,
                LocalDate.of(2026, 3, 5));
    }
}
