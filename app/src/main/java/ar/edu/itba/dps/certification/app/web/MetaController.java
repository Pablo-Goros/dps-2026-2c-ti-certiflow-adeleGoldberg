package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.domain.assistant.port.AssistantAvailability;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.certification.port.JurisdictionCatalog;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Static reference data the front end needs to build its forms. */
@RestController
@RequestMapping("/api/meta")
class MetaController {

    record AssetTypeInfo(String name, List<String> characteristics, List<String> subsystems) {
    }

    /** Whether the inspection assistant is on and which adapter answers; never its address, model or key. */
    record AssistantInfo(boolean enabled, String provider) {
    }

    private final JurisdictionCatalog jurisdictions;
    private final AssistantAvailability assistant;

    MetaController(JurisdictionCatalog jurisdictions, AssistantAvailability assistant) {
        this.jurisdictions = jurisdictions;
        this.assistant = assistant;
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

    /** Lets the interface offer the assistant only when the server has it on. */
    @GetMapping("/assistant")
    AssistantInfo assistant() {
        return new AssistantInfo(assistant.enabled(), assistant.adapter());
    }
}
