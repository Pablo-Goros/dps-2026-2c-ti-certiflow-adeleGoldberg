package ar.edu.itba.dps.certification.domain.certification.derivation;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.ResponsiblePartyRef;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateIssuer;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.CertificationPlan;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.support.DomainWorld;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DerivationEdgeTest {

    private static final Subsystem ELECTRICAL = Subsystem.of("electrical installation");
    private static final Subsystem PRESSURE = Subsystem.of("pressure system");
    private static final AssetId ASSET = AssetId.of("asset-1");
    private static final InspectionId INSPECTION = InspectionId.of("inspection-1");
    private static final SchemaVersionId VERSION = new SchemaVersionId(SchemaId.of("schema-1"), 1);
    private static final Instant AT = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant IN_A_YEAR = Instant.parse("2027-03-01T10:00:00Z");

    private final CertificateIssuer issuer = new CertificateIssuer();

    @Test
    @DisplayName("a partial scope needs the subsystem it covers")
    void aPartialScopeNeedsItsSubsystem() {
        assertThatThrownBy(() -> new CertificateScope.Partial(null))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("subsystem");
    }

    @Test
    @DisplayName("certifying by parts with no part is not a state that can be built")
    void byPartsCannotBeEmpty() {
        assertThatThrownBy(() -> new CertificationPlan.ByParts(Set.of()))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("subsystems to certify");
    }

    @Test
    @DisplayName("the plan of a null set of parts is refused, unlike the plan of none")
    void theePlanNeedsASetOfParts() {
        assertThatThrownBy(() -> CertificationPlan.over(null))
                .isInstanceOf(InvalidArgumentException.class);
        assertThat(CertificationPlan.over(Set.of()))
                .isEqualTo(new CertificationPlan.AsAWhole());
    }

    @Test
    @DisplayName("a derivation that refuses has to say why")
    void aRefusalCarriesReasons() {
        assertThatThrownBy(() -> new GlobalCertificateDerivation.NotDerivable(List.of()))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("reasons");
    }

    @Test
    @DisplayName("a global certificate covers at least one part and names what backs it")
    void aGlobalCertificateNeedsBacking() {
        assertThatThrownBy(() -> new GlobalCertificate(ASSET, INSPECTION, VERSION,
                new ValidityPeriod(AT, IN_A_YEAR), Set.of(), List.of(CertificateId.of("c1"))))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("covered subsystems");

        assertThatThrownBy(() -> new GlobalCertificate(ASSET, INSPECTION, VERSION,
                new ValidityPeriod(AT, IN_A_YEAR), Set.of(ELECTRICAL), List.of()))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("derived from");
    }

    @Test
    @DisplayName("a global certificate rests on exactly one certificate per part")
    void oneCertificatePerPart() {
        assertThatThrownBy(() -> new GlobalCertificate(ASSET, INSPECTION, VERSION,
                new ValidityPeriod(AT, IN_A_YEAR), Set.of(ELECTRICAL, PRESSURE),
                List.of(CertificateId.of("c1"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("exactly one partial certificate per subsystem");
    }

    @Test
    @DisplayName("the context refuses a certificate backed by another inspection")
    void theContextRefusesAForeignCertificate() {
        Certificate foreign = issuer.issue(CertificateId.of("c1"), ASSET,
                InspectionId.of("another"), VERSION, CertificateScope.of(ELECTRICAL),
                new ValidityPeriod(AT, IN_A_YEAR), null);

        assertThatThrownBy(() -> new GlobalDerivationContext(startedInspection(), List.of(foreign), AT))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("backed by the inspection being derived");
    }

    @Test
    @DisplayName("the context refuses a global certificate among the partials it derives from")
    void theContextRefusesAGlobalAmongThePartials() {
        Certificate global = issuer.issue(CertificateId.of("c1"), ASSET, INSPECTION, VERSION,
                CertificateScope.global(), new ValidityPeriod(AT, IN_A_YEAR), null);

        assertThatThrownBy(() -> new GlobalDerivationContext(startedInspection(), List.of(global), AT))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("partial certificates only");
    }

    @Test
    @DisplayName("two certificates cannot cover the same part of one inspection")
    void twoCertificatesCannotCoverTheSamePart() {
        Certificate first = partial(ELECTRICAL, "c1");
        Certificate duplicate = partial(ELECTRICAL, "c2");

        assertThatThrownBy(() ->
                new GlobalDerivationContext(startedInspection(), List.of(first, duplicate), AT))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot cover the same subsystem");
    }

    @Test
    @DisplayName("the index holds one entry per part and is unmodifiable")
    void theIndexIsOneEntryPerPart() {
        var context = new GlobalDerivationContext(startedInspection(),
                List.of(partial(ELECTRICAL, "c1"), partial(PRESSURE, "c2")), AT);

        Map<Subsystem, Certificate> indexed = context.partialsBySubsystem();

        assertThat(indexed).containsOnlyKeys(ELECTRICAL, PRESSURE);
        assertThatThrownBy(() -> indexed.put(ELECTRICAL, partial(ELECTRICAL, "c3")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("the context needs an inspection, partials and a moment")
    void theContextNeedsItsFacts() {
        Inspection inspection = startedInspection();
        assertThatThrownBy(() -> new GlobalDerivationContext(null, List.of(), AT))
                .isInstanceOf(InvalidArgumentException.class);
        assertThatThrownBy(() -> new GlobalDerivationContext(inspection, null, AT))
                .isInstanceOf(InvalidArgumentException.class);
        assertThatThrownBy(() -> new GlobalDerivationContext(inspection, List.of(), null))
                .isInstanceOf(InvalidArgumentException.class);
    }

    @Test
    @DisplayName("the policy needs a context")
    void thePolicyNeedsAContext() {
        assertThatThrownBy(() -> new AllSubsystemsMustBeInForce().deriveFrom(null))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("derivation context");
    }

    @Test
    @DisplayName("a derived global certificate describes how many parts it rests on")
    void aDerivedGlobalDescribesItsParts() {
        GlobalCertificate one = new GlobalCertificate(ASSET, INSPECTION, VERSION,
                new ValidityPeriod(AT, IN_A_YEAR), Set.of(ELECTRICAL),
                List.of(CertificateId.of("c1")));
        GlobalCertificate two = new GlobalCertificate(ASSET, INSPECTION, VERSION,
                new ValidityPeriod(AT, IN_A_YEAR), Set.of(ELECTRICAL, PRESSURE),
                List.of(CertificateId.of("c1"), CertificateId.of("c2")));

        assertThat(one.describe()).contains("its 1 part until").doesNotContain("parts");
        assertThat(two.describe()).contains("its 2 parts until");
        assertThat(GlobalCertificateDerivation.derived(two).describe()).isEqualTo(two.describe());
    }

    private Certificate partial(Subsystem subsystem, String id) {
        return issuer.issue(CertificateId.of(id), ASSET, INSPECTION, VERSION,
                CertificateScope.of(subsystem), new ValidityPeriod(AT, IN_A_YEAR), null);
    }

    private Inspection startedInspection() {
        SchemaVersion version = new SchemaVersion(VERSION, List.of(Section.of("Safety", 1,
                Criterion.of("ELEC", DomainWorld.housekeepingRule(), ELECTRICAL),
                Criterion.of("PRES", DomainWorld.housekeepingRule(), PRESSURE))), AT);
        PartyId inspector = PartyId.of("inspector-1");
        Inspection inspection = new Inspection(INSPECTION, ASSET, inspector,
                LocalDate.parse("2026-03-01"));
        inspection.start(inspector, version, new AssetSnapshot(ASSET, AssetType.FACILITY, "Central",
                Map.of(), "B1", new ResponsiblePartyRef(PartyId.of("owner-1"), "Owner"),
                Set.of(ELECTRICAL, PRESSURE), AT), AT);
        return inspection;
    }
}
