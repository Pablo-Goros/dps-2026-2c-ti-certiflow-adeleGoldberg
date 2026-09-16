package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

public sealed interface IssuanceBlocker {

    String describe();

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
