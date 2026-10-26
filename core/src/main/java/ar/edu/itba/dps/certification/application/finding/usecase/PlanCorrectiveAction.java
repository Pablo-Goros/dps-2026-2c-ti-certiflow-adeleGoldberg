package ar.edu.itba.dps.certification.application.finding.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectionPlan;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.time.LocalDate;

public final class PlanCorrectiveAction implements ar.edu.itba.dps.certification.domain.finding.port.inbound.PlanCorrectiveAction {
    private final Transactions transactions;

    private final FindingRepository findings;
    private final PartyRepository parties;
    private final AuditRecorder audit;
    private final Clock clock;
    private final ActorProvider actors;
    private final DomainEventPublisher events;

    public PlanCorrectiveAction(FindingRepository findings, PartyRepository parties, AuditRecorder audit, Clock clock, ActorProvider actors, DomainEventPublisher events, Transactions transactions) {
        this.transactions = transactions;
        this.findings = findings; this.events = events;
        this.parties = parties;
        this.actors = actors;
        this.audit = audit;
        this.clock = clock;
    }

    public Finding plan(FindingId findingId, String work, PartyId executor, LocalDate dueDate) {
        return transactions.execute(() -> {
            var actor = actors.requireUser();
            Finding finding = findings.require(findingId);
            parties.require(executor);
            CorrectionPlan plan = new CorrectionPlan(work, executor, dueDate);
            var at = clock.now();
            finding.planCorrection(actor.partyId(), plan, clock.today(at), at);
            findings.save(finding);
            audit.recordAs(actor, AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
                    AuditAction.CORRECTIVE_ACTION_PLANNED,
                    AuditDetail.decision("confirm the correction plan",
                            work + ", executor " + executor + ", due " + dueDate));
            for (var event : finding.pendingEvents()) {
                events.publish(event);
                finding.acknowledgeEvent(event);
                findings.save(finding);
            }
            return finding;
        });
    }
}
