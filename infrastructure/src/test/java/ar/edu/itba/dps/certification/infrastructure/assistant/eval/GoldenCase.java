package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.util.List;

/**
 * One case of the golden set: exactly what the assistant sees, and the findings a careful inspector
 * expects from it. An empty list means the right answer is to suggest nothing.
 */
public record GoldenCase(String id, String summary, AssistanceRequest request, List<ExpectedFinding> expected) {

    public GoldenCase {
        expected = List.copyOf(expected);
    }

    public record ExpectedFinding(CriterionId criterionId, CriterionResult result, Severity severity) {

        @Override
        public String toString() {
            return criterionId + " " + result + " " + severity;
        }
    }

    /** A case whose right answer is no suggestion at all: what makes inventing measurable. */
    public boolean expectsNothing() {
        return expected.isEmpty();
    }
}
