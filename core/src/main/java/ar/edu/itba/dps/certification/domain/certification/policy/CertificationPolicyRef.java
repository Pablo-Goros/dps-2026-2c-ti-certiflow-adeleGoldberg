package ar.edu.itba.dps.certification.domain.certification.policy;

import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

public record CertificationPolicyRef(JurisdictionId jurisdiction, String policyId, int revision) {
    public CertificationPolicyRef {
        Validate.required(jurisdiction, "jurisdiction");
        policyId = Validate.requiredText(policyId, "policy id");
        Validate.requiredPositive(revision, "policy revision");
    }
}
