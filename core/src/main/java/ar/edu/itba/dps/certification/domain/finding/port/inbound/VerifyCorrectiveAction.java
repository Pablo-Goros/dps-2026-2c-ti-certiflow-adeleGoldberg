package ar.edu.itba.dps.certification.domain.finding.port.inbound;

import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;

/** Input contract implemented by the application use case. */
public interface VerifyCorrectiveAction {
    Finding verify(FindingId findingId, boolean satisfactory, String reason);
}
