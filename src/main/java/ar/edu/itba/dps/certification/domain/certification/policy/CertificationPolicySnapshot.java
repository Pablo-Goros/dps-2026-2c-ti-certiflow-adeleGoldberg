package ar.edu.itba.dps.certification.domain.certification.policy;

import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Period;
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

    private static void positive(Period duration, String name) {
        Validate.required(duration, name);
        Validate.ensure(!duration.isZero() && !duration.isNegative(), name + " must be positive");
    }
}
