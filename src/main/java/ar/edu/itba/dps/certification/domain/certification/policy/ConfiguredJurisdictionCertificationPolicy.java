package ar.edu.itba.dps.certification.domain.certification.policy;

import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.FixedDurationValidityPolicy;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationAssessment;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceRequirement;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceRequirements;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ConfiguredJurisdictionCertificationPolicy implements JurisdictionCertificationPolicy {
    private final CertificationPolicySnapshot snapshot;
    public ConfiguredJurisdictionCertificationPolicy(CertificationPolicySnapshot snapshot) {
        this.snapshot = Validate.required(snapshot, "policy snapshot");
    }
    @Override
    public CertificationPolicySnapshot snapshot() {
        return snapshot;
    }

    @Override
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
            if (snapshot.blockingSeverities().contains(pending.severity())) {
                blockers.add(new IssuanceBlocker.BlockingSeverity(pending.criterionId(), pending.result(), pending.severity()));
            }
        }
        if (!snapshot.allowsConditional() && !context.pendingNonConformities().isEmpty()) {
            blockers.add(new IssuanceBlocker.ConditionalNotAllowed(context.pendingNonConformities().size()));
        }
        if (snapshot.restrictions().contains(PolicyRestriction.REJECTIONS_MUST_BE_CORRECTED)) {
            new IssuanceRequirements.RejectionsMustBeCorrected().unmetBy(context).ifPresent(blockers::add);
        }
        return List.copyOf(blockers);
    }

    @Override
    public CertificationAssessment assess(CertificationContext context, Instant at) {
        var blockers = new ArrayList<IssuanceBlocker>();
        for (var requirement : IssuanceRequirements.common()) {
            requirement.unmetBy(context).ifPresent(blockers::add);
        }
        blockers.addAll(jurisdictionBlockers(context));
        var mode = blockers.isEmpty() ? Optional.of(context.pendingNonConformities().isEmpty()
                ? CertificateMode.REGULAR : CertificateMode.CONDITIONAL) : Optional.<CertificateMode>empty();
        return new CertificationAssessment(context.inspectionId(), context.scope(), at, snapshot, blockers, mode);
    }

    @Override
    public ValidityPeriod validityFrom(Instant at, CertificateMode mode) {
        Validate.required(mode, "mode");
        Validate.ensure(mode != CertificateMode.CONDITIONAL || snapshot.allowsConditional(), "conditional mode is forbidden");
        return new FixedDurationValidityPolicy(mode == CertificateMode.REGULAR
                ? snapshot.regularDuration() : snapshot.conditionalDuration()).validityFrom(at);
    }
}
