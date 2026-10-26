package ar.edu.itba.dps.certification.domain.finding.port.inbound;

import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.LocalDate;

/** Input contract implemented by the application use case. */
public interface PlanCorrectiveAction {
    Finding plan(FindingId findingId, String work, PartyId executor, LocalDate dueDate);
}
