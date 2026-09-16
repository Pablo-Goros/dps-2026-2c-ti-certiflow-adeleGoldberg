package ar.edu.itba.dps.certification.domain.inspection.rectification;

import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

public sealed interface Correction {

    record AnswerCorrection(CriterionId criterionId, Answer answer) implements Correction {

        public AnswerCorrection {
            Validate.required(criterionId, "criterion id");
            Validate.required(answer, "corrected answer");
        }
    }

    record EvidenceReferenceCorrection(CriterionId criterionId, String evidenceId, String reference)
            implements Correction {

        public EvidenceReferenceCorrection {
            Validate.required(criterionId, "criterion id");
            evidenceId = Validate.requiredText(evidenceId, "evidence id");
            reference = Validate.requiredText(reference, "corrected evidence reference");
        }
    }

    record NoteCorrection(String noteId, String text) implements Correction {

        public NoteCorrection {
            noteId = Validate.requiredText(noteId, "note id");
            text = Validate.requiredText(text, "corrected note text");
        }
    }
}
