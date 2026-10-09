package ar.edu.itba.dps.certification.app.web;

import java.util.List;

/** A schema draft cannot be published; the draft stays open and the reasons are returned to the caller. */
public final class PublicationRefusedException extends RuntimeException {

    private final List<String> violations;

    public PublicationRefusedException(List<String> violations) {
        super("the draft cannot be published");
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
