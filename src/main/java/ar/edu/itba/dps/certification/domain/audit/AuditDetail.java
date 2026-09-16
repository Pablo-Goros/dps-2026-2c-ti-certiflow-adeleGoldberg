package ar.edu.itba.dps.certification.domain.audit;

import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;

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
