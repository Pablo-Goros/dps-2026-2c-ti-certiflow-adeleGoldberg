package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;

public final class CertificateIssuancePolicy {

    private final List<IssuanceRequirement> requirements;

    public CertificateIssuancePolicy() {
        this(IssuanceRequirements.standard());
    }

    public CertificateIssuancePolicy(List<IssuanceRequirement> requirements) {
        this.requirements = Validate.requiredNonEmpty(requirements, "issuance requirements");
    }

    public List<IssuanceBlocker> blockersFor(CertificationContext context) {
        Validate.required(context, "certification context");
        return requirements.stream()
                .map(requirement -> requirement.unmetBy(context))
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}
