package ar.edu.itba.dps.certification.application.assistant.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequests;
import ar.edu.itba.dps.certification.domain.assistant.AssistantReply;
import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.assistant.port.ConsultationRepository;
import ar.edu.itba.dps.certification.domain.assistant.port.InspectionAssistant;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.time.Instant;

public final class RequestAssistance implements ar.edu.itba.dps.certification.domain.assistant.port.inbound.RequestAssistance {
    private final Transactions transactions;

    private final InspectionRepository inspections;
    private final SchemaCatalog schemas;
    private final InspectionAssistant assistant;
    private final ConsultationRepository consultations;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public RequestAssistance(InspectionRepository inspections, SchemaCatalog schemas, InspectionAssistant assistant,
            ConsultationRepository consultations, IdGenerator ids, Clock clock, AuditRecorder audit,
            ActorProvider actors, Transactions transactions) {
        this.transactions = transactions;
        this.inspections = inspections;
        this.schemas = schemas;
        this.assistant = assistant;
        this.consultations = consultations;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    /** Two short transactions around the call, so no database transaction waits for the model. */
    public Consultation request(InspectionId inspectionId) {
        var actor = actors.requireUser();
        Instant requestedAt = clock.now();
        AssistanceRequest request = transactions.execute(() -> {
            Inspection inspection = inspections.require(inspectionId);
            Consultation.ensureConsultable(inspection, actor.partyId());
            return AssistanceRequests.of(inspection,
                    schemas.requireVersion(inspection.requireFrozenSchemaVersionId()), requestedAt);
        });
        AssistantReply reply = assistant.suggest(request);
        return transactions.execute(() -> {
            Consultation consultation = Consultation.record(ConsultationId.of(ids.newIdentifier()),
                    inspections.require(inspectionId), actor.partyId(), requestedAt, request, reply);
            consultations.save(consultation);
            audit.recordAs(actor, AuditedElementRef.inspection(inspectionId.value()),
                    AuditAction.ASSISTANCE_REQUESTED,
                    AuditDetail.decision("consultation " + consultation.id() + " answered by "
                                    + reply.version().describe(),
                            consultation.suggestions().size() + " suggestions, "
                                    + consultation.rejected().size() + " rejected"));
            return consultation;
        });
    }
}
