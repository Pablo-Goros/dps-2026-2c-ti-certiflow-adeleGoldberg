package ar.edu.itba.dps.certification.domain.shared.port.inbound;

/** Runs independent business sweeps and outbox deliveries; it is not one atomic batch. */
public interface RunMaintenance {
    record Result(int expiredCertificates, int expiredActions, int deliveredEvents) { }
    Result runAll();
}
