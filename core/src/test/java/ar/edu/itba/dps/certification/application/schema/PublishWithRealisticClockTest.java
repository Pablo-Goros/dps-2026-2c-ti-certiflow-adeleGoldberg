package ar.edu.itba.dps.certification.application.schema;

import ar.edu.itba.dps.certification.application.schema.usecase.PublishSchemaVersion;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real clock moves between two readings. Publishing "now" must read it once, otherwise the
 * effective date lands a few microseconds before the publication instant and is refused.
 */
class PublishWithRealisticClockTest {

    private static final class TickingClock implements Clock {
        private Instant current = Instant.parse("2026-03-01T10:00:00Z");

        @Override
        public Instant now() {
            current = current.plusNanos(1_000);
            return current;
        }

        @Override
        public LocalDate today() {
            return LocalDate.of(2026, 3, 1);
        }
    }

    @Test
    void publishingNowWorksWhenTheClockAdvancesBetweenReadings() {
        var system = new FullSystem();
        var schema = system.createSchema.create("Laboratory inspection", Set.of(AssetType.LABORATORY));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Safety", 1,
                DomainWorld.temperatureCriterion(), DomainWorld.documentationCriterion()));

        var result = new PublishSchemaVersion(system.schemas, new TickingClock(), system.audit)
                .publish(schema.id());

        assertThat(result.published()).isTrue();
        assertThat(result.publishedVersion().effectiveFrom()).isEqualTo(result.publishedVersion().publishedAt());
    }
}
