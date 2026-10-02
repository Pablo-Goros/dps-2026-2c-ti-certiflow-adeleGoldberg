package ar.edu.itba.dps.certification.application.finding.port;

import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;

import java.util.List;
import java.util.Optional;

public interface FindingRepository {

    void save(Finding finding);

    Optional<Finding> findById(FindingId id);

    default Finding require(FindingId id) {
        return findById(id).orElseThrow(() -> new DomainException("finding " + id + " does not exist"));
    }

    Optional<Finding> findByCriterion(InspectionId inspectionId, CriterionId criterionId);

    List<Finding> findByInspection(InspectionId inspectionId);

    List<Finding> findWithOpenActions();

    List<Finding> findAll();
}
