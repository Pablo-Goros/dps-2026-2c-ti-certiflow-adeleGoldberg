package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

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
