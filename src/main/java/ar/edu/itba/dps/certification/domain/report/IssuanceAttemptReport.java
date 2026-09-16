package ar.edu.itba.dps.certification.domain.report;

import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;
import java.util.Optional;

public record IssuanceAttemptReport(
        InspectionId inspectionId,
        boolean certified,
        Optional<CertificateId> certificateId,
        List<String> blockingReasons) {

    public IssuanceAttemptReport {
        Validate.required(inspectionId, "inspection id");
        Validate.required(certificateId, "certificate id");
        blockingReasons = List.copyOf(Validate.required(blockingReasons, "blocking reasons"));
    }

    public static IssuanceAttemptReport certified(InspectionId inspectionId,
            CertificateId certificateId) {
        return new IssuanceAttemptReport(inspectionId, true, Optional.of(certificateId), List.of());
    }

    public static IssuanceAttemptReport blocked(InspectionId inspectionId, List<String> reasons) {
        return new IssuanceAttemptReport(inspectionId, false, Optional.empty(), reasons);
    }
}
