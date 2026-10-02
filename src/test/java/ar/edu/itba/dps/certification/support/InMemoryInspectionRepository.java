package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionSummary;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryInspectionRepository implements InspectionRepository {

    private final Map<InspectionId, Inspection> stored = new LinkedHashMap<>();

    @Override
    public void save(Inspection inspection) {
        stored.put(inspection.id(), inspection);
    }

    @Override
    public Optional<Inspection> findById(InspectionId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public InspectionSummary summaryOf(InspectionId id) {
        return InspectionSummary.of(require(id));
    }

    @Override
    public boolean wasRectified(InspectionId id) {
        return !require(id).rectifications().isEmpty();
    }

    @Override
    public Optional<Inspection> findNonClosedByAsset(AssetId assetId) {
        return stored.values().stream()
                .filter(inspection -> inspection.assetId().equals(assetId))
                .filter(inspection -> !inspection.status().closed())
                .findFirst();
    }

    @Override
    public List<Inspection> findClosedByAsset(AssetId assetId) {
        return stored.values().stream()
                .filter(inspection -> inspection.assetId().equals(assetId))
                .filter(inspection -> inspection.status().closed())
                .toList();
    }

    @Override
    public List<Inspection> findAll() {
        return new ArrayList<>(stored.values());
    }
}
