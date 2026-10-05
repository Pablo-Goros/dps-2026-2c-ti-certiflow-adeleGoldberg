package ar.edu.itba.dps.certification.domain.certification.derivation;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record GlobalCertificate(
        AssetId assetId,
        InspectionId backingInspectionId,
        SchemaVersionId schemaVersionId,
        ValidityPeriod validity,
        Set<Subsystem> coveredSubsystems,
        List<CertificateId> derivedFrom,
        CertificateMode mode,
        List<PartialProvenance> provenance) {

    public record PartialProvenance(CertificateId certificateId, Subsystem subsystem,
            CertificationPolicySnapshot policy,
            CertificateMode mode) {
        public PartialProvenance {
            Validate.required(certificateId, "certificate id"); Validate.required(subsystem, "subsystem");
            Validate.required(policy, "policy"); Validate.required(mode, "mode");
        }
    }
    public GlobalCertificate {
        Validate.required(assetId, "asset id");
        Validate.required(backingInspectionId, "backing inspection id");
        Validate.required(schemaVersionId, "schema version id");
        Validate.required(validity, "validity period");
        coveredSubsystems = Collections.unmodifiableSet(new LinkedHashSet<>(
                Validate.requiredNonEmpty(coveredSubsystems, "covered subsystems")));
        derivedFrom = List.copyOf(
                Validate.requiredNonEmpty(derivedFrom, "certificates it is derived from"));
        Validate.ensure(coveredSubsystems.size() == derivedFrom.size(),
                "a global certificate rests on exactly one partial certificate per subsystem");
        Validate.required(mode, "mode"); provenance = List.copyOf(Validate.required(provenance, "provenance"));
        Validate.ensure(provenance.stream().map(PartialProvenance::certificateId).toList().equals(derivedFrom),
                "provenance must identify every backing certificate in order");
        Validate.ensure(provenance.stream().map(PartialProvenance::subsystem).collect(Collectors.toSet()).equals(coveredSubsystems),
                "provenance must cover exactly the declared subsystems");
        Validate.ensure((mode == CertificateMode.CONDITIONAL)
                == provenance.stream().anyMatch(p -> p.mode() == CertificateMode.CONDITIONAL),
                "global mode must match its partials");

    }

    public String describe() {
        return "asset " + assetId + " is certified as a whole over its "
                + coveredSubsystems.size() + (coveredSubsystems.size() == 1 ? " part" : " parts")
                + " until " + validity.expiresAt();
    }
}
