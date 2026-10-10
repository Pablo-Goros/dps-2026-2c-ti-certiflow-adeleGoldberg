package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.jobs.MaintenanceJobs;
import ar.edu.itba.dps.certification.app.ops.OutboxOperations;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    private final OutboxOperations outbox;

    AdminController(MaintenanceJobs jobs, OutboxOperations outbox) {
        this.jobs = jobs;
        this.outbox = outbox;
    }

    /** Runs every sweep now and reports what each one did. */
    @PostMapping("/jobs/run")
    MaintenanceJobs.Result run() {
        return jobs.runAll();
    }

    /** Events of the outbox, optionally only those PENDING, DONE or DEAD. */
    @GetMapping("/outbox")
    List<OutboxOperations.Entry> outbox(@RequestParam(name = "status", required = false) String status) {
        if (status != null && !STATUSES.contains(status)) {
            throw new InvalidArgumentException("status must be one of " + STATUSES);
        }
        return outbox.entries(status);
    }

    /** Gives the events that gave up a fresh set of attempts and delivers them. */
    @PostMapping("/outbox/retry-dead")
    OutboxOperations.Revival retryDead() {
        return outbox.retryDead();
    }
}
