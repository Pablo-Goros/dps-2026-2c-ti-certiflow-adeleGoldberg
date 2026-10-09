package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.application.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.Actor;

/**
 * The actor of the request being served, kept in a thread-local that the web layer sets before the
 * controller runs and always clears afterwards. Without it the actor is the system.
 */
public final class RequestActor implements ActorProvider {

    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

    public void set(Actor actor) {
        CURRENT.set(actor);
    }

    public void clear() {
        CURRENT.remove();
    }

    @Override
    public Actor current() {
        Actor actor = CURRENT.get();
        return actor == null ? Actor.system() : actor;
    }

    @Override
    public Actor.User requireUser() {
        if (current() instanceof Actor.User user) {
            return user;
        }
        throw new AuthenticationRequiredException(
                "this operation requires an acting user: send the " + ActorHeader.NAME + " header");
    }
}
