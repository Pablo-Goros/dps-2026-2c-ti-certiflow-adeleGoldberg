package ar.edu.itba.dps.certification.domain.certification.suspension;

import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Optional;

public sealed interface SuspensionCause {

    String describe();
    record NonConformity(CriterionId criterionId, Optional<CorrectiveActionId> correctiveActionId,
            Optional<RectificationId> rectificationId) implements SuspensionCause {
        public NonConformity {
            Validate.required(criterionId, "criterion id"); Validate.required(correctiveActionId, "action id");
            Validate.required(rectificationId, "rectification id");
        }
        @Override public String describe() { return "criterion " + criterionId + " no longer complies with the certificate policy"; }
    }

    record OverdueAction(CorrectiveActionId correctiveActionId) implements SuspensionCause {

        public OverdueAction {
            Validate.required(correctiveActionId, "corrective action id");
        }

        @Override
        public String describe() {
            return "corrective action " + correctiveActionId + " passed its deadline";
        }
    }

}
