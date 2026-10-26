package ar.edu.itba.dps.certification.domain.certification.policy;

import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.FixedDurationValidityPolicy;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceRequirement;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceRequirements;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Immutable rule definition; contains no mutable strategies or anonymous executable rules. */
public record CertificationPolicySnapshot(CertificationPolicyRef reference,
        Set<Severity> blockingSeverities, boolean allowsConditional,
        Period regularDuration, Period conditionalDuration, Set<PolicyRestriction> restrictions) {
    public CertificationPolicySnapshot {
        Validate.required(reference, "policy reference");
        blockingSeverities = Set.copyOf(Validate.required(blockingSeverities, "blocking severities"));
        restrictions = Set.copyOf(Validate.required(restrictions, "policy restrictions"));
        positive(regularDuration, "regular duration");
        positive(conditionalDuration, "conditional duration");
        Validate.ensure(allowsConditional || regularDuration.equals(conditionalDuration),
                "a policy forbidding conditional certificates must use the regular duration for both modes");
    }
    public boolean permitsPending(CriterionResult result, Severity severity) {
        Validate.required(result, "result");
        Validate.required(severity, "severity");
        return allowsConditional && !blockingSeverities.contains(severity)
                && !(result == CriterionResult.REJECTED && restrictions.contains(PolicyRestriction.REJECTIONS_MUST_BE_CORRECTED));
    }

    public List<IssuanceBlocker> complianceBlockers(CertificationContext context) {
        Validate.required(context, "context");
        var blockers = new ArrayList<IssuanceBlocker>();
        for (var requirement : List.<IssuanceRequirement>of(new IssuanceRequirements.NoActionMayBeOverdue(),
                new IssuanceRequirements.EveryActionMustBePlanned())) {
            requirement.unmetBy(context).ifPresent(blockers::add);
        }
        blockers.addAll(jurisdictionBlockers(context));
        return List.copyOf(blockers);
    }
    private List<IssuanceBlocker> jurisdictionBlockers(CertificationContext context) {
        var blockers = new ArrayList<IssuanceBlocker>();
        for (var pending : context.pendingNonConformities()) {
            if (blockingSeverities.contains(pending.severity())) {
                blockers.add(new IssuanceBlocker.BlockingSeverity(pending.criterionId(), pending.result(), pending.severity()));
            }
        }
        if (!allowsConditional && !context.pendingNonConformities().isEmpty()) {
            blockers.add(new IssuanceBlocker.ConditionalNotAllowed(context.pendingNonConformities().size()));
        }
        if (restrictions.contains(PolicyRestriction.REJECTIONS_MUST_BE_CORRECTED)) {
            new IssuanceRequirements.RejectionsMustBeCorrected().unmetBy(context).ifPresent(blockers::add);
        }
        return List.copyOf(blockers);
    }

    public ValidityPeriod validityFrom(Instant at, CertificateMode mode) {
        Validate.required(at, "issuance instant");
        Validate.required(mode, "certificate mode");
        Validate.ensure(mode != CertificateMode.CONDITIONAL || allowsConditional, "conditional mode is forbidden");
        return new FixedDurationValidityPolicy(mode == CertificateMode.REGULAR
                ? regularDuration : conditionalDuration).validityFrom(at);
    }

    private static void positive(Period duration, String name) {
        Validate.required(duration, name);
        Validate.ensure(!duration.isZero() && !duration.isNegative(), name + " must be positive");
    }
}
