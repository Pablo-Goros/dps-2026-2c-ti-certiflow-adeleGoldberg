package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.RectificationConsequences;
import ar.edu.itba.dps.certification.domain.inspection.port.FindingRegistry;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Rectification;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;

import java.time.Instant;
import java.util.List;

public final class RectifyClosedInspection {

    private final InspectionRepository inspections;
    private final FindingRegistry findings;
    private final RectificationConsequences consequences;
    private final ActorProvider actors;
    private final DomainEventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditRecorder audit;

    public RectifyClosedInspection(InspectionRepository inspections, AssetDirectory assets, FindingRegistry findings,
            DomainEventPublisher events, IdGenerator ids, Clock clock, AuditRecorder audit, ActorProvider actors) {
        this.inspections = inspections;
        this.findings = findings;
        this.consequences = new RectificationConsequences(findings, assets);
        this.actors = actors;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public Rectification rectify(InspectionId inspectionId, String reason, List<Correction> corrections) {
        return rectify(inspectionId, actors.requireUser(), reason, corrections);
    }

    public Rectification rectify(InspectionId inspectionId, PartyId author, String reason,
            List<Correction> corrections) {
        Inspection inspection = inspections.require(inspectionId);
        Instant at = clock.now();
        RectificationId rectificationId = new RectificationId(ids.newIdentifier());
        var actingUser = actors.current();
        if (!(actingUser instanceof Actor.User user)) {
            throw new DomainException("this operation requires an authenticated user");
        }
        PartyId actor = user.partyId();
        Validate.ensure(actor.equals(author),
                "rectification author must match the authenticated actor");
        var previousEvaluations = inspection.currentEvaluations();
        Rectification rectification = inspection.rectify(rectificationId, actor, at, reason, corrections);

        inspections.save(inspection);
        audit.recordAs(actingUser, AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_RECTIFIED,
                AuditDetail.dataChanged(rectification.changes().stream()
                        .map(change -> new FieldChange(
                                change.field(),
                                change.previousValue(),
                                change.currentValue()))
                        .toList()),
                rectification.reason());
        consequences.apply(inspection, rectification, previousEvaluations);
        for (var event : findings.pendingEvents(inspection.id())) {
            events.publish(event);
            findings.acknowledgeEvent(inspection.id(), event);
        }
        for (var event : inspection.pendingEvents()) {
            events.publish(event);
            inspection.acknowledgeEvent(event);
        }
        inspections.save(inspection);
        return rectification;
    }

}
