package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;
import java.util.Optional;

/**
 * One applicable criterion as the assistant sees it. {@code provisional} is what closing the
 * inspection now would conclude, with its severity and reasons, so the assistant compares it with the
 * notes instead of evaluating again; it is empty while the criterion has no answer.
 * {@code observedNonConformities} are the ones the inspector already recorded, so they are not
 * suggested again. {@code outcomes} describe what the rule measures and are context, not options to
 * choose from.
 */
public record CriterionContext(CriterionId id, String section, Optional<Subsystem> subsystem, Optional<String> answer,
        Optional<ProvisionalEvaluation> provisional, List<String> observedNonConformities, List<String> evidence,
        List<OutcomeOption> outcomes) {

    public CriterionContext {
        Validate.required(id, "criterion id");
        section = Validate.requiredText(section, "section");
        Validate.required(subsystem, "subsystem");
        Validate.required(answer, "answer");
        Validate.required(provisional, "provisional evaluation");
        observedNonConformities = List.copyOf(Validate.required(observedNonConformities, "observed non-conformities"));
        evidence = List.copyOf(Validate.required(evidence, "evidence"));
        outcomes = List.copyOf(Validate.requiredNonEmpty(outcomes, "rule outcomes"));
    }
}
