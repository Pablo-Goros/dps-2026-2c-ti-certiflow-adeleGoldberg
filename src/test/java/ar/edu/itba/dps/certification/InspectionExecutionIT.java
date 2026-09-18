package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionExecutionIT {

    private FullSystem system;
    private Party inspector;
    private Asset asset;
    private InspectionId inspectionId;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishLaboratorySchema(AssetType.LABORATORY);
        inspector = system.person("Ana Perez");
        asset = system.asset("Laboratory A", AssetType.LABORATORY,
                system.organization("Favaloro Foundation"));
        inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
    }

    @Test
    @DisplayName("an answer recorded by mistake can be removed before closing")
    void anAnswerCanBeRemovedBeforeClosing() {
        system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE, Measurement.of("30", "c"));

        system.removeAnswer.remove(inspectionId, DomainWorld.TEMPERATURE);

        assertThat(system.inspections.require(inspectionId)
                .requireRecord(DomainWorld.TEMPERATURE).answer()).isEmpty();
        assertThat(lastCorrection().previousValue()).isEqualTo("30 c");
        assertThat(lastCorrection().currentValue()).isNull();
    }

    @Test
    @DisplayName("an evidence reference attached by mistake can be removed before closing")
    void evidenceCanBeRemovedBeforeClosing() {
        EvidenceRecord evidence = system.attachEvidence.attach(inspectionId,
                DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "file://wrong.pdf");

        system.removeEvidence.remove(inspectionId, DomainWorld.DOCUMENTATION, evidence.id());

        assertThat(system.inspections.require(inspectionId)
                .requireRecord(DomainWorld.DOCUMENTATION).evidence()).isEmpty();
        assertThat(lastCorrection().previousValue()).isEqualTo("file://wrong.pdf");
        assertThat(lastCorrection().currentValue()).isNull();
    }

    @Test
    @DisplayName("an observation can be corrected before closing, keeping its author and instant")
    void anObservationCanBeCorrectedBeforeClosing() {
        InspectionNote note = system.recordNote.record(inspectionId, Optional.empty(),
                "door was blocked");

        InspectionNote corrected = system.correctNote.correct(inspectionId, note.id(),
                "corridor door was blocked");

        assertThat(corrected.text()).isEqualTo("corridor door was blocked");
        assertThat(corrected.author()).isEqualTo(note.author());
        assertThat(corrected.recordedAt()).isEqualTo(note.recordedAt());
        assertThat(system.inspections.require(inspectionId).notes()).hasSize(1);
        assertThat(lastCorrection().previousValue()).isEqualTo("door was blocked");
        assertThat(lastCorrection().currentValue()).isEqualTo("corridor door was blocked");
    }

    @Test
    @DisplayName("an observation can be removed before closing")
    void anObservationCanBeRemovedBeforeClosing() {
        InspectionNote note = system.recordNote.record(inspectionId, Optional.empty(),
                "door was blocked");

        system.removeNote.remove(inspectionId, note.id());

        assertThat(system.inspections.require(inspectionId).notes()).isEmpty();
        assertThat(lastCorrection().previousValue()).isEqualTo("door was blocked");
        assertThat(lastCorrection().currentValue()).isNull();
    }

    @Test
    @DisplayName("after closing, no record can be corrected or removed outside a rectification")
    void afterClosingEveryDirectChangeIsRefused() {
        system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE, Measurement.of("5", "c"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        EvidenceRecord evidence = system.attachEvidence.attach(inspectionId,
                DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        InspectionNote note = system.recordNote.record(inspectionId, Optional.empty(),
                "door was blocked");
        system.closeInspection.close(inspectionId);

        assertThatThrownBy(() -> system.removeAnswer.remove(inspectionId, DomainWorld.TEMPERATURE))
                .isInstanceOf(DomainException.class).hasMessageContaining("while it is CLOSED");
        assertThatThrownBy(() -> system.removeEvidence.remove(inspectionId,
                DomainWorld.DOCUMENTATION, evidence.id()))
                .isInstanceOf(DomainException.class).hasMessageContaining("while it is CLOSED");
        assertThatThrownBy(() -> system.correctNote.correct(inspectionId, note.id(), "other text"))
                .isInstanceOf(DomainException.class).hasMessageContaining("while it is CLOSED");
        assertThatThrownBy(() -> system.removeNote.remove(inspectionId, note.id()))
                .isInstanceOf(DomainException.class).hasMessageContaining("while it is CLOSED");

        Inspection after = system.inspections.require(inspectionId);
        assertThat(after.notes()).singleElement()
                .satisfies(kept -> assertThat(kept.text()).isEqualTo("door was blocked"));
        assertThat(after.requireRecord(DomainWorld.TEMPERATURE).answer()).isPresent();
        assertThat(after.requireRecord(DomainWorld.DOCUMENTATION).evidence()).hasSize(1);
    }

    @Test
    @DisplayName("an observation that does not exist cannot be corrected")
    void anUnknownObservationCannotBeCorrected() {
        assertThatThrownBy(() -> system.correctNote.correct(inspectionId, "note-404", "text"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("note note-404 does not exist");
    }

    private FieldChange lastCorrection() {
        return system.auditTrail.withAction(AuditAction.INSPECTION_DATA_CORRECTED).stream()
                .map(entry -> (AuditDetail.DataChanged) entry.detail())
                .flatMap(changed -> changed.changes().stream())
                .reduce((first, second) -> second)
                .orElseThrow();
    }
}
