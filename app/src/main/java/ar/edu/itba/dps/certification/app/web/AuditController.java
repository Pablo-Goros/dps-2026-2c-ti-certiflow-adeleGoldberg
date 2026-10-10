package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.application.audit.usecase.BrowseAuditTrail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef.ElementType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only view of the append-only audit trail. */
@RestController
@RequestMapping("/api/audit")
class AuditController {

    private final BrowseAuditTrail trail;

    AuditController(BrowseAuditTrail trail) {
        this.trail = trail;
    }

    /** With {@code type} and {@code id} the history of one element; without them, everything. */
    @GetMapping
    Object entries(@RequestParam(name = "type", required = false) ElementType type,
            @RequestParam(name = "id", required = false) String id) {
        if (type != null && id != null) {
            return Plain.of(trail.historyOf(new AuditedElementRef(type, id)));
        }
        return Plain.of(trail.all());
    }
}
