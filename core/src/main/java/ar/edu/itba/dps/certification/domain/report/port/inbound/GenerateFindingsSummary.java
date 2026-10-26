package ar.edu.itba.dps.certification.domain.report.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.report.FindingsSummary;

/** Input contract implemented by the application use case. */
public interface GenerateFindingsSummary {
    FindingsSummary generate(InspectionId inspectionId);
}
