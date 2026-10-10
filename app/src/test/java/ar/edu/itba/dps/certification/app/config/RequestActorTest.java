package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestActorTest {

    private final RequestActor actors = new RequestActor();

    @AfterEach
    void clean() {
        actors.clear();
    }

    @Test
    void withoutAnActorTheSystemActs() {
        assertThat(actors.current()).isEqualTo(Actor.system());
    }

    @Test
    void anOperationThatNeedsAUserIsRefusedWhenNobodyIsIdentified() {
        assertThatThrownBy(actors::requireUser)
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessageContaining(ActorHeader.NAME);
    }

    @Test
    void theIdentifiedUserIsTheActorUntilItIsCleared() {
        actors.set(Actor.user(PartyId.of("p-1"), "Ana"));

        assertThat(actors.requireUser().partyId()).isEqualTo(PartyId.of("p-1"));
        assertThat(actors.current().displayName()).isEqualTo("Ana");

        actors.clear();
        assertThat(actors.current()).isEqualTo(Actor.system());
    }

    @Test
    void theActorOfOneThreadIsNotSeenByAnother() throws InterruptedException {
        actors.set(Actor.user(PartyId.of("p-1"), "Ana"));
        var seenElsewhere = new java.util.concurrent.atomic.AtomicReference<Actor>();

        var other = new Thread(() -> seenElsewhere.set(actors.current()));
        other.start();
        other.join();

        assertThat(seenElsewhere.get()).isEqualTo(Actor.system());
    }
}
