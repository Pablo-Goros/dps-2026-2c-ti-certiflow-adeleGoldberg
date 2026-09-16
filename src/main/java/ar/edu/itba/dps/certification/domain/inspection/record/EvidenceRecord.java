package ar.edu.itba.dps.certification.domain.inspection.record;

import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;

public record EvidenceRecord(
        String id,
        String requirementLabel,
        EvidenceType type,
        String reference,
        Instant attachedAt) {

    public EvidenceRecord {
        id = Validate.requiredText(id, "evidence id");
        requirementLabel = Validate.requiredText(requirementLabel, "evidence requirement label");
        Validate.required(type, "evidence type");
        reference = Validate.requiredText(reference, "evidence reference");
        Validate.required(attachedAt, "evidence attachment instant");
    }

    public EvidenceRecord withReference(String newReference) {
        return new EvidenceRecord(id, requirementLabel, type, newReference, attachedAt);
    }
}
