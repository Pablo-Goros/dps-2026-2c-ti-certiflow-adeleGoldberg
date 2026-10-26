package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;

public final class FixedActor implements ActorProvider {

    private Actor actor;

    public FixedActor(String name) {
        this.actor = Actor.user(PartyId.of(name), name);
    }

    public void actingAs(Actor other) {
        this.actor = other;
    }

    @Override
    public Actor current() {
        return actor;
    }
}
