package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.application.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryCatalogue {

    public final AssetRepository assets = new AssetRepository() {

        private final Map<AssetId, Asset> stored = new LinkedHashMap<>();

        @Override
        public void save(Asset asset) {
            stored.put(asset.id(), asset);
        }

        @Override
        public Optional<Asset> findById(AssetId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Asset> findByType(AssetType assetType) {
            return stored.values().stream()
                    .filter(asset -> asset.assetType() == assetType)
                    .toList();
        }

        @Override
        public List<Asset> findByResponsible(PartyId responsible) {
            return stored.values().stream()
                    .filter(asset -> asset.responsible().partyId().equals(responsible))
                    .toList();
        }

        @Override
        public List<Asset> findByNameContaining(String nameFragment) {
            return stored.values().stream()
                    .filter(asset -> asset.name().contains(nameFragment))
                    .toList();
        }

        @Override
        public List<Asset> findAll() {
            return new ArrayList<>(stored.values());
        }
    };

    public final PartyRepository parties = new PartyRepository() {

        private final Map<PartyId, Party> stored = new LinkedHashMap<>();

        @Override
        public void save(Party party) {
            stored.put(party.id(), party);
        }

        @Override
        public Optional<Party> findById(PartyId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Party> findAll() {
            return new ArrayList<>(stored.values());
        }
    };
}
