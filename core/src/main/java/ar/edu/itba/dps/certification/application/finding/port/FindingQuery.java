package ar.edu.itba.dps.certification.application.finding.port;

import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.List;

public interface FindingQuery {

    List<Finding> findingsOf(InspectionId inspectionId);
}
