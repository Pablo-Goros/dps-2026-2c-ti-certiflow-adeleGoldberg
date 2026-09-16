package ar.edu.itba.dps.certification.application.finding.port;

import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.time.LocalDate;
import java.util.List;

public interface FindingQuery {

    List<Finding> findingsOf(InspectionId inspectionId);

    List<Finding> unverifiedRejectionsOf(InspectionId inspectionId);

    List<Finding> overdueOpenActionsOf(InspectionId inspectionId, LocalDate today);

    List<Finding> unplannedActionsOf(InspectionId inspectionId);
}
