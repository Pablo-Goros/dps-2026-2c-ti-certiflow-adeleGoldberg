package ar.edu.itba.dps.certification.domain.schema.rule;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A measurement classified by contiguous bands over a declared range (for example a pressure gauge
 * reading 0 to 1200 kPa, approved up to 1000 and rejected above it).
 *
 * <p>The range is where the bands live, not what the inspector is allowed to report: a measurement
 * outside it is recorded like any other and evaluated with {@link #outOfRange()}, because refusing
 * to record the worst readings would leave the inspection unable to state what was measured.
 */
public final class NumericRangeRule implements EvaluationRule {

    private final String unit;
    private final BigDecimal rangeMinimum;
    private final BigDecimal rangeMaximum;
    private final List<NumericBand> bands;
    private final RuleOutcome outOfRange;

    public NumericRangeRule(String unit, BigDecimal rangeMinimum, BigDecimal rangeMaximum,
            List<NumericBand> bands, RuleOutcome outOfRange) {
        this.unit = Validate.requiredText(unit, "unit");
        this.rangeMinimum = Validate.required(rangeMinimum, "range minimum");
        this.rangeMaximum = Validate.required(rangeMaximum, "range maximum");
        this.bands = Validate.requiredNonEmpty(bands, "bands").stream()
                .sorted(Comparator.comparing(NumericBand::lower))
                .toList();
        this.outOfRange = Validate.required(outOfRange, "outcome for a measurement outside the range");
    }

    public String unit() {
        return unit;
    }

    public BigDecimal rangeMinimum() {
        return rangeMinimum;
    }

    public BigDecimal rangeMaximum() {
        return rangeMaximum;
    }

    public List<NumericBand> bands() {
        return bands;
    }

    /** What a measurement outside the declared range amounts to. */
    public RuleOutcome outOfRange() {
        return outOfRange;
    }

    public boolean withinRange(BigDecimal value) {
        return value.compareTo(rangeMinimum) >= 0 && value.compareTo(rangeMaximum) <= 0;
    }

    @Override
    public Optional<String> admissibilityViolation(Answer answer) {
        if (!(answer instanceof Measurement measurement)) {
            return Optional.of("a numeric measurement is required");
        }
        if (!measurement.unit().equals(unit)) {
            return Optional.of("measurement must be expressed in " + unit + ", but was "
                    + measurement.unit());
        }
        return Optional.empty();
    }

    @Override
    public RuleOutcome evaluate(Answer answer) {
        admissibilityViolation(answer).ifPresent(violation -> {
            throw new DomainException("cannot evaluate an inadmissible answer: " + violation);
        });
        BigDecimal value = ((Measurement) answer).value();
        // A published version has no gaps inside the range, so an unmatched value lies outside it.
        return bands.stream()
                .filter(band -> band.contains(value))
                .findFirst()
                .map(NumericBand::outcome)
                .orElse(outOfRange);
    }

    @Override
    public Set<RuleOutcome> declaredOutcomes() {
        Set<RuleOutcome> outcomes = new HashSet<>(bands.stream().map(NumericBand::outcome).toList());
        outcomes.add(outOfRange);
        return Set.copyOf(outcomes);
    }

    @Override
    public List<String> publicationViolations() {
        List<String> violations = new ArrayList<>(rangeIsNotEmpty());
        if (!violations.isEmpty()) {
            return violations;
        }
        violations.addAll(everyBandIsWellFormed());
        if (!violations.isEmpty()) {
            return violations;
        }
        violations.addAll(bandsSpanTheRange());
        violations.addAll(adjacentBandsMeetExactlyOnce());
        violations.addAll(outOfRangeIsNotApproving());
        return violations;
    }

    private List<String> rangeIsNotEmpty() {
        if (rangeMinimum.compareTo(rangeMaximum) < 0) {
            return List.of();
        }
        return List.of("declared range [" + rangeMinimum.toPlainString() + ", "
                + rangeMaximum.toPlainString() + "] is empty");
    }

    private List<String> everyBandIsWellFormed() {
        return bands.stream()
                .filter(band -> !band.wellFormed())
                .map(band -> "band " + band.describe() + " has its bounds inverted")
                .toList();
    }

    private List<String> bandsSpanTheRange() {
        List<String> violations = new ArrayList<>();
        NumericBand first = bands.getFirst();
        if (first.lower().compareTo(rangeMinimum) != 0 || !first.lowerInclusive()) {
            violations.add("bands must start at the declared minimum "
                    + rangeMinimum.toPlainString() + " inclusively, but start at " + first.describe());
        }
        NumericBand last = bands.getLast();
        if (last.upper().compareTo(rangeMaximum) != 0 || !last.upperInclusive()) {
            violations.add("bands must end at the declared maximum " + rangeMaximum.toPlainString()
                    + " inclusively, but end at " + last.describe());
        }
        return violations;
    }

    private List<String> outOfRangeIsNotApproving() {
        if (!outOfRange.result().approved()) {
            return List.of();
        }
        return List.of("a measurement outside the declared range ["
                + rangeMinimum.toPlainString() + ", " + rangeMaximum.toPlainString()
                + "] cannot be approved");
    }

    private List<String> adjacentBandsMeetExactlyOnce() {
        List<String> violations = new ArrayList<>();
        for (int i = 1; i < bands.size(); i++) {
            meetingViolation(bands.get(i - 1), bands.get(i)).ifPresent(violations::add);
        }
        return violations;
    }

    private Optional<String> meetingViolation(NumericBand previous, NumericBand current) {
        String pair = "bands " + previous.describe() + " and " + current.describe();
        int boundary = current.lower().compareTo(previous.upper());
        if (boundary < 0) {
            return Optional.of(pair + " overlap");
        }
        if (boundary > 0) {
            return Optional.of(pair + " leave a gap");
        }
        String edge = current.lower().toPlainString();
        if (previous.upperInclusive() && current.lowerInclusive()) {
            return Optional.of(pair + " both include " + edge);
        }
        if (!previous.upperInclusive() && !current.lowerInclusive()) {
            return Optional.of(pair + " both exclude " + edge);
        }
        return Optional.empty();
    }
}
