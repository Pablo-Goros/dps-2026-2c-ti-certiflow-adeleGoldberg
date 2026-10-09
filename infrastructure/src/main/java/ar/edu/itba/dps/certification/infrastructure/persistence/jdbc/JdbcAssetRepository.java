package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class JdbcAssetRepository extends DocumentRepository<Asset> implements AssetRepository {

    public JdbcAssetRepository(JdbcTransactions db, StateCodec codec) {
        super(db, codec, Asset.class, "asset");
    }

    @Override
    public void save(Asset asset) {
        Map<String, Object> indexed = columns();
        indexed.put("asset_type", asset.assetType().name());
        indexed.put("responsible_id", asset.responsible().partyId().value());
        indexed.put("name", asset.name());
        indexed.put("jurisdiction", asset.jurisdiction().value());
        store(asset.id().value(), asset, indexed);
    }

    @Override
    public Optional<Asset> findById(AssetId id) {
        return findByKey(id.value());
    }

    @Override
    public List<Asset> findByType(AssetType assetType) {
        return findMany("WHERE t.asset_type = ?", assetType.name());
    }

    @Override
    public List<Asset> findByResponsible(PartyId responsible) {
        return findMany("WHERE t.responsible_id = ?", responsible.value());
    }

    @Override
    public List<Asset> findByNameContaining(String nameFragment) {
        Objects.requireNonNull(nameFragment, "nameFragment");
        if (nameFragment.isEmpty()) {
            return findAll();
        }
        return findMany("WHERE LOCATE(?, t.name) > 0", nameFragment);
    }

    @Override
    public List<Asset> findAll() {
        return findMany("");
    }
}
