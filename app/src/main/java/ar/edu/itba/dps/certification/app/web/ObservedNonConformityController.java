package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.InspectionResponse;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.ObservedNonConformityDto;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.ObservedNonConformityRequest;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.inbound.BrowseInspections;
import ar.edu.itba.dps.certification.domain.inspection.port.inbound.RecordObservedNonConformity;
import ar.edu.itba.dps.certification.domain.inspection.port.inbound.WithdrawObservedNonConformity;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-conformities the inspector observed on a criterion beyond what its rule measures. Recorded
 * while the inspection is in progress; after closure they are withdrawn only by rectification.
 */
@RestController
@RequestMapping("/api/inspections/{id}/criteria/{criterionId}/observed-nonconformities")
class ObservedNonConformityController {

    private final RecordObservedNonConformity record;
    private final WithdrawObservedNonConformity withdraw;
    private final BrowseInspections inspections;

    ObservedNonConformityController(RecordObservedNonConformity record, WithdrawObservedNonConformity withdraw,
            BrowseInspections inspections) {
        this.record = record;
        this.withdraw = withdraw;
        this.inspections = inspections;
    }

    @PostMapping
    ResponseEntity<ObservedNonConformityDto> record(@PathVariable("id") String id,
            @PathVariable("criterionId") String criterionId, @RequestBody ObservedNonConformityRequest request) {
        existing(id);
        var observation = record.record(InspectionId.of(id), CriterionId.of(criterionId), request.result(),
                request.severity(), request.description(),
                Optional.ofNullable(request.noteId()).filter(noteId -> !noteId.isBlank()));
        return ResponseEntity.status(201).body(ObservedNonConformityDto.of(CriterionId.of(criterionId), observation));
    }

    @DeleteMapping("/{observationId}")
    InspectionResponse withdraw(@PathVariable("id") String id, @PathVariable("criterionId") String criterionId,
            @PathVariable("observationId") String observationId) {
        existing(id);
        return view(withdraw.withdraw(InspectionId.of(id), CriterionId.of(criterionId), observationId));
    }

    private Inspection existing(String id) {
        return inspections.find(InspectionId.of(id))
                .orElseThrow(() -> new NotFoundException("inspection " + id + " does not exist"));
    }

    private InspectionResponse view(Inspection inspection) {
        return InspectionResponse.of(inspection, inspections.frozenVersion(inspection).orElse(null), Plain::of);
    }
}
