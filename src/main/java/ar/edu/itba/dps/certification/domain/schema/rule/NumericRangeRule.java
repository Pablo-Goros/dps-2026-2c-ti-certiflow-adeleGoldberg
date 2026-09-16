package ar.edu.itba.dps.certification.domain.schema.rule;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class NumericRangeRule implements EvaluationRule {

    private final String unit;
    private final BigDecimal domainMinimum;
    private final BigDecimal domainMaximum;
    private final List<NumericBand> bands;

    public NumericRangeRule(String unit, BigDecimal domainMinimum, BigDecimal domainMaximum,
            List<NumericBand> bands) {
        this.unit = Validate.requiredText(unit, "unit");
        this.domainMinimum = Validate.required(domainMinimum, "domain minimum");
        this.domainMaximum = Validate.required(domainMaximum, "domain maximum");
        this.bands = Validate.requiredNonEmpty(bands, "bands").stream()
                .sorted(Comparator.comparing(NumericBand::lower))
                .toList();
    }

    public String unit() {
        return unit;
    }

    public BigDecimal domainMinimum() {
        return domainMinimum;
    }

    public BigDecimal domainMaximum() {
        return domainMaximum;
    }

    public List<NumericBand> bands() {
        return bands;
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
        BigDecimal value = measurement.value();
        if (value.compareTo(domainMinimum) < 0 || value.compareTo(domainMaximum) > 0) {
            return Optional.of("measurement " + value.toPlainString() + " " + unit
                    + " is outside the admissible range [" + domainMinimum.toPlainString() + ", "
                    + domainMaximum.toPlainString() + "]");
        }
        return Optional.empty();
    }

    @Override
    public RuleOutcome evaluate(Answer answer) {
        admissibilityViolation(answer).ifPresent(violation -> {
            throw new DomainException("cannot evaluate an inadmissible answer: " + violation);
        });
        BigDecimal value = ((Measurement) answer).value();
        return bands.stream()
                .filter(band -> band.contains(value))
                .findFirst()
                .map(NumericBand::outcome)
                .orElseThrow(() -> new DomainException(
                        "no band matches " + value.toPlainString() + " " + unit));
    }

    @Override
    public Set<RuleOutcome> declaredOutcomes() {
        return bands.stream().map(NumericBand::outcome).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<String> publicationViolations() {
        List<String> violations = new ArrayList<>(admissibleDomainIsNotEmpty());
        if (!violations.isEmpty()) {
            return violations;
        }
        violations.addAll(everyBandIsWellFormed());
        if (!violations.isEmpty()) {
            return violations;
        }
        violations.addAll(bandsSpanTheAdmissibleDomain());
        violations.addAll(adjacentBandsMeetExactlyOnce());
        return violations;
    }

    private List<String> admissibleDomainIsNotEmpty() {
        if (domainMinimum.compareTo(domainMaximum) < 0) {
            return List.of();
        }
        return List.of("admissible domain [" + domainMinimum.toPlainString() + ", "
                + domainMaximum.toPlainString() + "] is empty");
    }

    private List<String> everyBandIsWellFormed() {
        return bands.stream()
                .filter(band -> !band.wellFormed())
                .map(band -> "band " + band.describe() + " has its bounds inverted")
                .toList();
    }

    private List<String> bandsSpanTheAdmissibleDomain() {
        List<String> violations = new ArrayList<>();
        NumericBand first = bands.getFirst();
        if (first.lower().compareTo(domainMinimum) != 0 || !first.lowerInclusive()) {
            violations.add("bands must start at the admissible minimum "
                    + domainMinimum.toPlainString() + " inclusively, but start at " + first.describe());
        }
        NumericBand last = bands.getLast();
        if (last.upper().compareTo(domainMaximum) != 0 || !last.upperInclusive()) {
            violations.add("bands must end at the admissible maximum " + domainMaximum.toPlainString()
                    + " inclusively, but end at " + last.describe());
        }
        return violations;
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
