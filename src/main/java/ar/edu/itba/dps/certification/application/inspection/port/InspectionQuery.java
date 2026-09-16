package ar.edu.itba.dps.certification.application.inspection.port;

import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;

import java.util.Optional;

public interface InspectionQuery {

    Optional<Inspection> findById(InspectionId id);

    InspectionSummary summaryOf(InspectionId id);

    boolean wasRectified(InspectionId id);

    default Inspection require(InspectionId id) {
        return findById(id)
                .orElseThrow(() -> new DomainException("inspection " + id + " does not exist"));
    }
}
