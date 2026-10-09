package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.infrastructure.persistence.PersistenceException;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.util.List;

/**
 * The outbox table. {@link #enqueue} joins the caller's transaction, which is the whole point: the
 * event exists if and only if the change that raised it was committed.
 */
public final class JdbcEventOutbox {

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    public static final String DEAD = "DEAD";

    /** A row waiting for delivery; the event is decoded only when asked for, so one unreadable row cannot hide the others. */
    public record Pending(long seq, String eventType, int attempts, String document, StateCodec codec) {

        public DomainEvent event() {
            try {
                Class<?> type = Class.forName(eventType, false, JdbcEventOutbox.class.getClassLoader());
                return codec.read(document, type.asSubclass(DomainEvent.class));
            } catch (ClassNotFoundException | ClassCastException e) {
                throw new PersistenceException("outbox row " + seq + " holds an unknown event type " + eventType, e);
            }
        }
    }

    /** What the monitoring endpoint shows about a row. */
    public record Entry(long seq, String eventType, String status, int attempts, String lastError, long createdAtMillis) {
    }

    private final JdbcTransactions db;
    private final StateCodec codec;

    public JdbcEventOutbox(JdbcTransactions db, StateCodec codec) {
        this.db = db;
        this.codec = codec;
    }

    public void enqueue(DomainEvent event) {
        db.update("INSERT INTO domain_event_outbox (event_type, status, attempts, created_at, doc) "
                        + "VALUES (?, ?, ?, ?, ?)",
                event.getClass().getName(), PENDING, 0, System.currentTimeMillis(), codec.write(event));
    }

    /** Oldest pending rows first. */
    public List<Pending> pending(int limit) {
        return db.query("SELECT seq, event_type, attempts, doc FROM domain_event_outbox "
                        + "WHERE status = 'PENDING' ORDER BY seq FETCH FIRST " + Math.max(1, limit) + " ROWS ONLY",
                row -> new Pending(row.getLong(1), row.getString(2), row.getInt(3), row.getString(4), codec));
    }

    /**
     * Takes the row for the running transaction: true for exactly one caller. Another transaction
     * trying the same row waits for this one to end and then finds it no longer pending.
     */
    public boolean claim(long seq) {
        return db.update("UPDATE domain_event_outbox SET status = 'DONE' WHERE seq = ? AND status = 'PENDING'", seq) == 1;
    }

    /** Counts a failed attempt; after {@code maxAttempts} the row is parked as DEAD instead of retried forever. */
    public void recordFailure(long seq, String error, int maxAttempts) {
        String shortened = error == null ? "" : error.substring(0, Math.min(error.length(), 900));
        db.update("UPDATE domain_event_outbox SET attempts = attempts + 1, last_error = ?, "
                        + "status = CASE WHEN attempts + 1 >= ? THEN 'DEAD' ELSE 'PENDING' END WHERE seq = ?",
                shortened, maxAttempts, seq);
    }

    /** Gives dead rows a fresh set of attempts; returns how many were revived. */
    public int retryDead() {
        return db.update("UPDATE domain_event_outbox SET status = 'PENDING', attempts = 0 WHERE status = 'DEAD'");
    }

    public List<Entry> entries(String status) {
        String filter = status == null ? "" : "WHERE status = ? ";
        Object[] parameters = status == null ? new Object[0] : new Object[] {status};
        return db.query("SELECT seq, event_type, status, attempts, last_error, created_at FROM domain_event_outbox "
                        + filter + "ORDER BY seq",
                row -> new Entry(row.getLong(1), row.getString(2), row.getString(3), row.getInt(4),
                        row.getString(5), row.getLong(6)),
                parameters);
    }
}
