package ar.edu.itba.dps.certification.domain.certification.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationAssessment;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.List;

/** Input contract implemented by the application use case. */
public interface EvaluateIssuanceEligibility {
    CertificationAssessment assess(InspectionId inspectionId);

    CertificationAssessment assess(InspectionId inspectionId, Subsystem subsystem);

    List<IssuanceBlocker> blockersFor(InspectionId inspectionId);

    List<IssuanceBlocker> blockersFor(InspectionId inspectionId, Subsystem subsystem);
}
