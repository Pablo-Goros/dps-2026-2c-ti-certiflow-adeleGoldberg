package ar.edu.itba.dps.certification.domain.finding.port.inbound;

import ar.edu.itba.dps.certification.domain.finding.Finding;

import java.util.List;

/** Input contract implemented by the application use case. */
public interface ExpireOverdueCorrectiveActions {
    List<Finding> sweep();
}
