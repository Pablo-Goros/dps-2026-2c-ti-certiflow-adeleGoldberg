package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Rectification;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Proof of a domain evaluation. Eligibility, mode and common guarantees cannot be supplied by callers. */
public final class CertificationAssessment {
    private final CertificationContext context;
    private final InspectionFacts inspectionFacts;
    private final Instant evaluatedAt;
    private final CertificationPolicySnapshot policy;
    private final List<IssuanceBlocker> blockers;
    private final Optional<CertificateMode> mode;

    private CertificationAssessment(CertificationContext context, Instant at,
            CertificationPolicySnapshot policy, List<IssuanceBlocker> blockers) {
        this.context = context;
        this.inspectionFacts = InspectionFacts.of(context.inspection());
        this.evaluatedAt = at;
        this.policy = policy;
        this.blockers = List.copyOf(blockers);
        this.mode = blockers.isEmpty() ? Optional.of(context.pendingNonConformities().isEmpty()
                ? CertificateMode.REGULAR : CertificateMode.CONDITIONAL) : Optional.empty();
    }

    private CertificationAssessment(CertificationAssessment original, List<IssuanceBlocker> blockers) {
        this.context = original.context;
        this.inspectionFacts = original.inspectionFacts;
        this.evaluatedAt = original.evaluatedAt;
        this.policy = original.policy;
        this.blockers = List.copyOf(blockers);
        this.mode = blockers.isEmpty() ? original.mode : Optional.empty();
    }

    public static CertificationAssessment evaluate(CertificationContext context, Instant at,
            CertificationPolicySnapshot policy) {
        Validate.required(context, "certification context");
        Validate.required(at, "evaluation instant");
        Validate.required(policy, "policy snapshot");
        Validate.ensure(context.today().equals(at.atZone(ZoneOffset.UTC).toLocalDate()),
                "assessment date must match its evaluation instant");
        context.inspection().closedAt().ifPresent(closed -> Validate.ensure(!closed.isAfter(at),
                "issuance cannot precede closure"));
        var blockers = new LinkedHashSet<IssuanceBlocker>();
        for (var requirement : IssuanceRequirements.common()) requirement.unmetBy(context).ifPresent(blockers::add);
        blockers.addAll(policy.complianceBlockers(context));
        return new CertificationAssessment(context, at, policy, List.copyOf(blockers));
    }

    /** A strategy may add restrictions, but cannot remove a failed requirement or choose a mode. */
    public CertificationAssessment withAdditionalBlockers(List<IssuanceBlocker> additional) {
        var combined = new LinkedHashSet<>(blockers);
        combined.addAll(Validate.required(additional, "additional blockers"));
        return new CertificationAssessment(this, List.copyOf(combined));
    }

    public void requireEligibleFor(Inspection inspection) {
        Validate.required(inspection, "inspection");
        Validate.ensure(inspectionId().equals(inspection.id()), "assessment must belong to the backing inspection");
        Validate.ensure(blockers.isEmpty(), "assessment must have no issuance blockers");
        Validate.ensure(inspectionFacts.equals(InspectionFacts.of(inspection)),
                "inspection changed after assessment; evaluate it again");
        var current = evaluate(new CertificationContext(inspection, context.findings(), context.today(), scope(),
                context.laterClosedInspection(), context.nonExpiredCertificateOfAsset()), evaluatedAt, policy);
        Validate.ensure(current.blockers.isEmpty() && current.mode.equals(mode),
                "certification facts changed after assessment; evaluate them again");
    }

    public InspectionId inspectionId() { return inspectionFacts.id(); }
    public CertificateScope scope() { return context.scope(); }
    public Instant evaluatedAt() { return evaluatedAt; }
    public CertificationPolicySnapshot policy() { return policy; }
    public List<IssuanceBlocker> blockers() { return blockers; }
    public Optional<CertificateMode> mode() { return mode; }

    private record InspectionFacts(InspectionId id, AssetId assetId, Optional<AssetSnapshot> asset,
            Optional<SchemaVersionId> schema, InspectionStatus status, Optional<Instant> startedAt,
            Optional<Instant> closedAt, Map<CriterionId, CriterionEvaluation> evaluations,
            List<Rectification> rectifications) {
        static InspectionFacts of(Inspection inspection) {
            return new InspectionFacts(inspection.id(), inspection.assetId(), inspection.assetSnapshot(),
                    inspection.frozenSchemaVersionId(), inspection.status(), inspection.startedAt(), inspection.closedAt(),
                    Map.copyOf(inspection.currentEvaluations()), List.copyOf(inspection.rectifications()));
        }
    }
}
