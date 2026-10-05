package ar.edu.itba.dps.certification.domain.certification.derivation;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.ResponsiblePartyRef;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateIssuer;
import ar.edu.itba.dps.certification.domain.certification.CertificateLifecycle;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionExpired;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.support.DomainWorld;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AllSubsystemsMustBeInForceTest {

    private static final Subsystem ELECTRICAL = Subsystem.of("electrical installation");
    private static final Subsystem PRESSURE = Subsystem.of("pressure system");

    private static final Instant STARTED_AT = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant IN_A_YEAR = Instant.parse("2027-03-01T10:00:00Z");

    private static final AssetId ASSET = AssetId.of("asset-1");
    private static final InspectionId INSPECTION = InspectionId.of("inspection-1");
    private static final SchemaVersionId VERSION = new SchemaVersionId(SchemaId.of("schema-1"), 1);

    private final AllSubsystemsMustBeInForce policy = new AllSubsystemsMustBeInForce();
    private final CertificateIssuer issuer = new CertificateIssuer();
    private final CertificateLifecycle lifecycle = new CertificateLifecycle();

    private enum PartialState { IN_FORCE, MISSING, SUSPENDED, EXPIRED }

    static Stream<Arguments> derivationScenarios() {
        return Stream.of(
                Arguments.of("every subsystem in force", PartialState.IN_FORCE,
                        PartialState.IN_FORCE, true, ""),
                Arguments.of("one subsystem never certified", PartialState.IN_FORCE,
                        PartialState.MISSING, false, "pressure system has no certificate"),
                Arguments.of("one subsystem suspended", PartialState.IN_FORCE,
                        PartialState.SUSPENDED, false, "pressure system is suspended"),
                Arguments.of("one subsystem expired", PartialState.EXPIRED,
                        PartialState.IN_FORCE, false, "electrical installation is expired"),
                Arguments.of("no subsystem certified at all", PartialState.MISSING,
                        PartialState.MISSING, false, "has no certificate"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("derivationScenarios")
    @DisplayName("a global certificate exists only while every subsystem holds one in force")
    void globalCertificateRequiresEverySubsystem(String scenario, PartialState electrical,
            PartialState pressure, boolean derivable, String expectedReason) {
        Inspection inspection = inspectionDeclaring(ELECTRICAL, PRESSURE);
        List<Certificate> partials = new ArrayList<>();
        partialIn(electrical, ELECTRICAL).ifPresent(partials::add);
        partialIn(pressure, PRESSURE).ifPresent(partials::add);

        GlobalCertificateDerivation derivation =
                policy.deriveFrom(new GlobalDerivationContext(inspection, partials, NOW));

        assertThat(derivation.derivedCertificate().isPresent()).isEqualTo(derivable);
        if (!derivable) {
            assertThat(derivation.describe()).contains(expectedReason);
        }
    }

    @Test
    @DisplayName("the derived validity is the window where every partial is simultaneously valid")
    void theDerivedValidityIsTheIntersectionOfThePartials() {
        Inspection inspection = inspectionDeclaring(ELECTRICAL, PRESSURE);
        Instant laterStart = Instant.parse("2026-04-01T10:00:00Z");
        Instant earlierEnd = Instant.parse("2026-12-01T10:00:00Z");
        List<Certificate> partials = List.of(
                certificate(ELECTRICAL, STARTED_AT, IN_A_YEAR),
                certificate(PRESSURE, laterStart, earlierEnd));

        GlobalCertificateDerivation derivation =
                policy.deriveFrom(new GlobalDerivationContext(inspection, partials, NOW));

        GlobalCertificate global = derivation.derivedCertificate().orElseThrow();
        assertThat(global.validity().issuedAt()).isEqualTo(laterStart);
        assertThat(global.validity().expiresAt()).isEqualTo(earlierEnd);
        assertThat(global.coveredSubsystems()).containsExactly(ELECTRICAL, PRESSURE);
        assertThat(global.derivedFrom()).hasSize(2);
    }

    @Test
    @DisplayName("a partial still outside its validity period does not certify its subsystem yet")
    void aPartialOutsideItsValidityPeriodDoesNotCount() {
        Inspection inspection = inspectionDeclaring(ELECTRICAL, PRESSURE);
        Instant startsLater = Instant.parse("2026-07-01T10:00:00Z");
        List<Certificate> partials = List.of(
                certificate(ELECTRICAL, STARTED_AT, IN_A_YEAR),
                certificate(PRESSURE, startsLater, IN_A_YEAR));

        GlobalCertificateDerivation derivation =
                policy.deriveFrom(new GlobalDerivationContext(inspection, partials, NOW));

        assertThat(derivation.derivedCertificate()).isEmpty();
        assertThat(derivation.describe()).contains("pressure system is outside its validity period");
    }

    @Test
    @DisplayName("an asset certified as a whole has no global certificate to derive")
    void aSchemaWithoutSubsystemsDerivesNothing() {
        Inspection inspection = inspectionDeclaring();

        GlobalCertificateDerivation derivation =
                policy.deriveFrom(new GlobalDerivationContext(inspection, List.of(), NOW));

        assertThat(derivation.derivedCertificate()).isEmpty();
        assertThat(derivation.describe()).contains("declares no part");
    }

    private java.util.Optional<Certificate> partialIn(PartialState state, Subsystem subsystem) {
        return switch (state) {
            case MISSING -> java.util.Optional.empty();
            case IN_FORCE -> java.util.Optional.of(certificate(subsystem, STARTED_AT, IN_A_YEAR));
            case EXPIRED -> {
                Certificate certificate = certificate(subsystem, STARTED_AT,
                        Instant.parse("2026-04-01T10:00:00Z"));
                certificate.expireIfDue(NOW);
                yield java.util.Optional.of(certificate);
            }
            case SUSPENDED -> {
                Certificate certificate = certificate(subsystem, STARTED_AT, IN_A_YEAR);
                lifecycle.apply(certificate, new CorrectiveActionExpired(INSPECTION,
                        FindingId.of("finding-1"), CorrectiveActionId.of("action-1"),
                        CriterionId.of("ELEC"), NOW));
                yield java.util.Optional.of(certificate);
            }
        };
    }

    private Certificate certificate(Subsystem subsystem, Instant from, Instant to) {
        return issuer.issue(CertificateId.of("cert-" + subsystem.name()), ASSET, INSPECTION, VERSION,
                CertificateScope.of(subsystem), new ValidityPeriod(from, to), null);
    }

    private Inspection inspectionDeclaring(Subsystem... subsystems) {
        List<Criterion> criteria = new ArrayList<>(Arrays.stream(subsystems)
                .map(subsystem -> Criterion.of(subsystem.name().toUpperCase(),
                        DomainWorld.housekeepingRule(), subsystem))
                .toList());
        if (criteria.isEmpty()) {
            criteria.add(Criterion.of("HK", DomainWorld.housekeepingRule()));
        }
        SchemaVersion version = new SchemaVersion(VERSION,
                List.of(Section.of("Safety", 1, criteria.toArray(Criterion[]::new))), STARTED_AT);

        PartyId inspector = PartyId.of("inspector-1");
        Inspection inspection = new Inspection(INSPECTION, ASSET, inspector,
                LocalDate.parse("2026-03-01"));
        inspection.start(inspector, version, snapshotHaving(subsystems), STARTED_AT);
        return inspection;
    }

    private AssetSnapshot snapshotHaving(Subsystem... subsystems) {
        if (subsystems.length == 0) {
            return new AssetSnapshot(ASSET, AssetType.LABORATORY, "Laboratory A", Map.of(),
                    "Building 1", new ResponsiblePartyRef(PartyId.of("owner-1"), "Owner"),
                    STARTED_AT);
        }
        return new AssetSnapshot(ASSET, AssetType.FACILITY, "Central Facility", Map.of(),
                "Building 1", new ResponsiblePartyRef(PartyId.of("owner-1"), "Owner"),
                Set.of(subsystems), STARTED_AT);
    }

    @Test
    @DisplayName("a subsystem the asset does not have is not required for its global certificate")
    void aSubsystemTheAssetLacksIsNotRequired() {
        SchemaVersion version = new SchemaVersion(VERSION, List.of(Section.of("Safety", 1,
                Criterion.of("ELEC", DomainWorld.housekeepingRule(), ELECTRICAL),
                Criterion.of("PRES", DomainWorld.housekeepingRule(), PRESSURE))), STARTED_AT);
        PartyId inspector = PartyId.of("inspector-1");
        Inspection inspection = new Inspection(INSPECTION, ASSET, inspector,
                LocalDate.parse("2026-03-01"));
        inspection.start(inspector, version, snapshotHaving(ELECTRICAL), STARTED_AT);

        GlobalCertificateDerivation derivation = policy.deriveFrom(new GlobalDerivationContext(
                inspection, List.of(certificate(ELECTRICAL, STARTED_AT, IN_A_YEAR)), NOW));

        GlobalCertificate global = derivation.derivedCertificate().orElseThrow();
        assertThat(global.coveredSubsystems()).containsExactly(ELECTRICAL);
    }
}
