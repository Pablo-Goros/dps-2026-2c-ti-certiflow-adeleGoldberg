package ar.edu.itba.dps.certification.application.shared.usecase;

import ar.edu.itba.dps.certification.domain.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.shared.port.DomainEventPublisher;

/** Retries committed events; failed publication keeps the event pending on its aggregate. */
public final class PublishPendingDomainEvents {
    private final InspectionRepository inspections;
    private final FindingRepository findings;
    private final DomainEventPublisher events;
    public PublishPendingDomainEvents(InspectionRepository inspections, FindingRepository findings,
            DomainEventPublisher events) {
        this.inspections = inspections;
        this.findings = findings;
        this.events = events;
    }
    public void publish() {
        for (var finding : findings.findAll()) {
            for (var event : finding.pendingEvents()) {
                events.publish(event);
                finding.acknowledgeEvent(event);
                findings.save(finding);
            }
        }
        for (var inspection : inspections.findAll()) {
            for (var event : inspection.pendingEvents()) {
                events.publish(event);
                inspection.acknowledgeEvent(event);
                inspections.save(inspection);
            }
        }
    }
}
