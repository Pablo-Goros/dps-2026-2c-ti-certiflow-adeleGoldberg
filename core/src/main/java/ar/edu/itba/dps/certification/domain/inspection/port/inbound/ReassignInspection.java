package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.LocalDate;

/** Input contract implemented by the application use case. */
public interface ReassignInspection {
    Inspection reassign(InspectionId inspectionId, PartyId newInspector,
            LocalDate newExpectedDate);
}
