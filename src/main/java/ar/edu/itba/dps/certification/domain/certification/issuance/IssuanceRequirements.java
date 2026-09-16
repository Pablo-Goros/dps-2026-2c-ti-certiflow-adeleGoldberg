package ar.edu.itba.dps.certification.domain.certification.issuance;

import java.util.List;
import java.util.Optional;

public final class IssuanceRequirements {

    private IssuanceRequirements() {
    }

    public static List<IssuanceRequirement> standard() {
        return List.of(
                new InspectionMustBeClosed(),
                new RejectionsMustBeCorrected(),
                new NoActionMayBeOverdue(),
                new EveryActionMustBePlanned(),
                new AssetMustNotHoldALiveCertificate());
    }

    public static final class InspectionMustBeClosed implements IssuanceRequirement {

        @Override
        public Optional<IssuanceBlocker> unmetBy(CertificationContext context) {
            return context.inspectionClosed()
                    ? Optional.empty()
                    : Optional.of(new IssuanceBlocker.InspectionNotClosed());
        }
    }

    public static final class RejectionsMustBeCorrected implements IssuanceRequirement {

        @Override
        public Optional<IssuanceBlocker> unmetBy(CertificationContext context) {
            return context.unverifiedRejections() == 0
                    ? Optional.empty()
                    : Optional.of(new IssuanceBlocker.UnverifiedRejection(
                            context.unverifiedRejections()));
        }
    }

    public static final class NoActionMayBeOverdue implements IssuanceRequirement {

        @Override
        public Optional<IssuanceBlocker> unmetBy(CertificationContext context) {
            return context.overdueOpenActions() == 0
                    ? Optional.empty()
                    : Optional.of(new IssuanceBlocker.OverdueOpenAction(context.overdueOpenActions()));
        }
    }

    public static final class EveryActionMustBePlanned implements IssuanceRequirement {

        @Override
        public Optional<IssuanceBlocker> unmetBy(CertificationContext context) {
            return context.unplannedActions() == 0
                    ? Optional.empty()
                    : Optional.of(new IssuanceBlocker.UnplannedAction(context.unplannedActions()));
        }
    }

    public static final class AssetMustNotHoldALiveCertificate implements IssuanceRequirement {

        @Override
        public Optional<IssuanceBlocker> unmetBy(CertificationContext context) {
            return context.nonExpiredCertificateOfAsset().map(IssuanceBlocker.AssetAlreadyCertified::new);
        }
    }
}
