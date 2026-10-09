package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;

public sealed interface IssuanceBlocker {

    String describe();

    record BlockingSeverity(CriterionId criterionId,
            CriterionResult result,
            Severity severity) implements IssuanceBlocker {
        public BlockingSeverity {
            Validate.required(criterionId, "criterion id");
            Validate.required(result, "result");
            Validate.required(severity, "severity");
            Validate.ensure(!result.approved(), "approved results do not block");
        }
        @Override public String describe() { return criterionId + " has blocking severity " + severity + " (" + result + ")"; }
    }
    record ConditionalNotAllowed(int count) implements IssuanceBlocker {
        public ConditionalNotAllowed { Validate.requiredPositive(count, "pending count"); }
        @Override public String describe() { return count + " pending nonconformities require a conditional certificate, forbidden by policy"; }
    }

    record InspectionNotClosed() implements IssuanceBlocker {

        @Override
        public String describe() {
            return "the backing inspection is not closed";
        }
    }

    record UnverifiedRejection(int count) implements IssuanceBlocker {

        @Override
        public String describe() {
            return count + " rejected criteria have no verified correction";
        }
    }

    record OverdueOpenAction(int count) implements IssuanceBlocker {

        @Override
        public String describe() {
            return count + " corrective actions are open and past their deadline";
        }
    }

    record UnplannedAction(int count) implements IssuanceBlocker {

        @Override
        public String describe() {
            return count + " corrective actions have not been planned with a deadline";
        }
    }

    record SupersededInspection(InspectionId laterInspectionId) implements IssuanceBlocker {
        public SupersededInspection {
            Validate.required(laterInspectionId, "later inspection id");
        }

        @Override
        public String describe() {
            return "the asset was inspected again later by inspection " + laterInspectionId
                    + ", so this inspection no longer describes its current state";
        }
    }

    record AssetAlreadyCertified(CertificateId certificateId) implements IssuanceBlocker {

        public AssetAlreadyCertified {
            Validate.required(certificateId, "certificate id");
        }

        @Override
        public String describe() {
            return "the asset already holds certificate " + certificateId
                    + ", which has not expired";
        }
    }
}
