package ar.edu.itba.dps.certification.app.web;

/** The addressed resource does not exist. */
public final class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
