package ar.edu.itba.dps.certification.app.config;

/** The operation needs an identified actor and the request has none (or an unknown one). */
public final class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
