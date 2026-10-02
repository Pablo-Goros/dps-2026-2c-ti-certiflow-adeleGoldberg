package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;

public final class CertificateIssuancePolicy {

    private final List<IssuanceRequirement> requirements;

    public CertificateIssuancePolicy() {
        this(List.of());
    }

    public CertificateIssuancePolicy(List<IssuanceRequirement> requirements) {
        Validate.required(requirements, "additional issuance requirements");
        var all = new java.util.ArrayList<>(IssuanceRequirements.standard());
        all.addAll(requirements);
        this.requirements = List.copyOf(all);
    }

    public List<IssuanceBlocker> blockersFor(CertificationContext context) {
        Validate.required(context, "certification context");
        return requirements.stream()
                .map(requirement -> requirement.unmetBy(context))
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}
