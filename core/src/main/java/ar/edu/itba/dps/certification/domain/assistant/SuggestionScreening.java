package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the domain accepts from an assistant's answer and what it refuses, with the reason. A
 * consultation keeps both; the evaluation harness counts the refused ones as invented, so it applies
 * exactly the rule the application applies.
 */
public final class SuggestionScreening {

    private SuggestionScreening() {
    }

    /** The findings kept, in the order the assistant gave them, and the ones refused with their reason. */
    public record Screened(List<SuggestedFinding> accepted, List<RejectedSuggestion> rejected) {

        public Screened {
            accepted = List.copyOf(accepted);
            rejected = List.copyOf(rejected);
        }
    }

    public static Screened screen(AssistanceRequest request, List<SuggestedFinding> findings) {
        Validate.required(request, "assistance request");
        Validate.required(findings, "suggested findings");
        List<SuggestedFinding> accepted = new ArrayList<>();
        List<RejectedSuggestion> rejected = new ArrayList<>();
        Set<CriterionId> suggested = new HashSet<>();
        for (SuggestedFinding finding : findings) {
            problemWith(request, suggested, finding).ifPresentOrElse(
                    problem -> rejected.add(new RejectedSuggestion(finding, problem)),
                    () -> {
                        accepted.add(finding);
                        suggested.add(finding.criterionId());
                    });
        }
        return new Screened(accepted, rejected);
    }

    private static Optional<String> problemWith(AssistanceRequest request, Set<CriterionId> suggested,
            SuggestedFinding finding) {
        if (request.criterion(finding.criterionId()).isEmpty()) {
            return Optional.of("criterion " + finding.criterionId() + " was not part of the consultation");
        }
        if (finding.result().approved()) {
            return Optional.of("an approving result is not a finding");
        }
        if (finding.description().isBlank() || finding.rationale().isBlank()) {
            return Optional.of("a suggestion needs a description and a rationale");
        }
        if (suggested.contains(finding.criterionId())) {
            return Optional.of("criterion " + finding.criterionId() + " was already suggested in this consultation");
        }
        return Optional.empty();
    }
}
