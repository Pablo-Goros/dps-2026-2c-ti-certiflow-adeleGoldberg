package ar.edu.itba.dps.certification.adapter.finding;

import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

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
}
