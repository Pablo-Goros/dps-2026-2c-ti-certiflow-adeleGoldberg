package ar.edu.itba.dps.certification.domain.finding.port;

import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.time.LocalDate;
import java.util.List;

public interface FindingQuery {

    List<Finding> findingsOf(InspectionId inspectionId);

    default List<Finding> unverifiedRejectionsOf(InspectionId inspectionId) {
        return findingsOf(inspectionId).stream().filter(Finding::blocksCertification).toList();
    }

    default List<Finding> overdueOpenActionsOf(InspectionId inspectionId, LocalDate today) {
        return findingsOf(inspectionId).stream().filter(f -> !f.obligationVoided())
                .filter(f -> f.correctiveAction().overdueAndOpen(today)).toList();
    }

    default List<Finding> unplannedActionsOf(InspectionId inspectionId) {
        return findingsOf(inspectionId).stream().filter(f -> !f.obligationVoided())
                .filter(f -> f.correctiveAction().awaitingPlan()).toList();
    }
}
