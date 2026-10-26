package ar.edu.itba.dps.certification.application;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.domain.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;
import ar.edu.itba.dps.certification.support.*;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UseCaseTransactionTest {
    private static final class Guard implements Transactions {
        boolean active;
        int calls;
        @Override public <T> T execute(Supplier<T> work) {
            calls++;
            active = true;
            try { return work.get(); } finally { active = false; }
        }
        @Override public void execute(Runnable work) { execute(() -> { work.run(); return null; }); }
    }

    @Test void directInputPortInvocationIncludesTheRepositoryAndAuditInsideItsOwnTransaction() {
        var transactions = new Guard();
        var parties = mock(PartyRepository.class);
        var trail = mock(AuditTrail.class);
        doAnswer(call -> { assertThat(transactions.active).isTrue(); return null; }).when(parties).save(any());
        doAnswer(call -> { assertThat(transactions.active).isTrue(); return null; }).when(trail).append(any());
        ar.edu.itba.dps.certification.domain.catalogue.port.inbound.RegisterParty input = new RegisterParty(parties,
                new SequentialIds(), new AuditRecorder(trail, new FixedActor("editor"),
                TestClock.at("2026-03-05T13:00:00Z")), transactions);
        assertThat(input.register("Inspector", PartyKind.PERSON).name()).isEqualTo("Inspector");
        verify(parties).save(any());
        verify(trail).append(any());
        assertThat(transactions.calls).isEqualTo(1);
        assertThat(transactions.active).isFalse();
    }
}
