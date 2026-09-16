package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryFindingRepository implements FindingRepository {

    private final Map<FindingId, Finding> stored = new LinkedHashMap<>();

    @Override
    public void save(Finding finding) {
        stored.put(finding.id(), finding);
    }

    @Override
    public Optional<Finding> findById(FindingId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Optional<Finding> findByCriterion(InspectionId inspectionId, CriterionId criterionId) {
        return stored.values().stream()
                .filter(finding -> finding.inspectionId().equals(inspectionId))
                .filter(finding -> finding.criterionId().equals(criterionId))
                .findFirst();
    }

    @Override
    public List<Finding> findByInspection(InspectionId inspectionId) {
        return stored.values().stream()
                .filter(finding -> finding.inspectionId().equals(inspectionId))
                .toList();
    }

    @Override
    public List<Finding> findWithOpenActions() {
        return stored.values().stream()
                .filter(finding -> finding.correctiveAction().status().open())
                .toList();
    }

    @Override
    public List<Finding> findAll() {
        return new ArrayList<>(stored.values());
    }
}
