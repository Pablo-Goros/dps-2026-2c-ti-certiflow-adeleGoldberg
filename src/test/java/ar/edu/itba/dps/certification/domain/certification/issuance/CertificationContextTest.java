package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.ResponsiblePartyRef;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.PolicyFacts;
import ar.edu.itba.dps.certification.support.TestPolicies;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CertificationContextTest {
    @Test void factsAreScopedOnceWithTransversalCriteriaAndAbsentPartsExcluded() {
        var inspection = new Inspection(InspectionId.of("inspection"), AssetId.of("asset"), PolicyFacts.INSPECTOR, PolicyFacts.today());
        var version = new SchemaVersion(new SchemaVersionId(SchemaId.of("schema"), 1), List.of(
                Section.of("parts", 1, DomainWorld.electricalCriterion(), DomainWorld.pressureCriterion(), DomainWorld.buildingSafetyCriterion()),
                Section.of("common", 2, Criterion.of("COMMON", DomainWorld.housekeepingRule()))), PolicyFacts.AT);
        inspection.start(PolicyFacts.INSPECTOR, version, new AssetSnapshot(inspection.assetId(), AssetType.FACILITY, "Facility", Map.of(),
                "Here", new ResponsiblePartyRef(PolicyFacts.OWNER, "owner"), Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE),
                PolicyFacts.AT, TestPolicies.REFERENCE), PolicyFacts.AT);
        for (var id : List.of(DomainWorld.ELECTRICAL_WIRING, DomainWorld.PRESSURE_VALVES, CriterionId.of("COMMON"))) {
            inspection.recordAnswer(PolicyFacts.INSPECTOR, id, OptionAnswer.of("untidy"));
        }
        inspection.close(PolicyFacts.INSPECTOR, PolicyFacts.AT);
        var context = new CertificationContext(inspection, List.of(), PolicyFacts.today(), CertificateScope.of(DomainWorld.ELECTRICAL),
                Optional.empty(), Optional.empty());
        assertThat(context.pendingNonConformities()).extracting(CertificationContext.PendingNonConformity::criterionId)
                .containsExactlyInAnyOrder(DomainWorld.ELECTRICAL_WIRING, CriterionId.of("COMMON"));
        assertThat(context.unplannedActions()).isEqualTo(2);
        assertThat(inspection.currentEvaluations()).doesNotContainKey(DomainWorld.BUILDING_EXITS);
    }
    @Test void findingsFromAnotherInspectionAreRejected() {
        var facts = new PolicyFacts(CriterionResult.OBSERVED, Severity.LOW);
        var foreign = new Finding(FindingId.of("foreign"), InspectionId.of("other"), facts.finding.criterionId(), facts.finding.assetId(),
                PolicyFacts.OWNER, PolicyFacts.INSPECTOR, facts.finding.result(), facts.finding.reasons(), facts.finding.severity(),
                List.of(), CorrectiveActionId.of("foreign-action"), PolicyFacts.AT);
        assertThatThrownBy(() -> facts.context(PolicyFacts.today(), List.of(foreign))).hasMessageContaining("backing inspection");
    }
}
