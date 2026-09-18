package ar.edu.itba.dps.certification.domain.audit;

public enum AuditAction {

    ASSET_REGISTERED(false),
    ASSET_RESPONSIBLE_CHANGED(false),
    ASSET_RELOCATED(false),
    PARTY_REGISTERED(false),

    SCHEMA_CREATED(false),
    SCHEMA_APPLICABILITY_CHANGED(false),
    SCHEMA_DRAFT_OPENED(false),
    SCHEMA_DRAFT_EDITED(false),
    SCHEMA_DRAFT_DISCARDED(false),
    SCHEMA_VERSION_PUBLISHED(false),

    INSPECTION_ASSIGNED(false),
    INSPECTION_REASSIGNED(false),
    INSPECTION_STARTED(false),
    INSPECTION_DATA_RECORDED(false),
    INSPECTION_DATA_CORRECTED(false),
    INSPECTION_CLOSED(false),
    INSPECTION_RECTIFIED(true),

    FINDING_CREATED(false),
    FINDING_REVISED(true),
    FINDING_EVIDENCE_CORRECTED(true),
    FINDING_OBLIGATION_VOIDED(true),

    CORRECTIVE_ACTION_PLANNED(false),
    CORRECTIVE_ACTION_EXECUTION_REPORTED(false),
    CORRECTIVE_ACTION_VERIFIED(true),
    CORRECTIVE_ACTION_CLOSED(false),
    CORRECTIVE_ACTION_EXPIRED(false),
    CORRECTIVE_ACTION_VOIDED(true),

    CERTIFICATE_ISSUED(false),
    CERTIFICATE_ISSUANCE_BLOCKED(false),
    CERTIFICATE_SUSPENDED(true),
    CERTIFICATE_REACTIVATED(false),
    CERTIFICATE_RENEWED(false),
    CERTIFICATE_EXPIRED(false);

    private final boolean requiresReason;

    AuditAction(boolean requiresReason) {
        this.requiresReason = requiresReason;
    }

    public boolean requiresReason() {
        return requiresReason;
    }
}
