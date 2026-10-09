package ar.edu.itba.dps.certification.app.config;

public final class ActorHeader {
    /** Id of the registered party that performs the request. See DESIGN.md for the security debt. */
    public static final String NAME = "X-Actor-Id";

    private ActorHeader() {
    }
}
