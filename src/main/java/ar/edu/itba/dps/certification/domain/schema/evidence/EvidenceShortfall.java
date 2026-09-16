package ar.edu.itba.dps.certification.domain.schema.evidence;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record EvidenceShortfall(EvidenceType type, String label, int requiredCount, int presentedCount) {

    public EvidenceShortfall {
        Validate.required(type, "evidence type");
        label = Validate.requiredText(label, "evidence label");
    }

    public String describe() {
        return "required " + requiredCount + " " + type + " (" + label + "), presented " + presentedCount;
    }
}
