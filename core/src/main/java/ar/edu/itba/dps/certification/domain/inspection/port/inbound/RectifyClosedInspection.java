package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Rectification;

import java.util.List;

/** Input contract implemented by the application use case. */
public interface RectifyClosedInspection {
    Rectification rectify(InspectionId inspectionId, String reason, List<Correction> corrections);
}
