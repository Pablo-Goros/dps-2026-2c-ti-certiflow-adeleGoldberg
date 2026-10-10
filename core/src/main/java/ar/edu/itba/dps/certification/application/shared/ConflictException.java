package ar.edu.itba.dps.certification.application.shared;

/**
 * An operation could not be applied because the stored state moved on: someone else changed the
 * same aggregate first (optimistic locking) or the record already exists. Callers can reload and
 * retry. Persistence adapters raise it; the driving adapters translate it (HTTP 409).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
