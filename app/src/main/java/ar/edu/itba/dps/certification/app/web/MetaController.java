package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.config.JurisdictionCatalog;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Static reference data the front end needs to build its forms. */
@RestController
@RequestMapping("/api/meta")
class MetaController {

    record AssetTypeInfo(String name, List<String> characteristics, List<String> subsystems) {
    }

    private final JurisdictionCatalog jurisdictions;

    MetaController(JurisdictionCatalog jurisdictions) {
        this.jurisdictions = jurisdictions;
    }

    @GetMapping("/asset-types")
    List<AssetTypeInfo> assetTypes() {
        return Arrays.stream(AssetType.values())
                .map(type -> new AssetTypeInfo(type.name(),
                        type.characteristicNames().stream().sorted().toList(),
                        type.subsystems().stream().map(subsystem -> subsystem.name()).toList()))
                .toList();
    }

    @GetMapping("/jurisdictions")
    List<String> jurisdictions() {
        return jurisdictions.names();
    }
}
