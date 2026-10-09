package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;

import java.util.ArrayList;
import java.util.List;

public final class RecordingEventPublisher implements DomainEventPublisher {

    public final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
    }

    public <T extends DomainEvent> List<T> ofType(Class<T> type) {
        return published.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
