package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Optional;

/** What the inspector decided. Accepting or editing names the observed non-conformity it recorded. */
public sealed interface SuggestionDecision {

    PartyId by();

    Instant at();

    String verdict();

    record Accepted(PartyId by, Instant at, String observationId) implements SuggestionDecision {

        public Accepted {
            Validate.required(by, "deciding inspector");
            Validate.required(at, "decision instant");
            observationId = Validate.requiredText(observationId, "observed non-conformity id");
        }

        @Override
        public String verdict() {
            return "ACCEPTED";
        }
    }

    record Edited(PartyId by, Instant at, String observationId, CriterionId criterionId, CriterionResult result,
            Severity severity, String description, String note) implements SuggestionDecision {

        public Edited {
            Validate.required(by, "deciding inspector");
            Validate.required(at, "decision instant");
            observationId = Validate.requiredText(observationId, "observed non-conformity id");
            Validate.required(criterionId, "criterion id");
            Validate.required(result, "edited result");
            Validate.ensure(!result.approved(), "an edited suggestion is still a non-conformity; discard it instead");
            Validate.required(severity, "edited severity");
            description = Validate.requiredText(description, "edited description");
            note = Validate.requiredText(note, "edit note");
        }

        @Override
        public String verdict() {
            return "EDITED";
        }
    }

    record Discarded(PartyId by, Instant at, Optional<String> reason) implements SuggestionDecision {

        public Discarded {
            Validate.required(by, "deciding inspector");
            Validate.required(at, "decision instant");
            reason = Validate.required(reason, "discard reason").map(text -> Validate.requiredText(text, "discard reason"));
        }

        @Override
        public String verdict() {
            return "DISCARDED";
        }
    }
}
