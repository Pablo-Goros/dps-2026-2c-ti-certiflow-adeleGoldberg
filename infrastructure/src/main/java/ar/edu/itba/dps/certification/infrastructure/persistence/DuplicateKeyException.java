package ar.edu.itba.dps.certification.infrastructure.persistence;

import ar.edu.itba.dps.certification.application.shared.ConflictException;

import java.sql.SQLException;

/** A unique constraint rejected the write (same primary key or a business uniqueness rule). */
public final class DuplicateKeyException extends ConflictException {

    public DuplicateKeyException(String message, SQLException cause) {
        super(message, cause);
    }
}
