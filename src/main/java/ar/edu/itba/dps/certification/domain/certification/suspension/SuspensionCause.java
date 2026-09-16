package ar.edu.itba.dps.certification.domain.certification.suspension;

import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

public sealed interface SuspensionCause {

    String describe();

    record OverdueAction(CorrectiveActionId correctiveActionId) implements SuspensionCause {

        public OverdueAction {
            Validate.required(correctiveActionId, "corrective action id");
        }

        @Override
        public String describe() {
            return "corrective action " + correctiveActionId + " passed its deadline";
        }
    }

    record RectifiedRejection(CriterionId criterionId, RectificationId rectificationId)
            implements SuspensionCause {

        public RectifiedRejection {
            Validate.required(criterionId, "criterion id");
            Validate.required(rectificationId, "rectification id");
        }

        @Override
        public String describe() {
            return "criterion " + criterionId + " became rejected through rectification "
                    + rectificationId;
        }
    }
}
