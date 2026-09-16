package ar.edu.itba.dps.certification.application.finding;

import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.time.LocalDate;
import java.util.List;

public final class RepositoryFindingQuery implements FindingQuery {

    private final FindingRepository findings;

    public RepositoryFindingQuery(FindingRepository findings) {
        this.findings = findings;
    }

    @Override
    public List<Finding> findingsOf(InspectionId inspectionId) {
        return findings.findByInspection(inspectionId);
    }

    @Override
    public List<Finding> unverifiedRejectionsOf(InspectionId inspectionId) {
        return findings.findByInspection(inspectionId).stream()
                .filter(Finding::blocksCertification)
                .toList();
    }

    @Override
    public List<Finding> overdueOpenActionsOf(InspectionId inspectionId, LocalDate today) {
        return findings.findByInspection(inspectionId).stream()
                .filter(finding -> !finding.obligationVoided())
                .filter(finding -> finding.correctiveAction().overdueAndOpen(today))
                .toList();
    }

    @Override
    public List<Finding> unplannedActionsOf(InspectionId inspectionId) {
        return findings.findByInspection(inspectionId).stream()
                .filter(finding -> !finding.obligationVoided())
                .filter(finding -> finding.correctiveAction().awaitingPlan())
                .toList();
    }
}
