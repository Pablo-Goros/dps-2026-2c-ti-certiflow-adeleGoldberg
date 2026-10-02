package ar.edu.itba.dps.certification.domain.shared.port;

import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.DomainException;

public interface ActorProvider {

    Actor current();

    /** The authenticated user acting now; automatic operations (system actor) are refused. */
    default Actor.User requireUser() {
        if (current() instanceof Actor.User user) {
            return user;
        }
        throw new DomainException(
                "this operation requires an authenticated user");
    }
}
