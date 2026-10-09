package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateLifecycle;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionClosed;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionExpired;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionPlanned;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionVoided;
import ar.edu.itba.dps.certification.domain.inspection.CriterionResultRevised;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Persists and audits the consequences decided by the domain. */
public final class CertificationReactions implements DomainEventHandler {

    private record Affected(InspectionId inspectionId, CriterionId criterionId) { }

    private final CertificateRepository certificates;
    private final InspectionQuery inspections;
    private final CertificateLifecycle lifecycle;
    private final AuditRecorder audit;
    private final FindingQuery findings;
    private final Clock clock;

    public CertificationReactions(CertificateRepository certificates, InspectionQuery inspections,
            CertificateLifecycle lifecycle, AuditRecorder audit,
            FindingQuery findings,
            Clock clock) {
        this.certificates = certificates;
        this.inspections = inspections;
        this.lifecycle = lifecycle;
        this.audit = audit;
        this.findings = findings; this.clock = clock;
    }

    @Override
    public void handle(DomainEvent event) {
        Optional<Affected> affected = affectedBy(event);
        if (affected.isEmpty()) {
            return;
        }
        List<Certificate> backing = certificates.findByBackingInspection(affected.get().inspectionId());
        if (backing.isEmpty()) {
            return;
        }
        Inspection inspection = inspections.require(affected.get().inspectionId());
        var at = clock.now();
        var currentFindings = findings.findingsOf(inspection.id());
        for (Certificate certificate : backing) {
            if (!weighsOn(inspection, affected.get().criterionId(), certificate)) {
                continue;
            }
            var context = new CertificationContext(
                    inspection, currentFindings, at.atZone(ZoneOffset.UTC).toLocalDate(),
                    certificate.scope(), Optional.empty(), Optional.empty());
            lifecycle.reconcile(certificate, context, at).ifPresent(change -> {
                certificates.save(change.certificate());
                recordChange(change);
            });
        }
    }

    private boolean weighsOn(Inspection inspection, CriterionId criterionId, Certificate certificate) {
        return certificate.scope().coveredSubsystem()
                .map(subsystem -> inspection.criterionWeighsOn(criterionId, subsystem))
                .orElse(true);
    }

    private void recordChange(CertificateLifecycle.Change change) {
        var certificate = change.certificate();
        if (change.suspended()) {
            audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                    AuditAction.CERTIFICATE_SUSPENDED,
                    AuditDetail.certificateStateChanged(change.previousStatus(), certificate),
                    change.reason());
        } else if (change.reactivated()) {
            audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                    AuditAction.CERTIFICATE_REACTIVATED,
                    AuditDetail.certificateStateChanged(change.previousStatus(), certificate));
        }
    }

    private Optional<Affected> affectedBy(DomainEvent event) {
        return switch (event) {
            case CorrectiveActionPlanned planned ->
                    Optional.of(new Affected(planned.inspectionId(), planned.criterionId()));
            case CorrectiveActionExpired expired ->
                    Optional.of(new Affected(expired.inspectionId(), expired.criterionId()));
            case CriterionResultRevised revised ->
                    Optional.of(new Affected(revised.inspectionId(), revised.criterionId()));
            case CorrectiveActionClosed closed ->
                    Optional.of(new Affected(closed.inspectionId(), closed.criterionId()));
            case CorrectiveActionVoided voided ->
                    Optional.of(new Affected(voided.inspectionId(), voided.criterionId()));
            default -> Optional.empty();
        };
    }
}
