package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.ResponsiblePartyRef;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectionPlan;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.action.ExecutionReport;
import ar.edu.itba.dps.certification.domain.finding.action.Verification;
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
import ar.edu.itba.dps.certification.domain.schema.rule.MappedOptionsRule;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;

import java.time.*;
import java.util.*;

/** Real domain aggregates with fixed time, independent of application services and repositories. */
public final class PolicyFacts {
    public static final Instant AT = Instant.parse("2026-03-01T10:00:00Z");
    public static final PartyId INSPECTOR = PartyId.of("inspector"), OWNER = PartyId.of("owner");
    public static final CriterionId CRITERION = CriterionId.of("criterion");
    public final Inspection inspection;
    public final Finding finding;
    public PolicyFacts(CriterionResult result, Severity severity) {
        var outcome = result == CriterionResult.REJECTED ? RuleOutcome.rejected("BAD", severity, "bad")
                : result == CriterionResult.OBSERVED ? RuleOutcome.observed("BAD", severity, "bad")
                : RuleOutcome.approved("GOOD", "good");
        var criterion = new Criterion(CRITERION, new MappedOptionsRule(Map.of("answer", outcome)), List.of());
        var version = new SchemaVersion(new SchemaVersionId(SchemaId.of("schema"), 1),
                List.of(Section.of("section", 1, criterion)), AT);
        inspection = new Inspection(InspectionId.of("inspection"), AssetId.of("asset"), INSPECTOR, today());
        inspection.start(INSPECTOR, version, new AssetSnapshot(inspection.assetId(), AssetType.LABORATORY,
                "Lab", Map.of(), "Here", new ResponsiblePartyRef(OWNER, "owner"), AT, TestPolicies.REFERENCE), AT);
        inspection.recordAnswer(INSPECTOR, CRITERION, OptionAnswer.of("answer"));
        inspection.close(INSPECTOR, AT);
        var evaluation = inspection.currentEvaluations().get(CRITERION);
        finding = result.approved() ? null : new Finding(FindingId.of("finding"), inspection.id(), CRITERION,
                inspection.assetId(), OWNER, INSPECTOR, result, evaluation.reasons(), severity, List.of(),
                CorrectiveActionId.of("action"), AT);
    }
    public static LocalDate today() { return AT.atZone(ZoneOffset.UTC).toLocalDate(); }
    public void plan() { finding.planCorrection(OWNER, new CorrectionPlan("repair", OWNER, today()), today(), AT); }
    public void verify() {
        finding.reportCorrectionExecution(new ExecutionReport("done", List.of("proof"), OWNER, AT));
        finding.concludeCorrection(Verification.satisfactory("verified", INSPECTOR, AT), today());
    }
    public CertificationContext context() { return context(today(), finding == null ? List.of() : List.of(finding)); }
    public CertificationContext context(LocalDate date, List<Finding> findings) {
        return new CertificationContext(inspection, findings, date, CertificateScope.global(), Optional.empty(), Optional.empty());
    }
}
