package ar.edu.itba.dps.certification.domain.shared.port.inbound;

/** Input contract implemented by the application use case. */
public interface PublishPendingDomainEvents {
    void publish();
}
