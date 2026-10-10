package ar.edu.itba.dps.certification.app.jobs;

import ar.edu.itba.dps.certification.app.ops.OutboxOperations;
import ar.edu.itba.dps.certification.application.certification.usecase.ExpireDueCertificates;
import ar.edu.itba.dps.certification.application.finding.usecase.ExpireOverdueCorrectiveActions;
import ar.edu.itba.dps.certification.application.shared.port.Transactions;
import ar.edu.itba.dps.certification.application.shared.usecase.PublishPendingDomainEvents;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The orchestration of the sweeps, with the use cases and the outbox replaced by mocks. */
class MaintenanceJobsTest {

    /** Runs the work at once, as a transaction that always commits would. */
    private static final class Immediate implements Transactions {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }

        @Override
        public void execute(Runnable work) {
            work.run();
        }
    }

    private final ExpireDueCertificates expireCertificates = mock(ExpireDueCertificates.class);
    private final ExpireOverdueCorrectiveActions expireActions = mock(ExpireOverdueCorrectiveActions.class);
    private final PublishPendingDomainEvents republish = mock(PublishPendingDomainEvents.class);
    private final OutboxOperations outbox = mock(OutboxOperations.class);

    private MaintenanceJobs jobs(boolean enabled) {
        return new MaintenanceJobs(new Immediate(), expireCertificates, expireActions, republish, outbox, enabled);
    }

    @BeforeEach
    void nothingToDoByDefault() {
        when(expireActions.sweep()).thenReturn(List.of());
        when(expireCertificates.sweep()).thenReturn(List.of());
    }

    @Test
    void theSweepsRunInAnOrderWhereEachOneFeedsTheNext() {
        jobs(true).runAll();

        InOrder order = inOrder(expireActions, outbox, expireCertificates, republish);
        order.verify(expireActions).sweep();
        order.verify(outbox).dispatchPending();
        order.verify(expireCertificates).sweep();
        order.verify(republish).publish();
        order.verify(outbox).dispatchPending();
    }

    @Test
    void theResultReportsWhatEachSweepDid() {
        when(expireActions.sweep()).thenReturn(Collections.nCopies(2, (Finding) null));
        when(expireCertificates.sweep()).thenReturn(Collections.nCopies(3, (Certificate) null));
        when(outbox.dispatchPending()).thenReturn(4, 5);

        var result = jobs(true).runAll();

        assertThat(result).isEqualTo(new MaintenanceJobs.Result(3, 2, 9));
    }

    @Test
    void aScheduledRunDoesNothingWhenTheJobsAreDisabled() {
        jobs(false).scheduledSweeps();
        jobs(false).scheduledDelivery();

        verifyNoInteractions(expireActions, expireCertificates, republish, outbox);
    }

    @Test
    void aScheduledRunThatFailsIsSwallowedSoTheNextOneCanTryAgain() {
        when(expireActions.sweep()).thenThrow(new IllegalStateException("database is down"));

        assertThatCode(() -> jobs(true).scheduledSweeps()).doesNotThrowAnyException();
        verify(expireCertificates, never()).sweep();
    }

    @Test
    void theFrequentScheduledRunOnlyDeliversPendingEvents() {
        jobs(true).scheduledDelivery();

        verify(outbox, times(1)).dispatchPending();
        verifyNoInteractions(expireActions, expireCertificates, republish);
    }
}
