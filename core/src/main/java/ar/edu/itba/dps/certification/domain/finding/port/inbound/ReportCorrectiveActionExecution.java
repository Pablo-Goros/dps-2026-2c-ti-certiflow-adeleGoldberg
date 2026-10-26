package ar.edu.itba.dps.certification.domain.finding.port.inbound;

import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;

import java.util.List;

/** Input contract implemented by the application use case. */
public interface ReportCorrectiveActionExecution {
    Finding report(FindingId findingId, String statement, List<String> evidenceReferences);
}
