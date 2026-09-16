package ar.edu.itba.dps.certification.domain.finding.action;

import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.List;

public record ExecutionReport(
        String statement,
        List<String> evidenceReferences,
        PartyId reportedBy,
        Instant reportedAt) {

    public ExecutionReport {
        statement = Validate.requiredText(statement, "execution statement");
        evidenceReferences = Validate.requiredNonEmpty(evidenceReferences, "execution evidence");
        Validate.required(reportedBy, "executor");
        Validate.required(reportedAt, "execution instant");
    }
}
