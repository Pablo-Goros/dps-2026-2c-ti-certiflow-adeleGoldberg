package ar.edu.itba.dps.certification.app.jobs;

import ar.edu.itba.dps.certification.app.ops.OutboxOperations;
import ar.edu.itba.dps.certification.application.certification.usecase.ExpireDueCertificates;
import ar.edu.itba.dps.certification.application.finding.usecase.ExpireOverdueCorrectiveActions;
import ar.edu.itba.dps.certification.application.shared.port.Transactions;
import ar.edu.itba.dps.certification.application.shared.usecase.PublishPendingDomainEvents;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The background processes of the system, run on a schedule and on demand (admin endpoint):
 * <ul>
 *   <li>expire certificates whose validity ended;</li>
 *   <li>expire corrective actions past their deadline (which suspends the certificates they back);</li>
 *   <li>re-publish events still pending on their aggregates;</li>
 *   <li>deliver pending outbox events, retrying the ones that failed.</li>
 * </ul>
 * Each sweep runs in its own transaction and as the system actor. A failure is logged and the next
 * run tries again; nothing here can bring the application down.
 */
@Component
public class MaintenanceJobs {

    /** What one run did. */
    public record Result(int expiredCertificates, int expiredActions, int deliveredEvents) {
    }

    private static final System.Logger LOG = System.getLogger(MaintenanceJobs.class.getName());

    private final Transactions transactions;
    private final ExpireDueCertificates expireCertificates;
    private final ExpireOverdueCorrectiveActions expireActions;
    private final PublishPendingDomainEvents republish;
    private final OutboxOperations outbox;
    private final boolean enabled;

    public MaintenanceJobs(Transactions transactions, ExpireDueCertificates expireCertificates,
            ExpireOverdueCorrectiveActions expireActions, PublishPendingDomainEvents republish,
            OutboxOperations outbox, @Value("${certiflow.jobs.enabled:true}") boolean enabled) {
        this.transactions = transactions;
        this.expireCertificates = expireCertificates;
        this.expireActions = expireActions;
        this.republish = republish;
        this.outbox = outbox;
        this.enabled = enabled;
    }

    /** Runs every sweep once, in an order where each one feeds the next. */
    public Result runAll() {
        int actions = transactions.execute(() -> expireActions.sweep().size());
        int delivered = outbox.dispatchPending();
        int certificates = transactions.execute(() -> expireCertificates.sweep().size());
        transactions.execute(republish::publish);
        delivered += outbox.dispatchPending();
        return new Result(certificates, actions, delivered);
    }

    @Scheduled(fixedDelayString = "${certiflow.jobs.sweep-interval:PT1M}",
            initialDelayString = "${certiflow.jobs.initial-delay:PT30S}")
    void scheduledSweeps() {
        if (!enabled) {
            return;
        }
        try {
            Result result = runAll();
            LOG.log(System.Logger.Level.DEBUG, "maintenance run: " + result);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "maintenance run failed; it will be retried", e);
        }
    }

    /** Frequent and cheap: retries events whose handlers failed earlier. */
    @Scheduled(fixedDelayString = "${certiflow.jobs.dispatch-interval:PT5S}",
            initialDelayString = "${certiflow.jobs.initial-delay:PT30S}")
    void scheduledDelivery() {
        if (!enabled) {
            return;
        }
        outbox.dispatchPending();
    }
}
