package ar.edu.itba.dps.certification.domain.audit;

import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public sealed interface AuditDetail {

    record ElementCreated(String description) implements AuditDetail {

        public ElementCreated {
            description = Validate.requiredText(description, "created element description");
        }
    }

    record StateChanged(String previousState, String newState) implements AuditDetail {

        public StateChanged {
            previousState = Validate.requiredText(previousState, "previous state");
            newState = Validate.requiredText(newState, "new state");
        }
    }

    record DataChanged(List<FieldChange> changes) implements AuditDetail {

        public DataChanged {
            changes = Validate.requiredNonEmpty(changes, "audited field changes");
        }
    }

    record DecisionRecorded(String decision, String outcome) implements AuditDetail {

        public DecisionRecorded {
            decision = Validate.requiredText(decision, "decision");
            outcome = Validate.requiredText(outcome, "decision outcome");
        }
    }

    record CertificationDecision(String operation,
            InspectionId inspectionId,
            CertificateScope scope,
            CertificationPolicySnapshot policy,
            Optional<CertificateMode> mode,
            Optional<CertificateId> certificateId,
            List<IssuanceBlocker> blockers,
            Instant evaluatedAt,
            Optional<ValidityPeriod> validity) implements AuditDetail {
        public CertificationDecision {
            operation = Validate.requiredText(operation, "operation");
            Validate.required(evaluatedAt, "evaluation instant");
            Validate.required(validity, "validity");
            Validate.required(certificateId, "certificate id");
            Validate.ensure(validity.isPresent() == certificateId.isPresent(), "only issued decisions have validity");
            Validate.required(inspectionId, "inspection id");
            Validate.required(scope, "scope");
            Validate.required(policy, "policy");
            Validate.required(mode, "mode");
            blockers = List.copyOf(Validate.required(blockers, "blockers"));
            Validate.ensure(certificateId.isPresent() == blockers.isEmpty() && mode.isPresent() == blockers.isEmpty(),
                    "a certification decision either issued a certificate with a mode or contains blockers");
        }
    }

    record CertificateStateChanged(CertificateStatus previousState,
            CertificateStatus newState,
            CertificationPolicySnapshot policy, CertificateScope scope, CertificateMode mode,
            List<SuspensionCause> unresolvedCauses) implements AuditDetail {
        public CertificateStateChanged {
            Validate.required(previousState, "previous status");
            Validate.required(newState, "new status");
            Validate.required(policy, "policy");
            Validate.required(scope, "scope");
            Validate.required(mode, "mode");
            unresolvedCauses = List.copyOf(Validate.required(unresolvedCauses, "unresolved causes"));
        }
    }
    static AuditDetail certificateStateChanged(CertificateStatus previous,
            Certificate certificate) {
        return new CertificateStateChanged(previous, certificate.status(), certificate.policy(), certificate.scope(),
                certificate.mode(), certificate.unresolvedCauses());
    }

    record PolicyResolutionFailed(String operation,
            InspectionId inspectionId,
            CertificateScope scope, String error) implements AuditDetail {
        public PolicyResolutionFailed {
            operation = Validate.requiredText(operation, "operation");
            Validate.required(inspectionId, "inspection id");
            Validate.required(scope, "scope"); error = Validate.requiredText(error, "resolution error");
        }
    }

    static AuditDetail certification(String operation,
            IssuanceDecision decision) {
        return switch (decision) {
            case IssuanceDecision.Issued issued -> {
                var c = issued.certificate();
                yield new CertificationDecision(operation, c.backingInspectionId(), c.scope(), c.policy(),
                        Optional.of(c.mode()), Optional.of(c.id()), List.of(),
                        c.validity().issuedAt(), Optional.of(c.validity()));
            }
            case IssuanceDecision.Blocked blocked -> {
                var a = blocked.assessment();
                yield new CertificationDecision(operation, a.inspectionId(), a.scope(), a.policy(),
                        Optional.empty(), Optional.empty(), a.blockers(), a.evaluatedAt(), Optional.empty());
            }
            case IssuanceDecision.AlreadyIssued repeated ->
                    throw new DomainException("a repetition is not a new decision");
        };
    }

    static AuditDetail created(String description) {
        return new ElementCreated(description);
    }

    static AuditDetail stateChanged(Object previousState, Object newState) {
        return new StateChanged(String.valueOf(previousState), String.valueOf(newState));
    }

    static AuditDetail dataChanged(FieldChange... changes) {
        return new DataChanged(List.of(changes));
    }

    static AuditDetail dataChanged(List<FieldChange> changes) {
        return new DataChanged(changes);
    }

    static AuditDetail decision(String decision, String outcome) {
        return new DecisionRecorded(decision, outcome);
    }
}
