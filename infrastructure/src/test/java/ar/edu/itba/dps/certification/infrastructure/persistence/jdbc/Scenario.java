package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * A realistic body of data produced by the real use cases over the in-memory adapters of the core:
 * a rejected inspection with a planned corrective action, an approved one with a global
 * certificate, a facility with two partial certificates, and an inspection still open.
 */
final class Scenario {

    final FullSystem system = new FullSystem();
    final Party inspector;
    final Party organization;
    final Asset rejectedLaboratory;
    final Asset approvedLaboratory;
    final Asset openLaboratory;
    final Asset facility;
    final InspectionId rejectedInspection;
    final InspectionId approvedInspection;
    final InspectionId facilityInspection;
    final InspectionId openInspection;

    Scenario() {
        system.publishLaboratorySchema(AssetType.LABORATORY);
        system.publishSubsystemSchema(AssetType.FACILITY);
        inspector = system.person("Ana Perez");
        system.actAs(inspector);
        organization = system.organization("Favaloro Foundation");
        rejectedLaboratory = system.asset("Laboratory A", AssetType.LABORATORY, organization);
        approvedLaboratory = system.asset("Laboratory B", AssetType.LABORATORY, organization);
        openLaboratory = system.asset("Laboratory C", AssetType.LABORATORY, organization);
        facility = system.registerAsset.register("Central Facility", AssetType.FACILITY, organization.id(),
                "Building 1", Map.of("room", "12", "purpose", "research"),
                Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE), JurisdictionId.of("REFERENCE"));

        rejectedInspection = laboratoryInspection(rejectedLaboratory, "30");
        Finding finding = system.findings.findByInspection(rejectedInspection).getFirst();
        system.planAsResponsible(finding.id(), "recalibrate the cooling unit", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));

        approvedInspection = laboratoryInspection(approvedLaboratory, "5");
        system.issueCertificate.issue(approvedInspection);

        facilityInspection = facilityInspection();
        system.issueCertificate.issuePartial(facilityInspection, DomainWorld.ELECTRICAL);
        system.issueCertificate.issuePartial(facilityInspection, DomainWorld.PRESSURE);

        openInspection = system.assignInspection
                .assign(openLaboratory.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(openInspection);
        system.recordAnswer.record(openInspection, DomainWorld.TEMPERATURE, Measurement.of("5", "c"));
    }

    private InspectionId laboratoryInspection(Asset asset, String temperature) {
        InspectionId id = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(id);
        system.recordAnswer.record(id, DomainWorld.TEMPERATURE, Measurement.of(temperature, "c"));
        system.recordAnswer.record(id, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(id);
        return id;
    }

    private InspectionId facilityInspection() {
        InspectionId id = system.assignInspection
                .assign(facility.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(id);
        system.recordAnswer.record(id, DomainWorld.ELECTRICAL_WIRING, OptionAnswer.of("clean"));
        system.recordAnswer.record(id, DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean"));
        system.recordAnswer.record(id, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(id);
        return id;
    }
}
