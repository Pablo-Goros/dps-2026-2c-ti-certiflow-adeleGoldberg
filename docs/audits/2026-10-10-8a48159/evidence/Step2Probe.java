import ar.edu.itba.dps.certification.support.*;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.finding.usecase.*;
import ar.edu.itba.dps.certification.domain.catalogue.*;
import ar.edu.itba.dps.certification.domain.inspection.*;
import ar.edu.itba.dps.certification.domain.inspection.rectification.*;
import ar.edu.itba.dps.certification.domain.schema.*;
import ar.edu.itba.dps.certification.domain.shared.answer.*;
import java.time.*;
import java.util.*;

/** Read-only audit probe: uses baseline compiled code and in-memory test ports. */
public class Step2Probe {
    static Clock localClock(FullSystem s) {
        return new Clock() {
            public Instant now() { return s.clock.now(); }
            public LocalDate today() { return now().atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toLocalDate(); }
        };
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var s = new FullSystem();
        var owner = s.person("Owner"); var inspector = s.person("Inspector");
        s.actAs(inspector); s.publishLaboratorySchema(AssetType.LABORATORY);
        var asset = s.asset("Lab", AssetType.LABORATORY, owner);
        var id = s.assignInspection.assign(asset.id(), inspector.id(), s.clock.today()).id();
        s.startInspection.start(id);
        s.recordAnswer.record(id, DomainWorld.TEMPERATURE, Measurement.of("20", "c"));
        s.recordAnswer.record(id, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        s.attachEvidence.attach(id, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "manual");
        s.closeInspection.close(id);
        var f = s.findings.findByInspection(id).getFirst();
        var local = localClock(s);
        var due = LocalDate.parse("2026-03-02");
        s.planAsResponsible(f.id(), "repair", owner.id(), due);
        s.clock.advance(Duration.between(s.clock.now(), Instant.parse("2026-03-03T00:30:00Z")));
        var sweep = new ExpireOverdueCorrectiveActions(s.findings, local, s.events, s.audit);
        int expired = sweep.sweep().size();
        var blockers = s.evaluateEligibility.assess(id).blockers();
        require(expired == 0 && !f.correctiveAction().overdueAndOpen(local.today()), "local deadline inclusive");
        require(blockers.stream().anyMatch(b -> b.getClass().getSimpleName().equals("OverdueOpenAction")), "UTC eligibility blocks");
        System.out.println("C-TIME-01 at=" + local.now() + " localDate=" + local.today() + " UTCDate=" + s.clock.today()
                + " sweepExpired=" + expired + " eligibility=" + blockers);
        s.actingAs(owner.id(), () -> s.reportExecution.report(f.id(), "done", List.of("proof")));
        var verifier = new VerifyCorrectiveAction(s.findings, local, s.events, s.audit, s.actors);
        s.actingAs(inspector.id(), () -> verifier.verify(f.id(), true, "verified"));
        require(f.correctiveAction().metItsDeadline(), "local verification is timely");
        require(s.evaluateEligibility.assess(id).blockers().isEmpty(), "closed action clears blockers");
        System.out.println("C-TIME-03 same instant verification metItsDeadline=true eligibilityAfterVerification=[]");
        var planner = new PlanCorrectiveAction(s.findings, s.audit, local, s.actors, s.events);
        // A separate pending obligation avoids the already-planned guard.
        s.rectifyClosedInspection.rectify(id, "remove reading error", List.of(new Correction.AnswerCorrection(DomainWorld.TEMPERATURE, Measurement.of("5", "c"))));
        s.rectifyClosedInspection.rectify(id, "restore observation", List.of(new Correction.AnswerCorrection(DomainWorld.TEMPERATURE, Measurement.of("20", "c"))));
        try {
            s.actingAs(owner.id(), () -> planner.plan(f.id(), "same-day repair", owner.id(), local.today()));
            throw new AssertionError("expected UTC date rejection");
        } catch (ar.edu.itba.dps.certification.domain.shared.DomainException e) {
            require(e.getMessage().contains("deadline cannot precede"), "expected past-date rule");
            System.out.println("C-TIME-02 localToday plan rejected=" + e.getMessage());
        }
        s.planAsResponsible(f.id(), "repair", owner.id(), s.clock.today());
        // Inspect the absent-subsystem rectification independently.
        var p = new FullSystem(); var pi = p.person("Inspector"); var po = p.person("Owner"); p.actAs(pi);
        p.publishSubsystemSchema(AssetType.FACILITY);
        var pa = p.registerAsset.register("Facility", AssetType.FACILITY, po.id(), "Here", Map.of(),
                Set.of(DomainWorld.ELECTRICAL), JurisdictionId.of("REFERENCE"));
        var pid = p.assignInspection.assign(pa.id(), pi.id(), p.clock.today()).id();
        p.startInspection.start(pid); p.closeInspection.close(pid);
        var inspection = p.inspections.require(pid);
        require(!inspection.requireRecord(DomainWorld.PRESSURE_VALVES).applicable(), "absent pressure");
        try {
            p.rectifyClosedInspection.rectify(pid, "invalid absent part", List.of(new Correction.AnswerCorrection(DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean"))));
            throw new AssertionError("expected missing evaluation crash");
        } catch (NoSuchElementException e) {
            require(inspection.requireRecord(DomainWorld.PRESSURE_VALVES).answer().isPresent(), "mutated before crash");
            require(inspection.rectifications().size() == 1, "rectification appended before crash");
            System.out.println("C-RECT-04 exception=" + e.getClass().getSimpleName() + " absentAnswerMutated=true rectifications=1");
        }
        // Nanosecond F2 startup boundaries, including retained inspections and tied schedules.
        var t = new FullSystem(); var ti = t.person("Inspector"); var to = t.person("Owner"); t.actAs(ti);
        var schema = t.createSchema.create("Lab", Set.of(AssetType.LABORATORY));
        t.openDraft.open(schema.id()); t.editDraft.addSection(schema.id(), Section.of("Docs", 1, DomainWorld.documentationCriterion()));
        var effective = t.clock.now().plusSeconds(10);
        t.publishSchemaVersion.publish(schema.id(), effective);
        var ta = t.asset("Lab", AssetType.LABORATORY, to);
        var early = t.assignInspection.assign(ta.id(), ti.id(), t.clock.today()).id();
        try { t.startInspection.start(early); throw new AssertionError("no effective version"); }
        catch (ar.edu.itba.dps.certification.domain.shared.DomainException e) { require(t.inspections.require(early).status() == InspectionStatus.ASSIGNED, "no mutation"); }
        t.clock.advance(Duration.between(t.clock.now(), effective)); t.startInspection.start(early); t.closeInspection.close(early);
        t.openDraft.open(schema.id()); t.editDraft.addSection(schema.id(), Section.of("Temperature", 2, DomainWorld.temperatureCriterion()));
        var boundary = effective.plusSeconds(10); t.publishSchemaVersion.publish(schema.id(), boundary);
        for (var offset : new long[]{-1, 0, 1}) {
            t.clock.advance(Duration.between(t.clock.now(), boundary.plusNanos(offset)));
            var iid = t.assignInspection.assign(ta.id(), ti.id(), t.clock.today()).id();
            int actual = t.startInspection.start(iid).requireFrozenSchemaVersionId().number();
            require(actual == (offset < 0 ? 1 : 2), "boundary version");
            require(t.inspections.require(early).requireFrozenSchemaVersionId().number() == 1, "old snapshot retained");
            System.out.println("C-F2-01 offsetNanos=" + offset + " startedVersion=" + actual + " oldVersion=1");
            t.closeInspection.close(iid);
        }
        t.openDraft.open(schema.id()); t.publishSchemaVersion.publish(schema.id(), boundary.plusSeconds(10));
        t.openDraft.open(schema.id()); t.publishSchemaVersion.publish(schema.id(), boundary.plusSeconds(10));
        require(schema.effectiveVersionAt(boundary.plusSeconds(10)).orElseThrow().number() == 4, "tie selects greatest number");
        System.out.println("C-F2-02 noEffective=start rejected; tiedFutureVersions=highest number 4");
        var a = new FullSystem(); var ai = a.person("Inspector"); var ao = a.person("Owner"); a.actAs(ai);
        var generic = a.createSchema.create("Generic", Set.of(AssetType.LABORATORY));
        a.openDraft.open(generic.id()); a.editDraft.addSection(generic.id(), Section.of("Docs", 1, DomainWorld.documentationCriterion()));
        a.publishSchemaVersion.publish(generic.id());
        a.openDraft.open(generic.id());
        a.editDraft.addSection(generic.id(), Section.of("Parts", 2, DomainWorld.electricalCriterion(), DomainWorld.pressureCriterion(), DomainWorld.buildingSafetyCriterion()));
        a.publishSchemaVersion.publish(generic.id(), a.clock.now().plus(Duration.ofDays(10)));
        var applicability = new ar.edu.itba.dps.certification.application.schema.usecase.ChangeSchemaApplicability(a.schemas, a.schemaApplicability, a.audit);
        applicability.applyTo(generic.id(), AssetType.FACILITY);
        var facility = a.asset("All-parts facility", AssetType.FACILITY, ao);
        var aid = a.assignInspection.assign(facility.id(), ai.id(), a.clock.today()).id();
        var started = a.startInspection.start(aid);
        a.recordAnswer.record(aid, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        a.attachEvidence.attach(aid, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "manual");
        a.closeInspection.close(aid);
        var decision = a.issueCertificate.issue(aid);
        require(facility.subsystems().size() == 3 && started.certifiableSubsystems().isEmpty(), "parts unevaluated");
        require(decision instanceof ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision.Issued, "global issued despite missing coverage");
        System.out.println("C-F1-F2-01 actualAssetParts=3 selectedVersion=" + started.requireFrozenSchemaVersionId().number()
                + " inspectedParts=0 directGlobal=ISSUED");
    }
}
