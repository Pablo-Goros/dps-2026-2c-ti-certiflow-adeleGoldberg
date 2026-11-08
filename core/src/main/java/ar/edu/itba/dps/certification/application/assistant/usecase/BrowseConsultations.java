package ar.edu.itba.dps.certification.application.assistant.usecase;

import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.assistant.port.ConsultationRepository;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Read side of the assistant: what was asked about an inspection, what it suggested and what was decided. */
public final class BrowseConsultations implements ar.edu.itba.dps.certification.domain.assistant.port.inbound.BrowseConsultations {

    private static final Comparator<Consultation> NEWEST_FIRST =
            Comparator.comparing(Consultation::requestedAt).reversed();

    private final Transactions transactions;

    private final ConsultationRepository consultations;

    public BrowseConsultations(ConsultationRepository consultations, Transactions transactions) {
        this.transactions = transactions;
        this.consultations = consultations;
    }

    /** Newest first; consultations requested at the same instant come last-stored first. */
    public List<Consultation> of(InspectionId inspectionId) {
        return transactions.execute(() -> {
            List<Consultation> newestFirst = new ArrayList<>(consultations.findByInspection(inspectionId).reversed());
            newestFirst.sort(NEWEST_FIRST);
            return List.copyOf(newestFirst);
        });
    }

    public Optional<Consultation> find(ConsultationId consultationId) {
        return transactions.execute(() -> {
            return consultations.findById(consultationId);
        });
    }
}
