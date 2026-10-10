package ar.edu.itba.dps.certification.app.ops;

import java.util.List;

/**
 * What the operations side (scheduled jobs, admin endpoints) needs from the event outbox, without
 * knowing how it is stored or delivered.
 */
public interface OutboxOperations {

    /** One stored event as monitoring shows it. {@code status} is PENDING, DONE or DEAD. */
    record Entry(long seq, String eventType, String status, int attempts, String lastError, long createdAtMillis) {
    }

    /** {@code revived} events got fresh attempts; {@code delivered} were handled in the same call. */
    record Revival(int revived, int delivered) {
    }

    /** Delivers the pending events once and returns how many were handled. Never throws. */
    int dispatchPending();

    /** Stored events, optionally only those with the given status. */
    List<Entry> entries(String status);

    /** Gives the events that gave up a fresh set of attempts and delivers them. */
    Revival retryDead();
}
