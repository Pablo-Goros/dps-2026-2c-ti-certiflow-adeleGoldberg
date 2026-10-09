package ar.edu.itba.dps.certification.infrastructure.events;

import ar.edu.itba.dps.certification.application.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcEventOutbox;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcTransactions;

/**
 * The {@link DomainEventPublisher} of the running application. Publishing writes the event to the
 * outbox in the caller's transaction; delivery to the handlers is attempted once that transaction
 * commits (and again by the scheduled job if it did not succeed).
 */
public final class OutboxEventPublisher implements DomainEventPublisher {

    private final JdbcTransactions transactions;
    private final JdbcEventOutbox outbox;
    private final OutboxDispatcher dispatcher;

    public OutboxEventPublisher(JdbcTransactions transactions, JdbcEventOutbox outbox,
            OutboxDispatcher dispatcher) {
        this.transactions = transactions;
        this.outbox = outbox;
        this.dispatcher = dispatcher;
    }

    @Override
    public void publish(DomainEvent event) {
        transactions.execute(() -> outbox.enqueue(event));
        transactions.afterCommit(dispatcher::dispatchPending);
    }
}
