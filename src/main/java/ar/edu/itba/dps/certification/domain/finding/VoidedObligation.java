package ar.edu.itba.dps.certification.domain.finding;

import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;

public record VoidedObligation(RectificationId rectificationId, String reason, Instant voidedAt) {

    public VoidedObligation {
        Validate.required(rectificationId, "rectification id");
        reason = Validate.requiredText(reason, "voiding reason");
        Validate.required(voidedAt, "voiding instant");
    }
}
