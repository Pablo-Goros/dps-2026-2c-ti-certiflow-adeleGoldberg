package ar.edu.itba.dps.certification.domain.certification.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

/** Input contract implemented by the application use case. */
public interface IssueCertificate {
    IssuanceDecision issue(InspectionId inspectionId);

    IssuanceDecision issuePartial(InspectionId inspectionId, Subsystem subsystem);
}
