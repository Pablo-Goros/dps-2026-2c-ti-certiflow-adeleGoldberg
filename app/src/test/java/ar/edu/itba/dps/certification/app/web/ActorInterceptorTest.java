package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.config.ActorHeader;
import ar.edu.itba.dps.certification.app.config.AuthenticationRequiredException;
import ar.edu.itba.dps.certification.app.config.RequestActor;
import ar.edu.itba.dps.certification.application.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.application.catalogue.usecase.BrowseParties;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Who is acting is decided from the X-Actor-Id header, against the registered parties. */
class ActorInterceptorTest {

    /** A register of parties kept in memory. */
    private static final class Register implements PartyRepository {
        private final List<Party> parties = new ArrayList<>();

        @Override
        public void save(Party party) {
            parties.add(party);
        }

        @Override
        public Optional<Party> findById(PartyId id) {
            return parties.stream().filter(party -> party.id().equals(id)).findFirst();
        }

        @Override
        public List<Party> findAll() {
            return List.copyOf(parties);
        }
    }

    private final RequestActor actors = new RequestActor();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private ActorInterceptor interceptor;

    @BeforeEach
    void registeredParties() {
        var register = new Register();
        register.save(new Party(PartyId.of("p-1"), "Ana", PartyKind.PERSON));
        interceptor = new ActorInterceptor(new BrowseParties(register), actors);
    }

    @AfterEach
    void clean() {
        actors.clear();
    }

    @Test
    void aRequestWithoutTheHeaderRunsAsTheSystem() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(actors.current()).isEqualTo(Actor.system());
    }

    @Test
    void aBlankHeaderCountsAsNoHeader() {
        request.addHeader(ActorHeader.NAME, "   ");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(actors.current()).isEqualTo(Actor.system());
    }

    @Test
    void aRegisteredPartyBecomesTheActor() {
        request.addHeader(ActorHeader.NAME, " p-1 ");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(actors.requireUser().partyId()).isEqualTo(PartyId.of("p-1"));
        assertThat(actors.requireUser().name()).isEqualTo("Ana");
    }

    @Test
    void anUnknownPartyIsRefusedInsteadOfFallingBackToTheSystem() {
        request.addHeader(ActorHeader.NAME, "ghost");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessageContaining("ghost");
        assertThat(actors.current()).isEqualTo(Actor.system());
    }

    @Test
    void theActorIsForgottenWhenTheRequestEnds() {
        request.addHeader(ActorHeader.NAME, "p-1");
        interceptor.preHandle(request, response, new Object());

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(actors.current()).isEqualTo(Actor.system());
    }
}
