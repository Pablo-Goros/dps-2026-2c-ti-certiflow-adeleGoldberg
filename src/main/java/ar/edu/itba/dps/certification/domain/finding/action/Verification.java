package ar.edu.itba.dps.certification.domain.finding.action;

import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;

public record Verification(
        boolean satisfactory,
        String reason,
        PartyId verifiedBy,
        Instant verifiedAt) {

    public Verification {
        reason = Validate.requiredText(reason, "verification reason");
        Validate.required(verifiedBy, "verifier");
        Validate.required(verifiedAt, "verification instant");
    }

    public static Verification satisfactory(String reason, PartyId verifiedBy, Instant verifiedAt) {
        return new Verification(true, reason, verifiedBy, verifiedAt);
    }
}
