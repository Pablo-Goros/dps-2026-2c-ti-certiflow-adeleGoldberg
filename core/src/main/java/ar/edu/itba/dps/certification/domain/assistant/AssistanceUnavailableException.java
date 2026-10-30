package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.shared.Validate;

/**
 * No complete, readable reply could be obtained. The categories belong to the domain: each adapter
 * translates the signals of its provider into them, so callers never depend on a provider.
 */
public final class AssistanceUnavailableException extends RuntimeException {

    public enum Reason { UNREACHABLE, REFUSED, INCOMPLETE, MALFORMED, MISCONFIGURED }

    private final Reason reason;

    public AssistanceUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = Validate.required(reason, "reason");
    }

    public AssistanceUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Validate.required(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
