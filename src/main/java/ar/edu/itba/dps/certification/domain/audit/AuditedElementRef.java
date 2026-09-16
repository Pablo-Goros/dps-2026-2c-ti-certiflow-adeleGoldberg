package ar.edu.itba.dps.certification.domain.audit;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record AuditedElementRef(ElementType type, String id) {

    public enum ElementType {
        ASSET, SCHEMA, INSPECTION, FINDING, CORRECTIVE_ACTION, CERTIFICATE
    }

    public AuditedElementRef {
        Validate.required(type, "audited element type");
        id = Validate.requiredText(id, "audited element id");
    }

    public static AuditedElementRef asset(String id) {
        return new AuditedElementRef(ElementType.ASSET, id);
    }

    public static AuditedElementRef schema(String id) {
        return new AuditedElementRef(ElementType.SCHEMA, id);
    }

    public static AuditedElementRef inspection(String id) {
        return new AuditedElementRef(ElementType.INSPECTION, id);
    }

    public static AuditedElementRef finding(String id) {
        return new AuditedElementRef(ElementType.FINDING, id);
    }

    public static AuditedElementRef correctiveAction(String id) {
        return new AuditedElementRef(ElementType.CORRECTIVE_ACTION, id);
    }

    public static AuditedElementRef certificate(String id) {
        return new AuditedElementRef(ElementType.CERTIFICATE, id);
    }

    @Override
    public String toString() {
        return type + ":" + id;
    }
}
