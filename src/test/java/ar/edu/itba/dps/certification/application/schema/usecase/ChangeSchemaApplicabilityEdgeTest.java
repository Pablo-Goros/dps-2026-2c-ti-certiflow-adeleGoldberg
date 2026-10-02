package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaApplicability;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.support.InMemoryAuditTrail;
import ar.edu.itba.dps.certification.support.InMemorySchemaRepository;
import ar.edu.itba.dps.certification.support.FixedActor;
import ar.edu.itba.dps.certification.support.TestClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChangeSchemaApplicabilityEdgeTest {

    @Test
    @DisplayName("applying a schema to another free type saves and audits the change")
    void applyingToAFreeTypeSavesAndAuditsTheChange() {
        InMemorySchemaRepository schemas = new InMemorySchemaRepository();
        AuditRecorder audit = new AuditRecorder(new InMemoryAuditTrail(), new FixedActor("operator"),
                TestClock.at("2026-03-01T10:00:00Z"));
        InspectionSchema schema = new SchemaApplicability().create(SchemaId.of("schema-1"), "Inspection",
                Set.of(AssetType.LABORATORY), Set.of());
        schemas.save(schema);
        ChangeSchemaApplicability change = new ChangeSchemaApplicability(schemas,
                new SchemaApplicability(), audit);

        InspectionSchema updated = change.applyTo(schema.id(), AssetType.FACTORY);

        assertThat(updated.appliesTo(AssetType.FACTORY)).isTrue();
    }

    @Test
    @DisplayName("the stop-applying use case saves and audits when the domain service allows it")
    void stopApplyingSavesAndAuditsWhenAllowedByTheDomainService() {
        InMemorySchemaRepository schemas = new InMemorySchemaRepository();
        AuditRecorder audit = new AuditRecorder(new InMemoryAuditTrail(), new FixedActor("operator"),
                TestClock.at("2026-03-01T10:00:00Z"));
        InspectionSchema schema = new SchemaApplicability().create(SchemaId.of("schema-1"), "Inspection",
                Set.of(AssetType.LABORATORY), Set.of());
        schemas.save(schema);
        SchemaApplicability applicability = mock(SchemaApplicability.class);
        ChangeSchemaApplicability change = new ChangeSchemaApplicability(schemas, applicability, audit);

        InspectionSchema updated = change.stopApplyingTo(schema.id(), AssetType.LABORATORY);

        assertThat(updated).isSameAs(schema);
        verify(applicability).stopApplyingTo(schema, AssetType.LABORATORY);
    }
}
