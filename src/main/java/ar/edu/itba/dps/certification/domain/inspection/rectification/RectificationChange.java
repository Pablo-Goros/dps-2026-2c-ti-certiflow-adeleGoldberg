package ar.edu.itba.dps.certification.domain.inspection.rectification;

import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Optional;

public sealed interface RectificationChange {

    Optional<CriterionId> affectedCriterion();

    String describe();

    record AnswerCorrected(CriterionId criterionId, String previousValue, String currentValue)
            implements RectificationChange {

        public AnswerCorrected {
            Validate.required(criterionId, "criterion id");
        }

        @Override
        public Optional<CriterionId> affectedCriterion() {
            return Optional.of(criterionId);
        }

        @Override
        public String describe() {
            return "answer of " + criterionId + ": " + previousValue + " -> " + currentValue;
        }
    }

    record EvidenceReferenceChanged(CriterionId criterionId, String evidenceId, String previousValue,
            String currentValue) implements RectificationChange {

        public EvidenceReferenceChanged {
            Validate.required(criterionId, "criterion id");
            evidenceId = Validate.requiredText(evidenceId, "evidence id");
        }

        @Override
        public Optional<CriterionId> affectedCriterion() {
            return Optional.of(criterionId);
        }

        @Override
        public String describe() {
            return "evidence " + evidenceId + " of " + criterionId + ": " + previousValue + " -> "
                    + currentValue;
        }
    }

    record NoteCorrected(String noteId, String previousValue, String currentValue)
            implements RectificationChange {

        public NoteCorrected {
            noteId = Validate.requiredText(noteId, "note id");
            previousValue = Validate.requiredText(previousValue, "previous note text");
            currentValue = Validate.requiredText(currentValue, "corrected note text");
        }

        @Override
        public Optional<CriterionId> affectedCriterion() {
            return Optional.empty();
        }

        @Override
        public String describe() {
            return "note " + noteId + ": " + previousValue + " -> " + currentValue;
        }
    }
}
