package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.jobs.MaintenanceJobs;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import ar.edu.itba.dps.certification.infrastructure.events.OutboxDispatcher;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcEventOutbox;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcTransactions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operations on the background processes. Not protected (see the security debt in DESIGN.md): they
 * exist so the processes can be demonstrated and tested without waiting for the schedule.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController {

    private static final Set<String> STATUSES = Set.of("PENDING", "DONE", "DEAD");

    private final MaintenanceJobs jobs;
    private final JdbcEventOutbox outbox;
    private final OutboxDispatcher dispatcher;
    private final JdbcTransactions transactions;

    AdminController(MaintenanceJobs jobs, JdbcEventOutbox outbox, OutboxDispatcher dispatcher,
            JdbcTransactions transactions) {
        this.jobs = jobs;
        this.outbox = outbox;
        this.dispatcher = dispatcher;
        this.transactions = transactions;
    }

    /** Runs every sweep now and reports what each one did. */
    @PostMapping("/jobs/run")
    MaintenanceJobs.Result run() {
        return jobs.runAll();
    }

    /** Events of the outbox, optionally only those PENDING, DONE or DEAD. */
    @GetMapping("/outbox")
    List<JdbcEventOutbox.Entry> outbox(@RequestParam(name = "status", required = false) String status) {
        if (status != null && !STATUSES.contains(status)) {
            throw new InvalidArgumentException("status must be one of " + STATUSES);
        }
        return outbox.entries(status);
    }

    /** Gives the events that gave up a fresh set of attempts and delivers them. */
    @PostMapping("/outbox/retry-dead")
    Map<String, Integer> retryDead() {
        int revived = transactions.execute(outbox::retryDead);
        int delivered = dispatcher.dispatchPending();
        return Map.of("revived", revived, "delivered", delivered);
    }
}
