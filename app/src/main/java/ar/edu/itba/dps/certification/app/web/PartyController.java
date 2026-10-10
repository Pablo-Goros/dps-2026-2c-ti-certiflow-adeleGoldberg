package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.web.dto.PartyRequest;
import ar.edu.itba.dps.certification.app.web.dto.PartyResponse;
import ar.edu.itba.dps.certification.application.catalogue.usecase.BrowseParties;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.application.shared.port.Transactions;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/parties")
class PartyController {

    private final RegisterParty registerParty;
    private final BrowseParties parties;
    private final Transactions transactions;

    PartyController(RegisterParty registerParty, BrowseParties parties, Transactions transactions) {
        this.registerParty = registerParty;
        this.parties = parties;
        this.transactions = transactions;
    }

    @PostMapping
    ResponseEntity<PartyResponse> register(@RequestBody PartyRequest request) {
        var party = transactions.execute(() -> registerParty.register(request.name(), request.kind()));
        return ResponseEntity.created(URI.create("/api/parties/" + party.id().value()))
                .body(PartyResponse.of(party));
    }

    @GetMapping
    List<PartyResponse> list() {
        return parties.all().stream().map(PartyResponse::of).toList();
    }

    @GetMapping("/{id}")
    PartyResponse get(@PathVariable("id") String id) {
        return parties.find(new PartyId(id))
                .map(PartyResponse::of)
                .orElseThrow(() -> new NotFoundException("party " + id + " does not exist"));
    }
}
