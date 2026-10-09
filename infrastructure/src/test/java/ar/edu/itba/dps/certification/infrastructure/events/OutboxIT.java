package ar.edu.itba.dps.certification.infrastructure.events;

import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionPlanned;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcEventOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The outbox over a real database: atomic with the change, delivered after commit, retried when a handler fails. */
class OutboxIT {

    private JdbcPersistence persistence;
    private final List<DomainEvent> handled = new CopyOnWriteArrayList<>();
    private final AtomicBoolean handlerBroken = new AtomicBoolean(false);
    private OutboxEventPublisher publisher;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void open() {
        persistence = TestDatabase.create();
        DomainEventHandler handler = event -> {
            if (handlerBroken.get()) {
                throw new IllegalStateException("handler is down");
            }
            handled.add(event);
        };
        dispatcher = new OutboxDispatcher(persistence.transactions(), persistence.eventOutbox(),
                List.of(handler), 3);
        publisher = new OutboxEventPublisher(persistence.transactions(), persistence.eventOutbox(), dispatcher);
    }

    private static DomainEvent event(String criterion) {
        return new CorrectiveActionPlanned(InspectionId.of("i-1"), CriterionId.of(criterion),
                Instant.parse("2026-03-01T10:00:00Z"));
    }

    private void saveParty(String name) {
        persistence.parties().save(new Party(new PartyId("p-" + name), name, PartyKind.PERSON));
    }

    private List<JdbcEventOutbox.Entry> entries(String status) {
        return persistence.eventOutbox().entries(status);
    }

    @Test
    void anEventIsNotHandledUntilTheTransactionThatRaisedItCommits() {
        List<Integer> handledInside = new ArrayList<>();

        persistence.transactions().execute(() -> {
            saveParty("ana");
            publisher.publish(event("A"));
            handledInside.add(handled.size());
        });

        assertThat(handledInside).containsExactly(0);
        assertThat(handled).hasSize(1);
        assertThat(entries("DONE")).hasSize(1);
        assertThat(entries("PENDING")).isEmpty();
    }

    @Test
    void aRolledBackTransactionLeavesNoEvent() {
        assertThatThrownBy(() -> persistence.transactions().execute(() -> {
            saveParty("bea");
            publisher.publish(event("B"));
            throw new IllegalStateException("the use case failed afterwards");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(entries(null)).isEmpty();
        assertThat(handled).isEmpty();
        assertThat(persistence.parties().findById(new PartyId("p-bea"))).isEmpty();
    }

    @Test
    void aFailingHandlerDoesNotUndoTheChangeAndTheEventIsRetried() {
        handlerBroken.set(true);

        persistence.transactions().execute(() -> {
            saveParty("carla");
            publisher.publish(event("C"));
        });

        assertThat(persistence.parties().findById(new PartyId("p-carla"))).isPresent();
        assertThat(entries("PENDING")).hasSize(1);
        assertThat(entries("PENDING").get(0).attempts()).isEqualTo(1);
        assertThat(entries("PENDING").get(0).lastError()).contains("handler is down");

        handlerBroken.set(false);
        int delivered = dispatcher.dispatchPending();

        assertThat(delivered).isEqualTo(1);
        assertThat(handled).hasSize(1);
        assertThat(entries("DONE")).hasSize(1);
        assertThat(entries("PENDING")).isEmpty();
    }

    @Test
    void anEventThatKeepsFailingIsParkedAsDeadAndCanBeRevived() {
        handlerBroken.set(true);
        persistence.transactions().execute(() -> publisher.publish(event("D")));
        dispatcher.dispatchPending();
        dispatcher.dispatchPending();

        assertThat(entries("DEAD")).hasSize(1);
        assertThat(entries("DEAD").get(0).attempts()).isEqualTo(3);

        handlerBroken.set(false);
        assertThat(dispatcher.dispatchPending()).isZero();
        assertThat(persistence.eventOutbox().retryDead()).isEqualTo(1);
        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(handled).hasSize(1);
    }

    @Test
    void theEventComesBackExactlyAsItWasPublished() {
        DomainEvent original = event("E");

        persistence.transactions().execute(() -> publisher.publish(original));

        assertThat(handled).containsExactly(original);
    }

    @Test
    void severalDispatchersNeverDeliverTheSameEventTwice() throws Exception {
        handlerBroken.set(true);
        for (int i = 0; i < 20; i++) {
            String criterion = "C" + i;
            persistence.transactions().execute(() -> persistence.eventOutbox().enqueue(event(criterion)));
        }
        handlerBroken.set(false);
        AtomicInteger deliveries = new AtomicInteger();
        // Separate dispatchers (as separate application instances would be): only the row claim keeps them apart.
        List<OutboxDispatcher> dispatchers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            dispatchers.add(new OutboxDispatcher(persistence.transactions(), persistence.eventOutbox(),
                    List.of(event -> deliveries.incrementAndGet()), 3));
        }
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (OutboxDispatcher each : dispatchers) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                each.dispatchPending();
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        dispatchers.get(0).dispatchPending();

        assertThat(deliveries.get()).isEqualTo(20);
        assertThat(entries("DONE")).hasSize(20);
    }
}
