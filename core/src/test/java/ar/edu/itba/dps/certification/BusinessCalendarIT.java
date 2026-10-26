package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.support.FullSystem;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BusinessCalendarIT {
    @Test void morningAndLateEveningClosuresOnTheSameBusinessDayHaveTheSamePlanningDeadline() {
        var system = new FullSystem();
        system.clock.useZone(ZoneId.of("America/Argentina/Buenos_Aires"));
        system.publishLaboratorySchema(AssetType.LABORATORY);
        var inspector = system.person("Inspector");
        var owner = system.organization("Owner");
        system.actAs(inspector);
        for (String instant : new String[]{"2026-03-05T13:00:00Z", "2026-03-06T01:30:00Z"}) {
            system.clock.advance(Duration.between(system.clock.now(), Instant.parse(instant)));
            assertThat(system.clock.today()).isEqualTo(LocalDate.parse("2026-03-05"));
            var asset = system.asset("Lab " + instant, AssetType.LABORATORY, owner);
            var inspection = system.assignInspection.assign(asset.id(), inspector.id(), system.clock.today());
            system.startInspection.start(inspection.id());
            system.closeInspection.close(inspection.id()); // Missing answers create the corrective actions.
        }
        assertThat(system.findings.findAll()).hasSize(4).allSatisfy(finding ->
                assertThat(finding.correctiveAction().planningDueDate()).isEqualTo(LocalDate.parse("2026-04-04")));
        system.clock.advance(Duration.between(system.clock.now(), Instant.parse("2026-04-05T02:59:59Z")));
        assertThat(system.expireActions.sweep()).isEmpty(); // Still April 4 in Buenos Aires.
        system.clock.advance(Duration.ofSeconds(1));
        assertThat(system.clock.today()).isEqualTo(LocalDate.parse("2026-04-05"));
        assertThat(system.expireActions.sweep()).hasSize(4).allMatch(f -> f.correctiveAction().deadlineBreached());
        assertThat(system.expireActions.sweep()).isEmpty();
    }
}
