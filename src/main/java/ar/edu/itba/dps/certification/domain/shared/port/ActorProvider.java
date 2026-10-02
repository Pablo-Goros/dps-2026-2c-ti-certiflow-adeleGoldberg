package ar.edu.itba.dps.certification.domain.shared.port;

import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

public interface ActorProvider {

    Actor current();

    default PartyId requireUser() {
        if (current() instanceof Actor.User user) {
            return user.partyId();
        }
        throw new DomainException(
                "this operation requires an authenticated user");
    }
}
