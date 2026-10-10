package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.app.ops.OutboxOperations;
import ar.edu.itba.dps.certification.infrastructure.events.OutboxDispatcher;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcEventOutbox;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcTransactions;

import java.util.List;

/** The operations side of the JDBC outbox: the only place where it meets the web and the jobs. */
final class JdbcOutboxOperations implements OutboxOperations {

    private final JdbcTransactions transactions;
    private final JdbcEventOutbox outbox;
    private final OutboxDispatcher dispatcher;

    JdbcOutboxOperations(JdbcTransactions transactions, JdbcEventOutbox outbox, OutboxDispatcher dispatcher) {
        this.transactions = transactions;
        this.outbox = outbox;
        this.dispatcher = dispatcher;
    }

    @Override
    public int dispatchPending() {
        return dispatcher.dispatchPending();
    }

    @Override
    public List<Entry> entries(String status) {
        return outbox.entries(status).stream()
                .map(e -> new Entry(e.seq(), e.eventType(), e.status(), e.attempts(), e.lastError(), e.createdAtMillis()))
                .toList();
    }

    @Override
    public Revival retryDead() {
        int revived = transactions.execute(outbox::retryDead);
        return new Revival(revived, dispatcher.dispatchPending());
    }
}
