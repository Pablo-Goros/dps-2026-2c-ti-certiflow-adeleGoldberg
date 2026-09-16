package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.LocalDate;
import java.util.List;

public final class ReassignInspection {

    private final InspectionRepository inspections;
    private final AuditRecorder audit;

    public ReassignInspection(InspectionRepository inspections, AuditRecorder audit) {
        this.inspections = inspections;
        this.audit = audit;
    }

    public Inspection reassign(InspectionId inspectionId, PartyId newInspector,
            LocalDate newExpectedDate) {
        Inspection inspection = inspections.require(inspectionId);
        PartyId previousInspector = inspection.inspector();
        LocalDate previousDate = inspection.expectedDate();
        inspection.reassign(newInspector, newExpectedDate);
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_REASSIGNED, AuditDetail.dataChanged(List.of(
                        FieldChange.of("inspector", previousInspector, newInspector),
                        FieldChange.of("expectedDate", previousDate, newExpectedDate))));
        return inspection;
    }
}
