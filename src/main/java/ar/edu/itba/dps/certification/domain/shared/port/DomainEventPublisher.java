package ar.edu.itba.dps.certification.domain.shared.port;

import ar.edu.itba.dps.certification.domain.shared.DomainEvent;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
