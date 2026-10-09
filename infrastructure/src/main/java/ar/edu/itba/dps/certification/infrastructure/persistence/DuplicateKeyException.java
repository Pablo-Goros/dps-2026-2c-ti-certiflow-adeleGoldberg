package ar.edu.itba.dps.certification.infrastructure.persistence;

import java.sql.SQLException;

/** A unique constraint rejected the write (same primary key or a business uniqueness rule). */
public final class DuplicateKeyException extends PersistenceException {

    public DuplicateKeyException(String message, SQLException cause) {
        super(message, cause);
    }
}
