package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;

import java.util.ArrayList;
import java.util.List;

public final class DispatchingEventPublisher implements DomainEventPublisher {

    private final List<DomainEventHandler> handlers = new ArrayList<>();
    public final List<DomainEvent> published = new ArrayList<>();

    public void register(DomainEventHandler handler) {
        handlers.add(handler);
    }

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
        handlers.forEach(handler -> handler.handle(event));
    }

    public <T extends DomainEvent> List<T> ofType(Class<T> type) {
        return published.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
