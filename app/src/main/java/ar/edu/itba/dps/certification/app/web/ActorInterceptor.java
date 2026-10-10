package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.config.ActorHeader;
import ar.edu.itba.dps.certification.app.config.AuthenticationRequiredException;
import ar.edu.itba.dps.certification.app.config.RequestActor;
import ar.edu.itba.dps.certification.application.catalogue.usecase.BrowseParties;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Identifies who performs the request from the {@code X-Actor-Id} header. A request without the
 * header runs as the system and is refused only by the use cases that require a user; a header
 * naming a party that does not exist is rejected outright.
 */
final class ActorInterceptor implements HandlerInterceptor {

    private final BrowseParties parties;
    private final RequestActor actors;

    ActorInterceptor(BrowseParties parties, RequestActor actors) {
        this.parties = parties;
        this.actors = actors;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String id = request.getHeader(ActorHeader.NAME);
        if (id == null || id.isBlank()) {
            return true;
        }
        var party = parties.find(new PartyId(id.trim()))
                .orElseThrow(() -> new AuthenticationRequiredException(
                        "unknown actor " + id.trim() + " in the " + ActorHeader.NAME + " header"));
        actors.set(Actor.user(party.id(), party.name()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        actors.clear();
    }
}
