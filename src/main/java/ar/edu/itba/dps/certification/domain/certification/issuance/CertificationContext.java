package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Optional;

public record CertificationContext(
        InspectionId inspectionId,
        boolean inspectionClosed,
        int unverifiedRejections,
        int overdueOpenActions,
        int unplannedActions,
        Optional<CertificateId> certificateOfInspection,
        Optional<CertificateId> nonExpiredCertificateOfAsset) {

    public CertificationContext {
        Validate.required(inspectionId, "inspection id");
        Validate.required(certificateOfInspection, "certificate of inspection");
        Validate.required(nonExpiredCertificateOfAsset, "non expired certificate of asset");
    }
}
