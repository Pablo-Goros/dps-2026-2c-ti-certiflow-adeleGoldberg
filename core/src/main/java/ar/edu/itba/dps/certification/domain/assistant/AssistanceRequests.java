package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.evaluation.CriterionEvaluator;
import ar.edu.itba.dps.certification.domain.inspection.CriterionRecord;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.record.ObservedNonConformity;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Builds the assistant's input from an inspection in progress, always in the same order. */
public final class AssistanceRequests {

    private static final CriterionEvaluator EVALUATOR = new CriterionEvaluator();

    /** By code, then by the rest, so outcomes that share a code come in the same order on every run. */
    static final Comparator<OutcomeOption> OUTCOME_ORDER = Comparator.comparing(OutcomeOption::code)
            .thenComparing(OutcomeOption::result)
            .thenComparingInt(option -> option.severity().map(Severity::ordinal).orElse(-1))
            .thenComparing(OutcomeOption::description);

    private AssistanceRequests() {
    }

    public static AssistanceRequest of(Inspection inspection, SchemaVersion frozen, Instant at) {
        Validate.required(inspection, "inspection");
        Validate.required(frozen, "frozen schema version");
        Validate.required(at, "instant");
        Validate.ensure(inspection.frozenSchemaVersionId().filter(frozen.id()::equals).isPresent(),
                "schema version " + frozen.id() + " is not the one inspection " + inspection.id() + " froze");
        Validate.ensure(!inspection.notes().isEmpty(),
                "inspection " + inspection.id() + " has no notes for the assistant to read");
        AssetSnapshot asset = inspection.assetSnapshot().orElseThrow();
        List<CriterionContext> criteria = new ArrayList<>();
        for (Section section : frozen.sections()) {
            for (Criterion criterion : section.criteria()) {
                CriterionRecord record = inspection.requireRecord(criterion.id());
                if (record.applicable()) {
                    criteria.add(contextOf(section, criterion, record, at));
                }
            }
        }
        List<Observation> observations = inspection.notes().stream()
                .map(note -> new Observation(note.criterionId(), note.text()))
                .toList();
        return new AssistanceRequest(asset.assetType(), asset.subsystems(), criteria, observations);
    }

    private static CriterionContext contextOf(Section section, Criterion criterion, CriterionRecord record, Instant at) {
        Optional<Answer> answer = record.answer();
        return new CriterionContext(criterion.id(), section.name(), criterion.subsystem(),
                answer.map(Answer::describe),
                answer.map(given -> ProvisionalEvaluation.of(EVALUATOR.evaluate(criterion, record, at))),
                record.observations().stream().map(ObservedNonConformity::describe).toList(),
                record.evidence().stream()
                        .map(evidence -> evidence.type() + ": " + evidence.requirementLabel())
                        .sorted()
                        .toList(),
                criterion.rule().declaredOutcomes().stream()
                        .map(OutcomeOption::of)
                        .sorted(OUTCOME_ORDER)
                        .toList());
    }
}
