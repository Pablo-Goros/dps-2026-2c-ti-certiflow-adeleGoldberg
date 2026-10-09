package ar.edu.itba.dps.certification.app.web.dto;

import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;

public record PartyRequest(String name, PartyKind kind) {
}
