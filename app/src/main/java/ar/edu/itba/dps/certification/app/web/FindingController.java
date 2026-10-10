package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.web.dto.FindingDtos.ExecutionRequest;
import ar.edu.itba.dps.certification.app.web.dto.FindingDtos.PlanRequest;
import ar.edu.itba.dps.certification.app.web.dto.FindingDtos.VerificationRequest;
import ar.edu.itba.dps.certification.application.finding.usecase.BrowseFindings;
import ar.edu.itba.dps.certification.application.finding.usecase.PlanCorrectiveAction;
import ar.edu.itba.dps.certification.application.finding.usecase.ReportCorrectiveActionExecution;
import ar.edu.itba.dps.certification.application.finding.usecase.VerifyCorrectiveAction;
import ar.edu.itba.dps.certification.application.inspection.usecase.BrowseInspections;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateFindingsSummary;
import ar.edu.itba.dps.certification.application.shared.port.Transactions;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Non-conformities found when an inspection closes and the corrective actions that answer them. */
@RestController
@RequestMapping("/api")
class FindingController {

    private final BrowseFindings findings;
    private final BrowseInspections inspections;
    private final PlanCorrectiveAction plan;
    private final ReportCorrectiveActionExecution execution;
    private final VerifyCorrectiveAction verification;
    private final GenerateFindingsSummary summary;
    private final Transactions transactions;

    FindingController(BrowseFindings findings, BrowseInspections inspections, PlanCorrectiveAction plan,
            ReportCorrectiveActionExecution execution, VerifyCorrectiveAction verification,
            GenerateFindingsSummary summary, Transactions transactions) {
        this.findings = findings;
        this.inspections = inspections;
        this.plan = plan;
        this.execution = execution;
        this.verification = verification;
        this.summary = summary;
        this.transactions = transactions;
    }

    @GetMapping("/findings")
    List<Map<String, Object>> list(
            @RequestParam(name = "inspectionId", required = false) String inspectionId,
            @RequestParam(name = "assetId", required = false) String assetId,
            @RequestParam(name = "openActions", required = false) Boolean openActions) {
        return findings.search(Optional.ofNullable(inspectionId).map(InspectionId::of),
                        Optional.ofNullable(assetId).map(AssetId::new), Boolean.TRUE.equals(openActions))
                .stream().map(Views::finding).toList();
    }

    @GetMapping("/findings/{id}")
    Map<String, Object> get(@PathVariable("id") String id) {
        return Views.finding(existing(id));
    }

    /** Planned by the finding's responsible (the actor); the due date is bounded by the planning deadline. */
    @PostMapping("/findings/{id}/plan")
    Map<String, Object> plan(@PathVariable("id") String id, @RequestBody PlanRequest request) {
        existing(id);
        return Views.finding(transactions.execute(() -> plan.plan(FindingId.of(id), request.work(),
                new PartyId(request.executorId()), request.dueDate())));
    }

    @PostMapping("/findings/{id}/execution")
    Map<String, Object> report(@PathVariable("id") String id, @RequestBody ExecutionRequest request) {
        existing(id);
        return Views.finding(transactions.execute(() -> execution.report(FindingId.of(id),
                request.statement(), request.evidenceReferences())));
    }

    @PostMapping("/findings/{id}/verification")
    Map<String, Object> verify(@PathVariable("id") String id, @RequestBody VerificationRequest request) {
        existing(id);
        return Views.finding(transactions.execute(() -> verification.verify(FindingId.of(id),
                request.satisfactory(), request.reason())));
    }

    /** Findings of one inspection with their actions and verifications, as a report. */
    @GetMapping("/inspections/{id}/findings-summary")
    Object summary(@PathVariable("id") String id) {
        if (inspections.find(InspectionId.of(id)).isEmpty()) {
            throw new NotFoundException("inspection " + id + " does not exist");
        }
        return Plain.of(summary.generate(InspectionId.of(id)));
    }

    private Finding existing(String id) {
        return findings.find(FindingId.of(id))
                .orElseThrow(() -> new NotFoundException("finding " + id + " does not exist"));
    }
}
