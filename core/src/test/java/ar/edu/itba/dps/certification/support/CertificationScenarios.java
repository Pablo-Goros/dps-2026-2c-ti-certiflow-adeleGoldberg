package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.domain.catalogue.*;
import ar.edu.itba.dps.certification.domain.certification.*;
import ar.edu.itba.dps.certification.domain.certification.issuance.*;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.inspection.*;
import ar.edu.itba.dps.certification.domain.schema.*;
import ar.edu.itba.dps.certification.domain.schema.rule.*;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.*;
import java.util.*;

/** Real, answered and closed inspections; certificates always pass through domain issuance. */
public final class CertificationScenarios {
    private CertificationScenarios() { }

    public static Inspection approvedInspection(InspectionId id, AssetId asset, SchemaVersionId versionId,
            Set<Subsystem> subsystems, Instant at) {
        var rule = new MappedOptionsRule(Map.of("yes", RuleOutcome.approved("OK", "approved")));
        var criteria = subsystems.isEmpty() ? List.of(Criterion.of("criterion", rule))
                : subsystems.stream().map(part -> Criterion.of(part.name(), rule, part)).toList();
        var version = new SchemaVersion(versionId,
                List.of(Section.of("Safety", 1, criteria.toArray(Criterion[]::new))), at);
        var inspector = PartyId.of("inspector");
        var inspection = new Inspection(id, asset, inspector, at.atZone(ZoneOffset.UTC).toLocalDate());
        inspection.start(inspector, version, new AssetSnapshot(asset,
                subsystems.isEmpty() ? AssetType.LABORATORY : AssetType.FACILITY,
                "Asset", Map.of(), "Here", new ResponsiblePartyRef(PartyId.of("owner"), "Owner"),
                subsystems, at, TestPolicies.REFERENCE), at);
        criteria.forEach(criterion -> inspection.recordAnswer(inspector, criterion.id(), OptionAnswer.of("yes")));
        inspection.close(inspector, at);
        return inspection;
    }

    public static Certificate issue(CertificateId id, Inspection inspection, CertificateScope scope,
            CertificationPolicySnapshot policy, Instant at, Certificate previous) {
        var context = new CertificationContext(inspection, List.of(), at.atZone(ZoneOffset.UTC).toLocalDate(),
                scope, Optional.empty(), Optional.empty());
        return new CertificateIssuer().issue(id, inspection, CertificationAssessment.evaluate(context, at, policy), previous);
    }
}
