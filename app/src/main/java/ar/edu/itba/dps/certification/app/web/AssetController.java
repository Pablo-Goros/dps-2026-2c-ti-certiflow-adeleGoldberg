package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.config.JurisdictionCatalog;
import ar.edu.itba.dps.certification.app.web.dto.AssetRequest;
import ar.edu.itba.dps.certification.app.web.dto.AssetResponse;
import ar.edu.itba.dps.certification.app.web.dto.LocationRequest;
import ar.edu.itba.dps.certification.app.web.dto.ResponsibleRequest;
import ar.edu.itba.dps.certification.application.catalogue.usecase.ChangeAssetResponsible;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RelocateAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.SearchAssets;
import ar.edu.itba.dps.certification.application.shared.port.Transactions;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/assets")
class AssetController {

    private final RegisterAsset registerAsset;
    private final RelocateAsset relocateAsset;
    private final ChangeAssetResponsible changeResponsible;
    private final SearchAssets searchAssets;
    private final Transactions transactions;
    private final JurisdictionCatalog jurisdictions;

    AssetController(RegisterAsset registerAsset, RelocateAsset relocateAsset,
            ChangeAssetResponsible changeResponsible, SearchAssets searchAssets,
            Transactions transactions, JurisdictionCatalog jurisdictions) {
        this.registerAsset = registerAsset;
        this.relocateAsset = relocateAsset;
        this.changeResponsible = changeResponsible;
        this.searchAssets = searchAssets;
        this.transactions = transactions;
        this.jurisdictions = jurisdictions;
    }

    @PostMapping
    ResponseEntity<AssetResponse> register(@RequestBody AssetRequest request) {
        if (request.jurisdiction() == null || !jurisdictions.names().contains(request.jurisdiction().trim())) {
            throw new InvalidArgumentException("unknown jurisdiction '" + request.jurisdiction()
                    + "'; known ones: " + jurisdictions.names());
        }
        Asset asset = transactions.execute(() -> {
            var responsible = new PartyId(request.responsibleId());
            var jurisdiction = JurisdictionId.of(request.jurisdiction());
            Map<String, String> characteristics = request.characteristics();
            if (request.subsystems() == null) {
                return registerAsset.register(request.name(), request.assetType(), responsible,
                        request.location(), characteristics, jurisdiction);
            }
            Set<Subsystem> subsystems = new LinkedHashSet<>();
            request.subsystems().forEach(name -> subsystems.add(Subsystem.of(name)));
            return registerAsset.register(request.name(), request.assetType(), responsible,
                    request.location(), characteristics, subsystems, jurisdiction);
        });
        return ResponseEntity.created(URI.create("/api/assets/" + asset.id().value()))
                .body(AssetResponse.of(asset));
    }

    /** Filters are alternatives, checked in this order: type, responsible, name fragment. */
    @GetMapping
    List<AssetResponse> list(
            @RequestParam(name = "type", required = false) AssetType type,
            @RequestParam(name = "responsible", required = false) String responsible,
            @RequestParam(name = "name", required = false) String name) {
        List<Asset> found;
        if (type != null) {
            found = searchAssets.byType(type);
        } else if (responsible != null) {
            found = searchAssets.byResponsible(new PartyId(responsible));
        } else if (name != null) {
            found = searchAssets.byNameContaining(name);
        } else {
            found = searchAssets.all();
        }
        return found.stream().map(AssetResponse::of).toList();
    }

    @GetMapping("/{id}")
    AssetResponse get(@PathVariable("id") String id) {
        return searchAssets.byId(new AssetId(id))
                .map(AssetResponse::of)
                .orElseThrow(() -> new NotFoundException("asset " + id + " does not exist"));
    }

    @PutMapping("/{id}/location")
    AssetResponse relocate(@PathVariable("id") String id, @RequestBody LocationRequest request) {
        requireExisting(id);
        return AssetResponse.of(transactions.execute(
                () -> relocateAsset.relocate(new AssetId(id), request.location())));
    }

    @PutMapping("/{id}/responsible")
    AssetResponse changeResponsible(@PathVariable("id") String id, @RequestBody ResponsibleRequest request) {
        requireExisting(id);
        return AssetResponse.of(transactions.execute(
                () -> changeResponsible.change(new AssetId(id), new PartyId(request.responsibleId()))));
    }

    private void requireExisting(String id) {
        if (searchAssets.byId(new AssetId(id)).isEmpty()) {
            throw new NotFoundException("asset " + id + " does not exist");
        }
    }
}
