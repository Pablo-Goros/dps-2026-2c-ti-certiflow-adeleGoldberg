package ar.edu.itba.dps.certification.app.web.dto;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaDraft;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceRequirement;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.schema.rule.EvaluationRule;
import ar.edu.itba.dps.certification.domain.schema.rule.MappedOptionsRule;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericBand;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericRangeRule;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.schema.rule.YesNoRule;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON shapes of the inspection schemas. Rules are a tagged flat record ({@code type} says which
 * fields apply) so the payload stays plain JSON without class names leaking from the core.
 */
public final class SchemaDtos {

    private SchemaDtos() {
    }

    public record Outcome(String code, CriterionResult result, Severity severity, String description) {

        static Outcome of(RuleOutcome outcome) {
            return new Outcome(outcome.code(), outcome.result(), outcome.severity(), outcome.description());
        }

        RuleOutcome toDomain() {
            return new RuleOutcome(code, result, severity, description);
        }
    }

    public record Band(BigDecimal lower, boolean lowerInclusive, BigDecimal upper, boolean upperInclusive,
            Outcome outcome) {

        static Band of(NumericBand band) {
            return new Band(band.lower(), band.lowerInclusive(), band.upper(), band.upperInclusive(),
                    Outcome.of(band.outcome()));
        }

        NumericBand toDomain() {
            Validate.required(outcome, "band outcome");
            return new NumericBand(lower, lowerInclusive, upper, upperInclusive, outcome.toDomain());
        }
    }

    /** {@code type} is YES_NO (uses yes/no), OPTIONS (options) or NUMERIC_RANGE (unit, minimum, maximum, bands). */
    public record Rule(String type, Outcome yes, Outcome no, Map<String, Outcome> options, String unit,
            BigDecimal minimum, BigDecimal maximum, List<Band> bands) {

        static Rule of(EvaluationRule rule) {
            if (rule instanceof YesNoRule yesNo) {
                return new Rule("YES_NO", Outcome.of(yesNo.whenAffirmative()), Outcome.of(yesNo.whenNegative()),
                        null, null, null, null, null);
            }
            if (rule instanceof MappedOptionsRule mapped) {
                Map<String, Outcome> options = new LinkedHashMap<>();
                mapped.options().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> options.put(entry.getKey(), Outcome.of(entry.getValue())));
                return new Rule("OPTIONS", null, null, options, null, null, null, null);
            }
            if (rule instanceof NumericRangeRule numeric) {
                return new Rule("NUMERIC_RANGE", null, null, null, numeric.unit(), numeric.domainMinimum(),
                        numeric.domainMaximum(), numeric.bands().stream().map(Band::of).toList());
            }
            throw new IllegalStateException("unsupported rule " + rule.getClass().getSimpleName());
        }

        EvaluationRule toDomain() {
            Validate.required(type, "rule type");
            return switch (type) {
                case "YES_NO" -> {
                    Validate.required(yes, "outcome for yes");
                    Validate.required(no, "outcome for no");
                    yield new YesNoRule(yes.toDomain(), no.toDomain());
                }
                case "OPTIONS" -> {
                    Validate.required(options, "option outcomes");
                    Map<String, RuleOutcome> outcomes = new LinkedHashMap<>();
                    options.forEach((key, outcome) -> outcomes.put(key, Validate.required(outcome, "option outcome").toDomain()));
                    yield new MappedOptionsRule(outcomes);
                }
                case "NUMERIC_RANGE" -> {
                    Validate.required(bands, "bands");
                    yield new NumericRangeRule(unit, minimum, maximum,
                            bands.stream().map(Band::toDomain).toList());
                }
                default -> throw new InvalidArgumentException(
                        "rule type must be YES_NO, OPTIONS or NUMERIC_RANGE, not " + type);
            };
        }
    }

    public record Evidence(EvidenceType type, String label, boolean mandatory, int minimumCount) {

        static Evidence of(EvidenceRequirement requirement) {
            return new Evidence(requirement.type(), requirement.label(), requirement.mandatory(),
                    requirement.minimumCount());
        }

        EvidenceRequirement toDomain() {
            return new EvidenceRequirement(type, label, mandatory, minimumCount);
        }
    }

    /** {@code subsystem} is optional: a criterion without one weighs on every subsystem. */
    public record CriterionDto(String id, Rule rule, String subsystem, List<Evidence> evidence) {

        static CriterionDto of(Criterion criterion) {
            return new CriterionDto(criterion.id().value(), Rule.of(criterion.rule()),
                    criterion.subsystem().map(Subsystem::name).orElse(null),
                    criterion.evidenceRequirements().stream().map(Evidence::of).toList());
        }

        Criterion toDomain() {
            Validate.required(rule, "criterion rule");
            List<EvidenceRequirement> requirements = evidence == null ? List.of()
                    : evidence.stream().map(Evidence::toDomain).toList();
            return new Criterion(CriterionId.of(id), rule.toDomain(), requirements,
                    java.util.Optional.ofNullable(subsystem).map(Subsystem::of));
        }
    }

    public record SectionDto(String name, int order, List<CriterionDto> criteria) {

        static SectionDto of(Section section) {
            return new SectionDto(section.name(), section.order(),
                    section.criteria().stream().map(CriterionDto::of).toList());
        }

        public Section toDomain() {
            Validate.required(criteria, "criteria");
            return new Section(name, order, criteria.stream().map(CriterionDto::toDomain).toList());
        }
    }

    public record VersionDto(String id, int number, Instant publishedAt, Instant effectiveFrom,
            List<SectionDto> sections) {

        public static VersionDto of(SchemaVersion version) {
            return new VersionDto(version.id().toString(), version.number(), version.publishedAt(),
                    version.effectiveFrom(), version.sections().stream().map(SectionDto::of).toList());
        }
    }

    public record DraftDto(List<SectionDto> sections) {

        static DraftDto of(SchemaDraft draft) {
            return new DraftDto(draft.sections().stream().map(SectionDto::of).toList());
        }
    }

    /**
     * {@code draft} is null when no draft is open; {@code effectiveVersion} is the number of the
     * version in force right now (a future-dated version is listed but not yet effective).
     */
    public record SchemaResponse(String id, String name, List<AssetType> assetTypes, DraftDto draft,
            List<VersionDto> versions, Integer effectiveVersion) {

        public static SchemaResponse of(InspectionSchema schema, Instant now) {
            return new SchemaResponse(
                    schema.id().value(),
                    schema.name(),
                    schema.applicableAssetTypes().stream().sorted().toList(),
                    schema.draft().map(DraftDto::of).orElse(null),
                    schema.publishedVersions().stream().map(VersionDto::of).toList(),
                    schema.effectiveVersionAt(now).map(version -> version.number()).orElse(null));
        }
    }

    public record CreateSchemaRequest(String name, Set<AssetType> assetTypes) {
    }

    /** {@code effectiveFrom} is optional; omitted means "from now". */
    public record PublishRequest(Instant effectiveFrom) {
    }

    public record ApplicabilityRequest(AssetType assetType) {
    }

    public record TransferRequest(String targetSchemaId, AssetType assetType) {
    }
}
