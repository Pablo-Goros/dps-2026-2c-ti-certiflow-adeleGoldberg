package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.AnswerDto;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.AssignRequest;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.EvidenceDto;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.EvidenceRequest;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.InspectionResponse;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.InspectionRow;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.NoteDto;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.NoteRequest;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.NoteTextRequest;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.ReassignRequest;
import ar.edu.itba.dps.certification.app.web.dto.InspectionDtos.RectifyRequest;
import ar.edu.itba.dps.certification.application.inspection.usecase.AssignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.AttachEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.BrowseInspections;
import ar.edu.itba.dps.certification.application.inspection.usecase.CloseInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.CorrectNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.ReassignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.RectifyClosedInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.StartInspection;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateInspectionAct;
import ar.edu.itba.dps.certification.application.shared.port.Transactions;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Inspections: assignment, execution (answers, evidence, notes), closure and rectification. The
 * acting inspector comes from the {@code X-Actor-Id} header and the core enforces that it is the
 * assigned one.
 */
@RestController
@RequestMapping("/api/inspections")
class InspectionController {

    private final AssignInspection assign;
    private final ReassignInspection reassign;
    private final StartInspection start;
    private final RecordAnswer recordAnswer;
    private final RemoveAnswer removeAnswer;
    private final AttachEvidence attachEvidence;
    private final RemoveEvidence removeEvidence;
    private final RecordNote recordNote;
    private final CorrectNote correctNote;
    private final RemoveNote removeNote;
    private final CloseInspection close;
    private final RectifyClosedInspection rectify;
    private final GenerateInspectionAct act;
    private final BrowseInspections inspections;
    private final Transactions transactions;

    InspectionController(AssignInspection assign, ReassignInspection reassign, StartInspection start,
            RecordAnswer recordAnswer, RemoveAnswer removeAnswer, AttachEvidence attachEvidence,
            RemoveEvidence removeEvidence, RecordNote recordNote, CorrectNote correctNote,
            RemoveNote removeNote, CloseInspection close, RectifyClosedInspection rectify,
            GenerateInspectionAct act, BrowseInspections inspections, Transactions transactions) {
        this.assign = assign;
        this.reassign = reassign;
        this.start = start;
        this.recordAnswer = recordAnswer;
        this.removeAnswer = removeAnswer;
        this.attachEvidence = attachEvidence;
        this.removeEvidence = removeEvidence;
        this.recordNote = recordNote;
        this.correctNote = correctNote;
        this.removeNote = removeNote;
        this.close = close;
        this.rectify = rectify;
        this.act = act;
        this.inspections = inspections;
        this.transactions = transactions;
    }

    @PostMapping
    ResponseEntity<InspectionResponse> assign(@RequestBody AssignRequest request) {
        Inspection inspection = transactions.execute(() -> assign.assign(new AssetId(request.assetId()),
                new PartyId(request.inspectorId()), request.expectedDate()));
        return ResponseEntity.created(URI.create("/api/inspections/" + inspection.id().value()))
                .body(view(inspection));
    }

    /** Optional filters: asset, inspector and status. */
    @GetMapping
    List<InspectionRow> list(
            @RequestParam(name = "assetId", required = false) String assetId,
            @RequestParam(name = "inspectorId", required = false) String inspectorId,
            @RequestParam(name = "status", required = false) InspectionStatus status) {
        return inspections.search(Optional.ofNullable(assetId).map(AssetId::new),
                        Optional.ofNullable(inspectorId).map(PartyId::new), Optional.ofNullable(status))
                .stream().map(InspectionRow::of).toList();
    }

    @GetMapping("/{id}")
    InspectionResponse get(@PathVariable("id") String id) {
        return view(existing(id));
    }

    @PutMapping("/{id}/assignment")
    InspectionResponse reassign(@PathVariable("id") String id, @RequestBody ReassignRequest request) {
        existing(id);
        return view(transactions.execute(() -> reassign.reassign(InspectionId.of(id),
                new PartyId(request.inspectorId()), request.expectedDate())));
    }

    @PostMapping("/{id}/start")
    InspectionResponse start(@PathVariable("id") String id) {
        existing(id);
        return view(transactions.execute(() -> start.start(InspectionId.of(id))));
    }

    @PutMapping("/{id}/answers/{criterionId}")
    InspectionResponse answer(@PathVariable("id") String id, @PathVariable("criterionId") String criterionId,
            @RequestBody AnswerDto answer) {
        existing(id);
        return view(transactions.execute(() -> recordAnswer.record(InspectionId.of(id),
                CriterionId.of(criterionId), answer.toDomain())));
    }

    @DeleteMapping("/{id}/answers/{criterionId}")
    InspectionResponse removeAnswer(@PathVariable("id") String id,
            @PathVariable("criterionId") String criterionId) {
        existing(id);
        return view(transactions.execute(() -> removeAnswer.remove(InspectionId.of(id),
                CriterionId.of(criterionId))));
    }

    @PostMapping("/{id}/evidence")
    ResponseEntity<EvidenceDto> attach(@PathVariable("id") String id, @RequestBody EvidenceRequest request) {
        existing(id);
        var evidence = transactions.execute(() -> attachEvidence.attach(InspectionId.of(id),
                CriterionId.of(request.criterionId()), request.requirementLabel(), request.reference()));
        return ResponseEntity.status(201).body(EvidenceDto.of(evidence));
    }

    @DeleteMapping("/{id}/criteria/{criterionId}/evidence/{evidenceId}")
    InspectionResponse removeEvidence(@PathVariable("id") String id,
            @PathVariable("criterionId") String criterionId, @PathVariable("evidenceId") String evidenceId) {
        existing(id);
        return view(transactions.execute(() -> removeEvidence.remove(InspectionId.of(id),
                CriterionId.of(criterionId), evidenceId)));
    }

    @PostMapping("/{id}/notes")
    ResponseEntity<NoteDto> note(@PathVariable("id") String id, @RequestBody NoteRequest request) {
        existing(id);
        var note = transactions.execute(() -> recordNote.record(InspectionId.of(id),
                Optional.ofNullable(request.criterionId()).map(CriterionId::of), request.text()));
        return ResponseEntity.status(201).body(NoteDto.of(note));
    }

    @PutMapping("/{id}/notes/{noteId}")
    NoteDto correctNote(@PathVariable("id") String id, @PathVariable("noteId") String noteId,
            @RequestBody NoteTextRequest request) {
        existing(id);
        return NoteDto.of(transactions.execute(
                () -> correctNote.correct(InspectionId.of(id), noteId, request.text())));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    InspectionResponse removeNote(@PathVariable("id") String id, @PathVariable("noteId") String noteId) {
        existing(id);
        return view(transactions.execute(() -> removeNote.remove(InspectionId.of(id), noteId)));
    }

    /** Closing evaluates every criterion; closing an already closed inspection is harmless. */
    @PostMapping("/{id}/close")
    InspectionResponse close(@PathVariable("id") String id) {
        existing(id);
        transactions.execute(() -> close.close(InspectionId.of(id)));
        return view(existing(id));
    }

    @PostMapping("/{id}/rectifications")
    ResponseEntity<InspectionResponse> rectify(@PathVariable("id") String id,
            @RequestBody RectifyRequest request) {
        existing(id);
        transactions.execute(() -> rectify.rectify(InspectionId.of(id), request.reason(), request.toDomain()));
        return ResponseEntity.status(201).body(view(existing(id)));
    }

    /** The inspection act: what was recorded, with original and rectified values side by side. */
    @GetMapping("/{id}/act")
    Object act(@PathVariable("id") String id) {
        existing(id);
        return Plain.of(act.generate(InspectionId.of(id)));
    }

    private Inspection existing(String id) {
        return inspections.find(InspectionId.of(id))
                .orElseThrow(() -> new NotFoundException("inspection " + id + " does not exist"));
    }

    private InspectionResponse view(Inspection inspection) {
        var version = inspections.frozenVersion(inspection).orElse(null);
        return InspectionResponse.of(inspection, version, Plain::of);
    }
}
