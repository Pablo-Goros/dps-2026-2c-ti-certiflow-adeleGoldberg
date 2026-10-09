package ar.edu.itba.dps.certification.infrastructure.persistence;

/**
 * Optimistic concurrency failure: the aggregate was changed by someone else after it was read in
 * the running transaction, so saving it would silently discard that change.
 */
public final class StaleAggregateException extends PersistenceException {

    public StaleAggregateException(String table, String id) {
        super("the " + table + " '" + id + "' was modified by another transaction; reload it and retry");
    }
}
