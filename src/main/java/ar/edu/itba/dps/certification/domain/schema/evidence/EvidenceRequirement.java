package ar.edu.itba.dps.certification.domain.schema.evidence;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record EvidenceRequirement(EvidenceType type, String label, boolean mandatory, int minimumCount) {

    public EvidenceRequirement {
        Validate.required(type, "evidence type");
        label = Validate.requiredText(label, "evidence requirement label");
        Validate.requiredPositive(minimumCount, "minimum evidence count");
    }

    public static EvidenceRequirement mandatory(EvidenceType type, String label) {
        return new EvidenceRequirement(type, label, true, 1);
    }

    public boolean satisfiedBy(int presentedCount) {
        return !mandatory || presentedCount >= minimumCount;
    }

    public EvidenceShortfall shortfall(int presentedCount) {
        return new EvidenceShortfall(type, label, minimumCount, presentedCount);
    }
}
