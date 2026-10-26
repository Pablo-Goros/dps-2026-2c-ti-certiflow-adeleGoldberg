package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.support.DomainWorld;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ScheduledVersionWithdrawalTest {
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private final InspectionSchema schema = new SchemaApplicability().create(
            SchemaId.of("schema"), "Equipment", Set.of(AssetType.EQUIPMENT), Set.of());

    private SchemaVersion publish(Instant publication, Instant effective) {
        schema.openDraft();
        if (schema.requireDraft().sections().isEmpty())
            schema.addSection(Section.of("Safety", 1, Criterion.of("HK", DomainWorld.housekeepingRule())));
        return schema.publish(publication, effective).publishedVersion();
    }

    @Test void scheduledVersionsNeedStrictlyIncreasingDatesAndEqualDatesNeverReplaceSilently() {
        publish(NOW, NOW);
        var future = publish(NOW.plusSeconds(1), NOW.plus(30, ChronoUnit.DAYS));
        schema.openDraft();
        assertThatThrownBy(() -> schema.publish(NOW.plusSeconds(2), future.effectiveFrom()))
                .hasMessageContaining("strictly after");
        assertThatThrownBy(() -> schema.publish(NOW.plusSeconds(2)))
                .hasMessageContaining("strictly after");
        assertThat(schema.publishedVersions()).hasSize(2);
        assertThat(schema.draft()).isPresent();
        assertThat(schema.effectiveVersionAt(future.effectiveFrom())).contains(future);
    }

    @Test void anExplicitWithdrawalAllowsAnUrgentCorrectionAndPreservesAllPublishedVersions() {
        var initial = publish(NOW, NOW);
        var scheduled = publish(NOW.plusSeconds(1), NOW.plus(30, ChronoUnit.DAYS));
        var at = NOW.plus(1, ChronoUnit.DAYS);
        var withdrawal = schema.withdrawScheduledVersion(2, at, "Incorrect threshold");
        var urgent = publish(at, at);
        var rescheduled = publish(at.plusSeconds(1), scheduled.effectiveFrom());
        assertThat(urgent.number()).isEqualTo(3);
        assertThat(rescheduled.number()).isEqualTo(4);
        assertThat(schema.findVersion(2)).contains(scheduled);
        assertThat(schema.withdrawals()).containsExactly(withdrawal);
        assertThat(schema.effectiveVersionAt(NOW)).contains(initial);
        assertThat(schema.effectiveVersionAt(at)).contains(urgent);
        assertThat(schema.effectiveVersionAt(scheduled.effectiveFrom())).contains(rescheduled);
        assertThat(scheduled.sections()).isEqualTo(initial.sections());
    }

    @Test void everyBlockingFutureVersionMustBeWithdrawnBeforeAnUrgentPublication() {
        publish(NOW, NOW);
        publish(NOW.plusSeconds(1), NOW.plus(30, ChronoUnit.DAYS));
        var last = publish(NOW.plusSeconds(2), NOW.plus(60, ChronoUnit.DAYS));
        schema.withdrawScheduledVersion(2, NOW.plusSeconds(3), "Wrong rule");
        schema.openDraft();
        assertThatThrownBy(() -> schema.publish(NOW.plusSeconds(4))).hasMessageContaining("strictly after");
        assertThat(schema.effectiveVersionAt(last.effectiveFrom())).contains(last);
        schema.withdrawScheduledVersion(3, NOW.plusSeconds(4), "Replan after correction");
        assertThat(schema.publish(NOW.plusSeconds(5)).published()).isTrue();
    }

    @Test void withdrawalIsOnlyForFutureVersionsAndRequiresAReasonWithoutPartialMutation() {
        publish(NOW, NOW);
        var scheduled = publish(NOW.plusSeconds(1), NOW.plus(30, ChronoUnit.DAYS));
        assertThatThrownBy(() -> schema.withdrawScheduledVersion(1, NOW.plusSeconds(2), "Too late"))
                .hasMessageContaining("only a future");
        assertThatThrownBy(() -> schema.withdrawScheduledVersion(2, scheduled.effectiveFrom(), "Too late"))
                .hasMessageContaining("only a future");
        assertThatThrownBy(() -> schema.withdrawScheduledVersion(2, NOW.plusSeconds(2), " "))
                .hasMessageContaining("reason");
        assertThat(schema.withdrawals()).isEmpty();
        schema.withdrawScheduledVersion(2, NOW.plusSeconds(2), "Cancelled");
        assertThatThrownBy(() -> schema.withdrawScheduledVersion(2, NOW.plusSeconds(3), "Again"))
                .hasMessageContaining("already withdrawn");
        assertThat(schema.withdrawals()).hasSize(1);
        assertThat(schema.effectiveVersionAt(scheduled.effectiveFrom()).orElseThrow().number()).isEqualTo(1);
    }
}
