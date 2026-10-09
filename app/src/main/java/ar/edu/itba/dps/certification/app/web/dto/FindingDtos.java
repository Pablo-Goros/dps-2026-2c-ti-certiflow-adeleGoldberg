package ar.edu.itba.dps.certification.app.web.dto;

import java.time.LocalDate;
import java.util.List;

public final class FindingDtos {

    private FindingDtos() {
    }

    /** The responsible of the finding plans the work; {@code executorId} is who will carry it out. */
    public record PlanRequest(String work, String executorId, LocalDate dueDate) {
    }

    public record ExecutionRequest(String statement, List<String> evidenceReferences) {
    }

    public record VerificationRequest(boolean satisfactory, String reason) {
    }
}
