package ar.edu.itba.dps.certification.application.finding.usecase;

import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.List;
import java.util.Optional;

/** Read side of the findings and their corrective actions. */
public final class BrowseFindings {

    private final FindingRepository findings;

    public BrowseFindings(FindingRepository findings) {
        this.findings = findings;
    }

    public Optional<Finding> find(FindingId id) {
        return findings.findById(id);
    }

    /**
     * Findings of one inspection, or (when {@code openActionsOnly}) those with an open corrective
     * action, or all of them; optionally narrowed to one asset.
     */
    public List<Finding> search(Optional<InspectionId> inspection, Optional<AssetId> asset,
            boolean openActionsOnly) {
        List<Finding> found = inspection.isPresent()
                ? findings.findByInspection(inspection.get())
                : openActionsOnly ? findings.findWithOpenActions() : findings.findAll();
        return found.stream()
                .filter(finding -> asset.map(finding.assetId()::equals).orElse(true))
                .toList();
    }
}
