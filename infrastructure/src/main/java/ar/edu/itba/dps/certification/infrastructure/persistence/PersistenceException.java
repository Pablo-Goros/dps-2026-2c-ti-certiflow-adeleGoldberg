package ar.edu.itba.dps.certification.infrastructure.persistence;

/**
 * Technical failure of the persistence adapters (broken connection, unreadable stored state,
 * unsupported object graph). It is deliberately unrelated to {@code DomainException}: a business
 * rule was not violated, the infrastructure could not do its job.
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
