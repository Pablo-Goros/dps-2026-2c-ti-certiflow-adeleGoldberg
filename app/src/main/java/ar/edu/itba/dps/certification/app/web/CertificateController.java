package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.web.dto.CertificateDtos.IssueRequest;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.certification.usecase.DeriveGlobalCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.EvaluateIssuanceEligibility;
import ar.edu.itba.dps.certification.application.certification.usecase.IssueCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.RenewCertificate;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateCertificateReport;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcTransactions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Certificates (F1 partial by subsystem, plus the global one) issued from a closed inspection under
 * the policy of the asset's jurisdiction (F3). A refused issuance is not an error of the request:
 * the body explains what blocks it.
 */
@RestController
@RequestMapping("/api")
class CertificateController {

    private final IssueCertificate issue;
    private final RenewCertificate renew;
    private final EvaluateIssuanceEligibility eligibility;
    private final DeriveGlobalCertificate derivation;
    private final GenerateCertificateReport report;
    private final CertificateRepository certificates;
    private final InspectionRepository inspections;
    private final JdbcTransactions transactions;

    CertificateController(IssueCertificate issue, RenewCertificate renew,
            EvaluateIssuanceEligibility eligibility, DeriveGlobalCertificate derivation,
            GenerateCertificateReport report, CertificateRepository certificates,
            InspectionRepository inspections, JdbcTransactions transactions) {
        this.issue = issue;
        this.renew = renew;
        this.eligibility = eligibility;
        this.derivation = derivation;
        this.report = report;
        this.certificates = certificates;
        this.inspections = inspections;
        this.transactions = transactions;
    }

    @PostMapping("/inspections/{id}/certificates")
    ResponseEntity<Map<String, Object>> issue(@PathVariable("id") String id,
            @RequestBody(required = false) IssueRequest request) {
        requireInspection(id);
        InspectionId inspectionId = InspectionId.of(id);
        String subsystem = request == null ? null : request.subsystem();
        return respond(transactions.execute(() -> subsystem == null
                ? issue.issue(inspectionId)
                : issue.issuePartial(inspectionId, Subsystem.of(subsystem))));
    }

    @PostMapping("/inspections/{id}/certificates/renewal")
    ResponseEntity<Map<String, Object>> renew(@PathVariable("id") String id,
            @RequestBody(required = false) IssueRequest request) {
        requireInspection(id);
        InspectionId inspectionId = InspectionId.of(id);
        String subsystem = request == null ? null : request.subsystem();
        return respond(transactions.execute(() -> subsystem == null
                ? renew.renew(inspectionId)
                : renew.renewPartial(inspectionId, Subsystem.of(subsystem))));
    }

    /** Whether a certificate could be issued now and, if not, every blocker. Changes nothing. */
    @GetMapping("/inspections/{id}/eligibility")
    Object eligibility(@PathVariable("id") String id,
            @RequestParam(name = "subsystem", required = false) String subsystem) {
        requireInspection(id);
        InspectionId inspectionId = InspectionId.of(id);
        return Plain.of(subsystem == null
                ? eligibility.assess(inspectionId)
                : eligibility.assess(inspectionId, Subsystem.of(subsystem)));
    }

    /** Whether the partial certificates in force add up to a global one. Changes nothing. */
    @GetMapping("/inspections/{id}/global-derivation")
    Object globalDerivation(@PathVariable("id") String id) {
        requireInspection(id);
        return Plain.of(derivation.deriveFor(InspectionId.of(id)));
    }

    @GetMapping("/certificates")
    List<Map<String, Object>> list(
            @RequestParam(name = "assetId", required = false) String assetId,
            @RequestParam(name = "inspectionId", required = false) String inspectionId,
            @RequestParam(name = "status", required = false) CertificateStatus status) {
        List<Certificate> found = inspectionId != null
                ? certificates.findByBackingInspection(InspectionId.of(inspectionId))
                : certificates.findAll();
        return found.stream()
                .filter(c -> assetId == null || c.assetId().value().equals(assetId))
                .filter(c -> status == null || c.status() == status)
                .map(Views::certificate)
                .toList();
    }

    @GetMapping("/certificates/{id}")
    Map<String, Object> get(@PathVariable("id") String id) {
        return Views.certificate(existing(id));
    }

    /** The certificate with its pending commitments, suspension causes and policy, for printing. */
    @GetMapping("/certificates/{id}/report")
    Object report(@PathVariable("id") String id) {
        existing(id);
        return Plain.of(report.generate(CertificateId.of(id)));
    }

    private ResponseEntity<Map<String, Object>> respond(IssuanceDecision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        return switch (decision) {
            case IssuanceDecision.Issued issued -> {
                body.put("outcome", "ISSUED");
                body.put("certificate", Views.certificate(issued.certificate()));
                yield ResponseEntity.status(201).body(body);
            }
            case IssuanceDecision.AlreadyIssued already -> {
                body.put("outcome", "ALREADY_ISSUED");
                body.put("detail", Plain.of(already));
                yield ResponseEntity.status(200).body(body);
            }
            case IssuanceDecision.Blocked blocked -> {
                body.put("outcome", "BLOCKED");
                body.put("assessment", Plain.of(blocked.assessment()));
                yield ResponseEntity.status(422).body(body);
            }
        };
    }

    private Certificate existing(String id) {
        return certificates.findById(CertificateId.of(id))
                .orElseThrow(() -> new NotFoundException("certificate " + id + " does not exist"));
    }

    private void requireInspection(String id) {
        if (inspections.findById(InspectionId.of(id)).isEmpty()) {
            throw new NotFoundException("inspection " + id + " does not exist");
        }
    }
}
