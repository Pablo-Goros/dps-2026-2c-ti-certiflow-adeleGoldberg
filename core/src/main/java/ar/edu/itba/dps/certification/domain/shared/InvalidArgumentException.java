package ar.edu.itba.dps.certification.domain.shared;

/** A malformed argument, distinct from a business rule violated by otherwise valid data. */
public final class InvalidArgumentException extends DomainException {

    public InvalidArgumentException(String message) {
        super(message);
    }
}
