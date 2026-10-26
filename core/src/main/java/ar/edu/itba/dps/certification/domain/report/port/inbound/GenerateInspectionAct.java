package ar.edu.itba.dps.certification.domain.report.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.report.InspectionAct;

/** Input contract implemented by the application use case. */
public interface GenerateInspectionAct {
    InspectionAct generate(InspectionId inspectionId);
}
