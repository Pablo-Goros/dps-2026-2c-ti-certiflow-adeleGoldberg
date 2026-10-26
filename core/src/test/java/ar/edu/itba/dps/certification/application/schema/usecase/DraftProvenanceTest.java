package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.support.DomainWorld;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DraftProvenanceTest {
    @Test
    void openingAfterWithdrawalAuditsTheVersionActuallyUsedAsSeed() {
        var system = new DomainWorld();
        var schema = system.createSchema.create("Equipment", Set.of(AssetType.EQUIPMENT));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Original", 1, DomainWorld.temperatureCriterion()));
        system.publishSchemaVersion.publish(schema.id());
        system.openDraft.open(schema.id());
        system.editDraft.removeSection(schema.id(), "Original");
        system.editDraft.addSection(schema.id(), Section.of("Scheduled", 1, DomainWorld.documentationCriterion()));
        system.publishSchemaVersion.publish(schema.id(), system.clock.now().plusSeconds(86400));
        schema.withdrawScheduledVersion(2, system.clock.now(), "Incorrect future rule");

        var draft = system.openDraft.open(schema.id());

        assertThat(draft.sections()).extracting(Section::name).containsExactly("Original");
        assertThat(system.auditTrail.withAction(AuditAction.SCHEMA_DRAFT_OPENED).getLast().detail().toString())
                .contains("draft seeded from version 1").doesNotContain("version 2");
    }
}
