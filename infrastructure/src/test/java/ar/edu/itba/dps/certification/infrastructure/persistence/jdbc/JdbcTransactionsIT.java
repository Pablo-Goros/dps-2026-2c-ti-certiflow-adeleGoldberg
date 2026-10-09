package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.persistence.StaleAggregateException;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcTransactionsIT {

    private final Scenario scenario = new Scenario();
    private JdbcPersistence database;
    private JdbcTransactions transactions;
    private Party party;
    private Asset asset;
    private AuditEntry entry;

    @BeforeEach
    void setUp() {
        database = TestDatabase.create();
        transactions = database.transactions();
        party = scenario.organization;
        asset = scenario.system.catalogue.assets.findAll().getFirst();
        entry = scenario.system.auditTrail.all().getFirst();
    }

    @Test
    @DisplayName("everything written in a unit of work becomes visible together when it ends")
    void aUnitOfWorkCommitsAllItsWrites() {
        transactions.execute(() -> {
            database.parties().save(party);
            database.assets().save(asset);
            database.auditTrail().append(entry);
        });

        assertThat(database.parties().findAll()).hasSize(1);
        assertThat(database.assets().findAll()).hasSize(1);
        assertThat(database.auditTrail().all()).hasSize(1);
    }

    @Test
    @DisplayName("a failure undoes every write of the unit of work and surfaces the original exception")
    void aFailureRollsBackEverything() {
        DomainException businessFailure = new DomainException("a business rule was broken");

        assertThatThrownBy(() -> transactions.execute(() -> {
            database.parties().save(party);
            database.assets().save(asset);
            database.auditTrail().append(entry);
            throw businessFailure;
        })).isSameAs(businessFailure);

        assertThat(database.parties().findAll()).isEmpty();
        assertThat(database.assets().findAll()).isEmpty();
        assertThat(database.auditTrail().all()).isEmpty();
    }

    @Test
    @DisplayName("a unit of work started inside another one joins it: both commit or both roll back")
    void nestedUnitsJoinTheOuterOne() {
        assertThatThrownBy(() -> transactions.execute(() -> {
            transactions.execute(() -> database.parties().save(party));
            throw new IllegalStateException("outer failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(database.parties().findAll()).isEmpty();
    }

    @Test
    @DisplayName("inside a unit of work a row is read once: every query returns the same instance")
    void theSameRowIsTheSameInstanceInsideAUnitOfWork() {
        database.assets().save(asset);

        transactions.execute(() -> {
            Asset byId = database.assets().findById(asset.id()).orElseThrow();
            Asset again = database.assets().findById(asset.id()).orElseThrow();
            Asset listed = database.assets().findAll().getFirst();
            Asset byType = database.assets().findByType(asset.assetType()).getFirst();
            assertThat(byId == again).isTrue();
            assertThat(byId == listed).isTrue();
            assertThat(byId == byType).isTrue();
        });

        Asset first = database.assets().findById(asset.id()).orElseThrow();
        Asset second = database.assets().findById(asset.id()).orElseThrow();
        assertThat(first == second).isFalse();
    }

    @Test
    @DisplayName("a change saved earlier in the unit of work is seen by later queries and committed once")
    void savedChangesAreVisibleInsideTheUnitOfWork() {
        database.assets().save(asset);

        transactions.execute(() -> {
            Asset loaded = database.assets().findById(asset.id()).orElseThrow();
            loaded.relocate("Warehouse 9");
            database.assets().save(loaded);
            loaded.relocate("Warehouse 10");
            database.assets().save(loaded);
        });

        assertThat(database.assets().findById(asset.id()).orElseThrow().location()).isEqualTo("Warehouse 10");
    }

    @Test
    @DisplayName("saving an aggregate that someone else changed after it was read is refused, not lost")
    void aStaleWriteIsRejected() throws Exception {
        database.assets().save(asset);
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch otherCommitted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread slow = new Thread(() -> {
            try {
                transactions.execute(() -> {
                    Asset mine = database.assets().findById(asset.id()).orElseThrow();
                    read.countDown();
                    await(otherCommitted);
                    mine.relocate("Slow writer");
                    database.assets().save(mine);
                });
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        slow.start();
        assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();

        transactions.execute(() -> {
            Asset theirs = database.assets().findById(asset.id()).orElseThrow();
            theirs.relocate("Fast writer");
            database.assets().save(theirs);
        });
        otherCommitted.countDown();
        slow.join(10_000);

        assertThat(failure.get()).isInstanceOf(StaleAggregateException.class);
        assertThat(database.assets().findById(asset.id()).orElseThrow().location()).isEqualTo("Fast writer");
    }

    @Test
    @DisplayName("after-commit actions run once the data is committed, and never after a rollback")
    void afterCommitActionsFollowTheOutcome() {
        AtomicInteger visibleToOthers = new AtomicInteger(-1);
        transactions.execute(() -> {
            database.parties().save(party);
            transactions.afterCommit(() -> visibleToOthers.set(database.parties().findAll().size()));
            assertThat(visibleToOthers.get()).isEqualTo(-1);
        });
        assertThat(visibleToOthers.get()).isEqualTo(1);

        AtomicInteger ranAfterRollback = new AtomicInteger();
        assertThatThrownBy(() -> transactions.execute(() -> {
            transactions.afterCommit(ranAfterRollback::incrementAndGet);
            throw new IllegalStateException("rolled back");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(ranAfterRollback.get()).isZero();
    }

    @Test
    @DisplayName("an after-commit action that fails does not undo or fail the committed work")
    void aFailingAfterCommitActionDoesNotAffectTheCommit() {
        transactions.execute(() -> {
            database.parties().save(party);
            transactions.afterCommit(() -> {
                throw new IllegalStateException("notification failed");
            });
        });

        assertThat(database.parties().findAll()).hasSize(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
