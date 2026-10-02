package ar.edu.itba.dps.certification.application.catalogue.port;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;
import java.util.Optional;

public interface AssetRepository {

    void save(Asset asset);

    Optional<Asset> findById(AssetId id);

    default Asset require(AssetId id) {
        return findById(id).orElseThrow(() -> new DomainException("asset " + id + " is not registered"));
    }

    List<Asset> findByType(AssetType assetType);

    List<Asset> findByResponsible(PartyId responsible);

    List<Asset> findByNameContaining(String nameFragment);

    List<Asset> findAll();
}
