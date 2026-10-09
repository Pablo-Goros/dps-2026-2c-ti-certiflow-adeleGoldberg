package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Delivers each event to the registered handlers inside the caller's transaction. Known debt: a
 * failing handler rolls back the whole request. The transactional outbox replaces this class.
 */
public final class SynchronousEventPublisher implements DomainEventPublisher {

    private final List<DomainEventHandler> handlers = new CopyOnWriteArrayList<>();

    public void register(DomainEventHandler handler) {
        handlers.add(handler);
    }

    @Override
    public void publish(DomainEvent event) {
        handlers.forEach(handler -> handler.handle(event));
    }
}
