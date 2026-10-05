package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;

public final class CertificateLifecycle {

    public record Change(Certificate certificate, CertificateStatus previousStatus,
            boolean suspended, boolean reactivated, String reason) { }

    /** Reconcile current scoped facts with the immutable policy used at issuance, never with event payloads. */
    public Optional<Change> reconcile(Certificate certificate,
            CertificationContext context, Instant at) {
        Validate.ensure(
                certificate.backingInspectionId().equals(context.inspectionId()) && certificate.scope().equals(context.scope()),
                "compliance facts must belong to the certificate and its scope");
        var previous = certificate.status();
        var policy = certificate.policy();
        var violations = new HashSet<CriterionId>();
        var overdue = new HashSet<CorrectiveActionId>();
        boolean suspended = false;
        for (var pending : context.pendingNonConformities()) {
            var finding = context.findings().stream().filter(f -> f.criterionId().equals(pending.criterionId())).findFirst();
            boolean policyViolation = certificate.mode() == CertificateMode.REGULAR
                    || !policy.permitsPending(pending.result(), pending.severity());
            boolean requiresPlan = finding.isEmpty() || finding.get().correctiveAction().awaitingPlan();
            boolean actionOverdue = finding.isPresent() && finding.get().correctiveAction().overdueAndOpen(context.today());
            boolean violates = policyViolation || requiresPlan || actionOverdue;
            if (violates) {
                violations.add(pending.criterionId());
                var evaluation = context.inspection().currentEvaluations().get(pending.criterionId());
                var actionId = finding.map(f -> f.correctiveAction().id());
                if ((policyViolation || requiresPlan) && certificate.unresolvedCauses().stream().noneMatch(c ->
                        c instanceof SuspensionCause.NonConformity n && n.criterionId().equals(pending.criterionId()))) {
                    suspended |= certificate.suspend(new SuspensionCause.NonConformity(pending.criterionId(),
                            actionId, evaluation.rectificationId()), at);
                }
                if (actionOverdue) {
                    overdue.add(finding.get().correctiveAction().id());
                    suspended |= certificate.suspend(new SuspensionCause.OverdueAction(finding.get().correctiveAction().id()), at);
                }
            }
        }
        var before = certificate.unresolvedCauses();
        boolean reactivated = certificate.resolveCauses(cause -> switch (cause) {
            case SuspensionCause.NonConformity nonconformity -> !violations.contains(nonconformity.criterionId());
            case SuspensionCause.OverdueAction action -> !overdue.contains(action.correctiveActionId());
        }, "current facts comply with the historical policy", at);
        if (!suspended && !reactivated && before.equals(certificate.unresolvedCauses())) return Optional.empty();
        return Optional.of(new Change(certificate, previous, suspended, reactivated,
                "current facts evaluated with " + policy.reference()));
    }

}
