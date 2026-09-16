package ar.edu.itba.dps.certification.application.shared.port;

import ar.edu.itba.dps.certification.domain.shared.Actor;

public interface ActorProvider {

    Actor current();
}
