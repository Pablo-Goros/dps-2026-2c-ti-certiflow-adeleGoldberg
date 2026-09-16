package ar.edu.itba.dps.certification.domain.report;

import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record FindingsSummary(InspectionId inspectionId, List<FindingLine> lines) {

    public FindingsSummary {
        Validate.required(inspectionId, "inspection id");
        lines = List.copyOf(Validate.required(lines, "finding lines"));
    }

    public record FindingLine(
            FindingId findingId,
            CriterionId criterionId,
            CriterionResult result,
            Severity severity,
            List<String> motives,
            PartyId responsible,
            List<String> presentedEvidence,
            List<String> missingEvidence,
            boolean obligationVoided,
            ActionLine action) {

        public FindingLine {
            Validate.required(findingId, "finding id");
            Validate.required(criterionId, "criterion id");
            Validate.required(result, "result");
            Validate.required(severity, "severity");
            motives = List.copyOf(Validate.required(motives, "motives"));
            Validate.required(responsible, "responsible");
            presentedEvidence = List.copyOf(Validate.required(presentedEvidence, "presented evidence"));
            missingEvidence = List.copyOf(Validate.required(missingEvidence, "missing evidence"));
            Validate.required(action, "action");
        }
    }

    public record ActionLine(
            CorrectiveActionId correctiveActionId,
            CorrectiveActionStatus status,
            Optional<String> work,
            Optional<PartyId> executor,
            Optional<LocalDate> dueDate,
            boolean deadlineBreached,
            List<VerificationLine> verifications) {

        public ActionLine {
            Validate.required(correctiveActionId, "corrective action id");
            Validate.required(status, "status");
            Validate.required(work, "work");
            Validate.required(executor, "executor");
            Validate.required(dueDate, "due date");
            verifications = List.copyOf(Validate.required(verifications, "verifications"));
        }
    }

    public record VerificationLine(boolean satisfactory, String reason, Instant verifiedAt) {

        public VerificationLine {
            reason = Validate.requiredText(reason, "verification reason");
            Validate.required(verifiedAt, "instant");
        }
    }
}
