package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.schema.usecase.WithdrawScheduledSchemaVersion;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.*;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import ar.edu.itba.dps.certification.support.*;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ScheduledVersionPersistenceIT {
    @Test void documentsWrittenBeforeWithdrawalsExistedRemainReadable() {
        var db = TestDatabase.create();
        var schema = new SchemaApplicability().create(SchemaId.of("legacy"), "Equipment",
                Set.of(AssetType.EQUIPMENT), Set.of());
        db.schemas().save(schema);
        var document = new StateCodec().write(schema);
        var legacy = document.replace(",\"withdrawals\":[]", "");
        assertThat(legacy).doesNotContain("withdrawals");
        db.transactions().execute(() -> db.transactions().update(
                "UPDATE inspection_schema SET doc = ? WHERE id = ?", legacy, schema.id().value()));
        var loaded = db.schemas().require(schema.id());
        assertThat(loaded.withdrawals()).isEmpty();
        loaded.openDraft();
        assertThat(loaded.draft()).isPresent();
    }

    @Test void withdrawalAndAuditSurviveReloadAndAnUrgentVersionCanBePublishedAfterwards() {
        var source = TestDatabase.dataSource();
        var db = JdbcPersistence.open(source);
        var clock = TestClock.at("2026-03-01T10:00:00Z");
        var actor = new FixedActor("editor");
        var schema = new SchemaApplicability().create(SchemaId.of("schema"), "Equipment",
                Set.of(AssetType.EQUIPMENT), Set.of());
        schema.openDraft();
        schema.addSection(Section.of("Safety", 1, Criterion.of("HK", DomainWorld.housekeepingRule())));
        var first = schema.publish(clock.now()).publishedVersion();
        schema.openDraft();
        var scheduled = schema.publish(clock.now(), clock.now().plus(Duration.ofDays(30))).publishedVersion();
        db.schemas().save(schema);
        clock.advanceDays(1);
        var usecase = new WithdrawScheduledSchemaVersion(db.schemas(), clock,
                new AuditRecorder(db.auditTrail(), actor, clock), actor, db.transactions());
        usecase.withdraw(schema.id(), 2, "Wrong threshold");

        var reopened = JdbcPersistence.open(source);
        var loaded = reopened.schemas().require(schema.id());
        assertThat(loaded.findVersion(2).map(SchemaVersion::id)).contains(scheduled.id());
        assertThat(loaded.effectiveVersionAt(scheduled.effectiveFrom()).map(SchemaVersion::id)).contains(first.id());
        assertThat(loaded.withdrawals()).singleElement().satisfies(w -> {
            assertThat(w.reason()).isEqualTo("Wrong threshold");
            assertThat(w.withdrawnAt()).isEqualTo(clock.now());
        });
        assertThat(reopened.auditTrail().entriesFor(AuditedElementRef.schema(schema.id().value())))
                .singleElement().satisfies(entry -> assertThat(entry.action()).isEqualTo(AuditAction.SCHEMA_VERSION_WITHDRAWN));
        loaded.openDraft();
        var urgent = loaded.publish(clock.now()).publishedVersion();
        reopened.schemas().save(loaded);
        assertThat(reopened.schemas().require(schema.id()).effectiveVersionAt(clock.now()).map(SchemaVersion::id)).contains(urgent.id());
        assertThat(urgent.number()).isEqualTo(3);
    }
}
