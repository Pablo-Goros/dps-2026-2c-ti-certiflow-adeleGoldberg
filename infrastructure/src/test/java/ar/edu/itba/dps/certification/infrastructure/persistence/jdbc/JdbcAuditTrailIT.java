package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The audit trail on the real database: what the use cases recorded comes back unchanged and in order. */
class JdbcAuditTrailIT {

    private final Scenario scenario = new Scenario();
    private JdbcPersistence database;

    @BeforeEach
    void storeEveryEntryTheScenarioRecorded() {
        database = TestDatabase.create();
        scenario.system.auditTrail.all().forEach(database.auditTrail()::append);
    }

    @Test
    void everyEntryComesBackEqualAndInTheOrderItWasAppended() {
        assertThat(scenario.system.auditTrail.all()).isNotEmpty();

        assertThat(database.auditTrail().all()).isEqualTo(scenario.system.auditTrail.all());
    }

    @Test
    void theHistoryOfOneElementIsOnlyItsOwnEntries() {
        var inspection = AuditedElementRef.inspection(scenario.approvedInspection.value());

        var history = database.auditTrail().entriesFor(inspection);

        assertThat(history).isNotEmpty();
        assertThat(history).allMatch(entry -> entry.element().equals(inspection));
        assertThat(history.size()).isLessThan(database.auditTrail().all().size());
    }

    @Test
    void theHistoryCanBeNarrowedToOneAction() {
        var inspection = AuditedElementRef.inspection(scenario.approvedInspection.value());
        AuditAction action = database.auditTrail().entriesFor(inspection).getFirst().action();

        var narrowed = database.auditTrail().entriesFor(inspection, action);

        assertThat(narrowed).isNotEmpty();
        assertThat(narrowed).allMatch(entry -> entry.action() == action);
    }

    @Test
    void anElementWithoutHistoryHasNoEntries() {
        assertThat(database.auditTrail().entriesFor(AuditedElementRef.inspection("never-existed"))).isEmpty();
    }

    @Test
    void entriesAreOnlyEverAppended() {
        AuditEntry first = database.auditTrail().all().getFirst();
        int before = database.auditTrail().all().size();

        database.auditTrail().append(first);

        assertThat(database.auditTrail().all()).hasSize(before + 1);
        assertThat(database.auditTrail().all().getFirst()).isEqualTo(first);
    }
}
