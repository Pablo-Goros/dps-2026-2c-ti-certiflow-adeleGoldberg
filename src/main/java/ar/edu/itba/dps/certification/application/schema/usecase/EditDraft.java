package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaDraft;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;

public final class EditDraft {

    private final SchemaRepository schemas;
    private final AuditRecorder audit;

    public EditDraft(SchemaRepository schemas, AuditRecorder audit) {
        this.schemas = schemas;
        this.audit = audit;
    }

    public SchemaDraft addSection(SchemaId schemaId, Section section) {
        InspectionSchema schema = schemas.require(schemaId);
        SchemaDraft draft = schema.requireDraft();
        draft.addSection(section);
        return audited(schema, draft, FieldChange.of("section." + section.name(), null,
                section.criteria().size() + " criteria"));
    }

    public SchemaDraft removeSection(SchemaId schemaId, String sectionName) {
        InspectionSchema schema = schemas.require(schemaId);
        SchemaDraft draft = schema.requireDraft();
        draft.removeSection(sectionName);
        return audited(schema, draft, FieldChange.of("section." + sectionName, "present", null));
    }

    private SchemaDraft audited(InspectionSchema schema, SchemaDraft draft, FieldChange change) {
        schemas.save(schema);
        audit.record(AuditedElementRef.schema(schema.id().value()),
                AuditAction.SCHEMA_DRAFT_EDITED, AuditDetail.dataChanged(change));
        return draft;
    }
}
