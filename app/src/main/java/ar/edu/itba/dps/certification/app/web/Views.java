package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.finding.CorrectiveAction;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON views of the aggregates that are classes (findings, corrective actions, certificates).
 * Their nested values are records, so {@link Plain} renders those.
 */
final class Views {

    private Views() {
    }

    static Map<String, Object> finding(Finding finding) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", finding.id().value());
        view.put("inspectionId", finding.inspectionId().value());
        view.put("criterionId", finding.criterionId().value());
        view.put("assetId", finding.assetId().value());
        view.put("inspectorId", finding.inspector().value());
        view.put("responsibleId", finding.responsible().value());
        view.put("result", finding.result().name());
        view.put("severity", finding.severity() == null ? null : finding.severity().name());
        view.put("reasons", finding.reasons().stream().map(EvaluationReason::describe).toList());
        view.put("presentedEvidence", finding.presentedEvidence());
        view.put("missingEvidence", Plain.of(finding.shortfalls()));
        view.put("createdAt", finding.createdAt().toString());
        view.put("obligationVoided", finding.obligationVoided());
        view.put("voided", Plain.of(finding.voided()));
        view.put("pendingNonConformity", finding.pendingNonConformity());
        List<Map<String, Object>> actions = finding.correctiveActions().stream().map(Views::action).toList();
        view.put("correctiveActions", actions);
        return view;
    }

    static Map<String, Object> action(CorrectiveAction action) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", action.id().value());
        view.put("status", action.status().name());
        view.put("planningDueDate", action.planningDueDate().toString());
        view.put("deadline", action.deadline().toString());
        view.put("plan", Plain.of(action.plan()));
        view.put("executions", Plain.of(action.executions()));
        view.put("verifications", Plain.of(action.verifications()));
        view.put("closedAt", Plain.of(action.closedAt()));
        view.put("deadlineBreached", action.deadlineBreached());
        view.put("blocksCertification", action.blocksCertification());
        view.put("voided", Plain.of(action.voided()));
        return view;
    }

    static Map<String, Object> certificate(Certificate certificate) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", certificate.id().value());
        view.put("assetId", certificate.assetId().value());
        view.put("backingInspectionId", certificate.backingInspectionId().value());
        view.put("schemaVersion", certificate.schemaVersionId().toString());
        view.put("scope", scope(certificate.scope()));
        view.put("subsystem", subsystem(certificate.scope()));
        view.put("status", certificate.status().name());
        view.put("mode", certificate.mode().name());
        view.put("issuedAt", certificate.validity().issuedAt().toString());
        view.put("expiresAt", certificate.validity().expiresAt().toString());
        view.put("previousCertificateId", Plain.of(certificate.previousCertificateId()));
        view.put("policy", Plain.of(certificate.policy()));
        view.put("suspensions", Plain.of(certificate.suspensions()));
        view.put("unresolvedCauses", Plain.of(certificate.unresolvedCauses()));
        return view;
    }

    /** {@code GLOBAL} or {@code PARTIAL}. */
    static String scope(CertificateScope scope) {
        return scope instanceof CertificateScope.Partial ? "PARTIAL" : "GLOBAL";
    }

    static String subsystem(CertificateScope scope) {
        return scope instanceof CertificateScope.Partial partial ? partial.subsystem().name() : null;
    }
}
