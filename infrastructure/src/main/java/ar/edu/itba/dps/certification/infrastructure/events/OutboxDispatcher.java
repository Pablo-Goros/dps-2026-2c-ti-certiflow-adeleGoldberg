package ar.edu.itba.dps.certification.infrastructure.events;

import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcEventOutbox;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcTransactions;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Delivers pending outbox events to the handlers.
 *
 * <p>Each event is handled in its own transaction that first claims the row: if the handlers throw,
 * the transaction rolls back (the row is pending again and none of their writes survive), and the
 * failure is recorded in a second transaction. So an event is applied at most once, never lost, and
 * a failing handler never touches the request that raised the event.
 *
 * <p>A pass delivers each pending row at most once; failed rows wait for the next pass (the
 * scheduled job), so a broken handler is not retried in a tight loop. Only one thread dispatches at a
 * time; a caller that finds another one busy returns at once and the busy one (or the next
 * scheduled pass) picks up what it left.
 */
public final class OutboxDispatcher {

    private static final System.Logger LOG = System.getLogger(OutboxDispatcher.class.getName());
    private static final int BATCH = 50;

    private final JdbcTransactions transactions;
    private final JdbcEventOutbox outbox;
    private final List<DomainEventHandler> handlers;
    private final int maxAttempts;
    private final ReentrantLock busy = new ReentrantLock();

    public OutboxDispatcher(JdbcTransactions transactions, JdbcEventOutbox outbox,
            List<DomainEventHandler> handlers, int maxAttempts) {
        this.transactions = transactions;
        this.outbox = outbox;
        this.handlers = List.copyOf(handlers);
        this.maxAttempts = maxAttempts;
    }

    /** Delivers what is pending and returns how many events were delivered. Never throws. */
    public int dispatchPending() {
        if (busy.isHeldByCurrentThread() || !busy.tryLock()) {
            return 0;
        }
        try {
            int delivered = 0;
            int deliveredInPass;
            do {
                deliveredInPass = 0;
                for (JdbcEventOutbox.Pending row : pendingSafely()) {
                    if (deliver(row)) {
                        deliveredInPass++;
                    }
                }
                delivered += deliveredInPass;
            } while (deliveredInPass > 0);
            return delivered;
        } finally {
            busy.unlock();
        }
    }

    private List<JdbcEventOutbox.Pending> pendingSafely() {
        try {
            return outbox.pending(BATCH);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "could not read the event outbox", e);
            return List.of();
        }
    }

    private boolean deliver(JdbcEventOutbox.Pending row) {
        try {
            return transactions.execute(() -> {
                if (!outbox.claim(row.seq())) {
                    return false;
                }
                DomainEvent event = row.event();
                handlers.forEach(handler -> handler.handle(event));
                return true;
            });
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING,
                    "event " + row.seq() + " (" + row.eventType() + ") failed: " + failure, failure);
            try {
                transactions.execute(() -> outbox.recordFailure(row.seq(), String.valueOf(failure), maxAttempts));
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "could not record the failure of event " + row.seq(), e);
            }
            return false;
        }
    }
}
