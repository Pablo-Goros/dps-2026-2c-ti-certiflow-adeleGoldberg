package ar.edu.itba.dps.certification.domain.finding.action;

import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.LocalDate;

public record CorrectionPlan(String work, PartyId executor, LocalDate dueDate) {

    public CorrectionPlan {
        work = Validate.requiredText(work, "planned work");
        Validate.required(executor, "executor");
        Validate.required(dueDate, "due date");
    }

    public boolean overdueOn(LocalDate today) {
        return today.isAfter(dueDate);
    }
}
