# Step 2 durable evidence

Reviewed HEAD: `8a4815960670ac1d467df4a8f0932c8932d2fab4`. No application/test-source changes.

## Executed core probes

Final run `012-step2-probes` executes [Step2Probe.java](Step2Probe.java) against baseline compiled production/test-fixture classes.

```powershell
& 'C:/Program Files/Java/jdk-26.0.2/bin/java.exe' --class-path 'core/target/classes;core/target/test-classes' docs/audits/2026-10-10-8a48159/evidence/Step2Probe.java
```

Time: 2026-10-10T19:28:52.1446791-03:00 to 2026-10-10T19:28:55.2443034-03:00; exit 0.

```text
C-TIME-01 at=2026-03-03T00:30:00Z localDate=2026-03-02 UTCDate=2026-03-03 sweepExpired=0 eligibility=[OverdueOpenAction[count=1]]
C-TIME-03 same instant verification metItsDeadline=true eligibilityAfterVerification=[]
C-TIME-02 localToday plan rejected=correction deadline cannot precede planning date
C-RECT-04 exception=NoSuchElementException absentAnswerMutated=true rectifications=1
C-F2-01 offsetNanos=-1 startedVersion=1 oldVersion=1
C-F2-01 offsetNanos=0 startedVersion=2 oldVersion=1
C-F2-01 offsetNanos=1 startedVersion=2 oldVersion=1
C-F2-02 noEffective=start rejected; tiedFutureVersions=highest number 4
C-F1-F2-01 actualAssetParts=3 selectedVersion=1 inspectedParts=0 directGlobal=ISSUED
```

The zone-aware Clock uses a controlled Instant and Buenos Aires LocalDate. Production use cases perform the comparisons; in-memory ports isolate the core. F2 probes use nanoseconds. No core probe proves JDBC or HTTP behavior.

## Executed HTTP and H2 probes

[step2_api_probe.py](step2_api_probe.py) starts the baseline hashed executable JAR, disables scheduled jobs, calls the actual HTTP API on port 18082, and terminates its owned process. It uses an isolated file H2 database under the run tree.

For a new reproduction, pass a fresh run-directory name as the final argument;
the script refuses an existing run directory to preserve prior evidence.

```powershell
python docs/audits/2026-10-10-8a48159/evidence/step2_api_probe.py 013-step2-api
```

Time: 2026-10-10T19:29:16.403604-03:00 to 2026-10-10T19:29:27.457378-03:00; probe exit 0; JAR SHA-256 `91a192ed7dea02a09f3dca1a96ff4c8f73281a714ec132d535872dee11beb076`.

```text
C:/Program Files/Java/jdk-26.0.2/bin/java.exe -jar app\target\certiflow-app-1.0.0-SNAPSHOT-exec.jar --server.port=18082 --spring.datasource.url=jdbc:h2:file:./target/audit-evidence/2026-10-10-8a48159/013-step2-api/db/certiflow --certiflow.jobs.enabled=false
```

```json
{
  "rectification": {
    "scenario": "C-RECT-04",
    "HTTP": 500,
    "code": "INTERNAL_ERROR",
    "inspectionUnchanged": true,
    "auditUnchanged": true
  },
  "coverage": {
    "scenario": "C-F1-F2-01",
    "assetParts": [
      "electrical installation",
      "pressure system"
    ],
    "selectedVersion": "13e52336-0d0d-4c36-88ba-aabcd4b53121#v1",
    "inspectionParts": [],
    "certificateScope": "GLOBAL",
    "certificateStatus": "VALID"
  }
}
```

C-RECT-04 asserts full inspection JSON equality and filtered inspection audit equality before/after the failed request. This establishes rollback for the actual request, not universal transaction correctness. C-F1-F2-01 rereads the certificate from H2 through GET; no mock API or repository is used. Neither probe reopens the file after process restart.

### Selected request and response excerpts

```json
{
  "method": "POST",
  "path": "/inspections/5ef2cf6f-b557-4a2a-9619-4490de0f28a8/rectifications",
  "request": {
    "reason": "Absent pressure criterion",
    "corrections": [
      {
        "type": "ANSWER",
        "criterionId": "PRES",
        "answer": {
          "type": "OPTION",
          "option": "clean"
        }
      }
    ]
  },
  "actor": "ed12cc32-64dd-4b33-b48e-c4b2ea049e7c",
  "status": 500,
  "response": {
    "status": 500,
    "code": "INTERNAL_ERROR",
    "message": "unexpected error",
    "details": [],
    "timestamp": "2026-10-10T22:29:27.047818800Z"
  }
}
```

```json
{
  "method": "POST",
  "path": "/schemas/13e52336-0d0d-4c36-88ba-aabcd4b53121/applicability",
  "request": {
    "assetType": "FACTORY"
  },
  "actor": null,
  "status": 200,
  "response": {
    "id": "13e52336-0d0d-4c36-88ba-aabcd4b53121",
    "name": "Generic",
    "assetTypes": [
      "LABORATORY",
      "FACTORY"
    ],
    "draft": null,
    "versions": [
      {
        "id": "13e52336-0d0d-4c36-88ba-aabcd4b53121#v1",
        "number": 1,
        "publishedAt": "2026-10-10T22:29:27.122250700Z",
        "effectiveFrom": "2026-10-10T22:29:27.122250700Z",
        "sections": [
          {
            "name": "Common",
            "order": 1,
            "criteria": [
              {
                "id": "HK",
                "rule": {
                  "type": "OPTIONS",
                  "yes": null,
                  "no": null,
                  "options": {
                    "clean": {
                      "code": "OK",
                      "result": "APPROVED",
                      "severity": null,
                      "description": "clean"
                    }
                  },
                  "unit": null,
                  "minimum": null,
                  "maximum": null,
                  "bands": null
                },
                "subsystem": null,
                "evidence": []
              }
            ]
          }
        ]
      },
      {
        "id": "13e52336-0d0d-4c36-88ba-aabcd4b53121#v2",
        "number": 2,
        "publishedAt": "2026-10-10T22:29:27.173533100Z",
        "effectiveFrom": "2026-10-20T22:29:27.166204Z",
        "sections": [
          {
            "name": "Common",
            "order": 1,
            "criteria": [
              {
                "id": "HK",
                "rule": {
                  "type": "OPTIONS",
                  "yes": null,
                  "no": null,
                  "options": {
                    "clean": {
                      "code": "OK",
                      "result": "APPROVED",
                      "severity": null,
                      "description": "clean"
                    }
                  },
                  "unit": null,
                  "minimum": null,
                  "maximum": null,
                  "bands": null
                },
                "subsystem": null,
                "evidence": []
              }
            ]
          },
          {
            "name": "Future parts",
            "order": 2,
            "criteria": [
              {
                "id": "ELEC",
                "rule": {
                  "type": "OPTIONS",
                  "yes": null,
                  "no": null,
                  "options": {
                    "clean": {
                      "code": "OK",
                      "result": "APPROVED",
                      "severity": null,
                      "description": "clean"
                    }
                  },
                  "unit": null,
                  "minimum": null,
                  "maximum": null,
                  "bands": null
                },
                "subsystem": "electrical installation",
                "evidence": []
              },
              {
                "id": "PRES",
                "rule": {
                  "type": "OPTIONS",
                  "yes": null,
                  "no": null,
                  "options": {
                    "clean": {
                      "code": "OK",
                      "result": "APPROVED",
                      "severity": null,
                      "description": "clean"
                    }
                  },
                  "unit": null,
                  "minimum": null,
                  "maximum": null,
                  "bands": null
                },
                "subsystem": "pressure system",
                "evidence": []
              },
              {
                "id": "SAFE",
                "rule": {
                  "type": "OPTIONS",
                  "yes": null,
                  "no": null,
                  "options": {
                    "clean": {
                      "code": "OK",
                      "result": "APPROVED",
                      "severity": null,
                      "description": "clean"
                    }
                  },
                  "unit": null,
                  "minimum": null,
                  "maximum": null,
                  "bands": null
                },
                "subsystem": "building safety",
                "evidence": []
              }
            ]
          }
        ]
      }
    ],
    "effectiveVersion": 1
  }
}
```

```json
{
  "method": "POST",
  "path": "/inspections/780a9bec-f837-4a5d-94fc-3bafc03d63b0/certificates",
  "request": {},
  "actor": null,
  "status": 201,
  "response": {
    "outcome": "ISSUED",
    "certificate": {
      "id": "3d1b8bd5-886f-4216-811b-26de087a4bc4",
      "assetId": "e3db316d-e315-4101-9402-674605c65b38",
      "backingInspectionId": "780a9bec-f837-4a5d-94fc-3bafc03d63b0",
      "schemaVersion": "13e52336-0d0d-4c36-88ba-aabcd4b53121#v1",
      "scope": "GLOBAL",
      "subsystem": null,
      "status": "VALID",
      "mode": "REGULAR",
      "issuedAt": "2026-10-10T22:29:27.338082800Z",
      "expiresAt": "2027-10-10T22:29:27.338082800Z",
      "previousCertificateId": null,
      "policy": {
        "reference": {
          "jurisdiction": "REFERENCE",
          "policyId": "reference",
          "revision": 1
        },
        "blockingSeverities": [],
        "allowsConditional": true,
        "regularDuration": "P12M",
        "conditionalDuration": "P12M",
        "restrictions": [
          "REJECTIONS_MUST_BE_CORRECTED"
        ]
      },
      "suspensions": [],
      "unresolvedCauses": []
    }
  }
}
```

## Focused existing tests

Run `009-step2-core`: root working directory; process-local JAVA_HOME `C:/Program Files/Java/jdk-26.0.2`; no new test source.

```powershell
mvn --batch-mode --no-transfer-progress -pl core verify '-Dtest=SchemaPublicationTest,SchemaValueEdgeTest,NumericAndYesNoRuleEdgeTest,CriterionEvaluatorTest,PolicyAwareCertificateLifecycleTest,AllSubsystemsMustBeInForceTest,CertificateSuspensionTest,FrozenSchemaVersionTest' '-Dit.test=FutureEffectiveSchemaIT,PartialCertificationIT,JurisdictionCertificationIT,RectificationIT,RecurringNonConformityIT,RenewalIT,ReviewCorrectionsIT,SecondReviewIT,ReportingIT,InspectionExecutionIT,CertificationLifecycleIT'
```

Time: 2026-10-10T19:26:48.4421749-03:00 to 2026-10-10T19:27:46.5983622-03:00; exit 0; BUILD SUCCESS.

| Executed class | Tests | Failures/errors/skips |
|---|---:|---|
| FrozenSchemaVersionTest | 4 | 0/0/0 |
| CertificateSuspensionTest | 13 | 0/0/0 |
| AllSubsystemsMustBeInForceTest | 9 | 0/0/0 |
| PolicyAwareCertificateLifecycleTest | 3 | 0/0/0 |
| CriterionEvaluatorTest | 6 | 0/0/0 |
| NumericAndYesNoRuleEdgeTest | 8 | 0/0/0 |
| SchemaPublicationTest | 15 | 0/0/0 |
| SchemaValueEdgeTest | 11 | 0/0/0 |
| CertificationLifecycleIT | 9 | 0/0/0 |
| FutureEffectiveSchemaIT | 1 | 0/0/0 |
| InspectionExecutionIT | 6 | 0/0/0 |
| JurisdictionCertificationIT | 6 | 0/0/0 |
| PartialCertificationIT | 27 | 0/0/0 |
| RectificationIT | 10 | 0/0/0 |
| RecurringNonConformityIT | 8 | 0/0/0 |
| RenewalIT | 6 | 0/0/0 |
| ReportingIT | 8 | 0/0/0 |
| ReviewCorrectionsIT | 20 | 0/0/0 |
| SecondReviewIT | 12 | 0/0/0 |

**182 core tests passed (69 unit, 113 integration), zero failures/errors/skips.** XML was selected by executed class and timestamp, then snapshotted; unrelated/stale reports were excluded. This is core-only focused execution, not a new full-system verification. Full run 002 remains baseline evidence because application code and JAR match. No current-only JaCoCo percentage is claimed.

## Code paths for new findings

Line numbers below belong to reviewed HEAD, not to a proposed repair.

[SchemaApplicability.java:19](../../../../core/src/main/java/ar/edu/itba/dps/certification/domain/schema/SchemaApplicability.java#L19)

```text
19:                 .forEach(type -> requireAvailable(type, covered));
20:         return new InspectionSchema(id, name, types);
21:     }
22: 
23:     public void applyTo(InspectionSchema schema, AssetType type, Optional<SchemaId> registeredOwner) {
24:         requireAvailable(type, Validate.required(registeredOwner, "registered schema owner"));
25:         requireVersionEvaluatesEveryPartOf(schema, type);
26:         schema.applyTo(type);
27:     }
```

[SchemaApplicability.java:43](../../../../core/src/main/java/ar/edu/itba/dps/certification/domain/schema/SchemaApplicability.java#L43)

```text
43:                 "source schema must remain applicable to at least one asset type");
44:         Validate.ensure(!target.appliesTo(type), "target schema already applies to asset type " + type);
45:         Validate.ensure(owner.isPresent() && owner.get().equals(source.id()),
46:                 "source schema must be the registered owner of the asset type");
47:         Validate.ensure(target.latestPublishedVersion().isPresent(),
48:                 "the replacement schema must have a published version");
49:         requireVersionEvaluatesEveryPartOf(target, type);
50:         source.stopApplyingTo(type);
51:         target.applyTo(type);
52:     }
53: 
54:     private void requireVersionEvaluatesEveryPartOf(InspectionSchema schema, AssetType type) {
55:         schema.latestPublishedVersion().ifPresent(version -> {
56:             Set<Subsystem> unevaluated = type.subsystems().stream()
57:                     .filter(subsystem -> !version.declaredSubsystems().contains(subsystem))
58:                     .collect(Collectors.toCollection(LinkedHashSet::new));
59:             Validate.ensure(unevaluated.isEmpty(), "version " + version.id() + " of schema "
60:                     + schema.id() + " evaluates no criterion for " + unevaluated
61:                     + ", which assets of type " + type + " may have");
62:         });
63:     }
64: 
65:     private void requireAvailable(AssetType type, Set<AssetType> alreadyCoveredAssetTypes) {
66:         Validate.required(type, "asset type");
```

[StartInspection.java:40](../../../../core/src/main/java/ar/edu/itba/dps/certification/application/inspection/usecase/StartInspection.java#L40)

```text
40:     public Inspection start(InspectionId inspectionId) {
41:         var actor = actors.requireUser();
42:         Inspection inspection = inspections.require(inspectionId);
43:         AssetType assetType = assets.assetTypeOf(inspection.assetId());
44:         Instant now = clock.now();
45:         SchemaVersion version = schemas.effectiveVersionFor(assetType, now)
46:                 .orElseThrow(() -> new DomainException("asset type " + assetType
47:                         + " has no published schema version effective at " + now + ", so the inspection cannot start"));
48:         AssetSnapshot snapshot = assets.captureSnapshot(inspection.assetId());
49:         inspection.start(actor.partyId(), version, snapshot, now);
50:         inspections.save(inspection);
51:         audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
52:                 AuditAction.INSPECTION_STARTED, AuditDetail.stateChanged("ASSIGNED", "IN_PROGRESS"));
53:         return inspection;
54:     }
```

[Inspection.java:100](../../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L100)

```text
100:                 .collect(Collectors.toCollection(LinkedHashSet::new));
101:     }
102: 
103:     public boolean criterionWeighsOn(CriterionId criterionId, Subsystem subsystem) {
104:         Validate.required(subsystem, "subsystem");
105:         return frozenSchemaVersion == null
106:                 || frozenSchemaVersion.criterionWeighsOn(criterionId, subsystem);
107:     }
108: 
109:     public Optional<AssetSnapshot> assetSnapshot() {
110:         return Optional.ofNullable(assetSnapshot);
```

[Inspection.java:240](../../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L240)

```text
240:     public Rectification rectify(RectificationId rectificationId,
241:             PartyId author, Instant at, String reason, List<Correction> corrections) {
242:         Validate.ensure(status.closed(), "only a closed inspection can be rectified");
243:         requireAssignedInspector(author, "rectify");
244:         Validate.required(rectificationId, "rectification id");
245:         Validate.required(at, "rectification instant");
246:         Validate.requiredText(reason, "rectification reason");
247:         Validate.requiredNonEmpty(corrections, "corrections");
248:         Validate.ensure(!at.isBefore(closedAt), "rectification cannot precede closure");
249:         Validate.ensure(rectifications.stream().noneMatch(existing -> existing.id().equals(rectificationId)),
250:                 "rectification " + rectificationId + " is already recorded");
251:         corrections = List.copyOf(corrections);
252:         corrections.forEach(correction -> rejectIfInapplicable(frozenSchemaVersion, correction));
253: 
254:         List<RectificationChange> changes = new ArrayList<>();
255:         for (Correction correction : corrections) {
256:             changes.add(apply(correction));
257:         }
258:         Rectification rectification =
259:                 new Rectification(rectificationId, author, at, reason, changes);
260:         rectifications.add(rectification);
261:         for (CriterionId criterionId : rectification.affectedCriteria()) {
262:             CriterionRecord record = requireRecord(criterionId);
263:             CriterionEvaluation previous = record.currentEvaluation().orElseThrow();
264:             CriterionEvaluation current = evaluator.evaluate(frozenSchemaVersion.requireCriterion(criterionId),
265:                     record, at).asRectificationOf(rectificationId, at);
266:             if (previous.result() != current.result() || !previous.reasons().equals(current.reasons())) {
267:                 recordEvaluationProducedBy(rectification, criterionId, current);
268:                 // Announced whenever the evaluation changes, not only the result: a rejection that
269:                 // comes back with different reasons is a new non-conformity that no verified
270:                 // correction covers, and the certificate it backs must react to it.
271:                 pendingEvents.add(new CriterionResultRevised(id, criterionId, previous.result(),
272:                         current.result(), rectificationId, reason, at));
273:             }
274:         }
275:         return rectification;
276:     }
277: 
278:     private void rejectIfInapplicable(SchemaVersion version, Correction correction) {
279:         switch (correction) {
280:             case Correction.AnswerCorrection answerCorrection -> {
281:                 requireRecord(answerCorrection.criterionId());
282:                 version.requireCriterion(answerCorrection.criterionId()).rule()
283:                         .admissibilityViolation(answerCorrection.answer())
284:                         .ifPresent(violation -> {
285:                             throw new DomainException("answer refused for criterion "
286:                                     + answerCorrection.criterionId() + ": " + violation);
287:                         });
288:             }
289:             case Correction.EvidenceReferenceCorrection evidenceCorrection ->
290:                     requireRecord(evidenceCorrection.criterionId())
```

[Inspection.java:296](../../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L296)

```text
296:     private RectificationChange apply(Correction correction) {
297:         return switch (correction) {
298:             case Correction.AnswerCorrection answerCorrection -> {
299:                 CriterionRecord record = requireRecord(answerCorrection.criterionId());
300:                 String previous = record.answer().map(Answer::describe).orElse(null);
301:                 record.recordAnswer(answerCorrection.answer());
302:                 yield new RectificationChange.AnswerCorrected(answerCorrection.criterionId(), previous,
303:                         answerCorrection.answer().describe());
304:             }
305:             case Correction.EvidenceReferenceCorrection evidenceCorrection -> {
306:                 CriterionRecord record = requireRecord(evidenceCorrection.criterionId());
307:                 String previous = record.requireEvidence(evidenceCorrection.evidenceId()).reference();
308:                 record.replaceReference(evidenceCorrection.evidenceId(), evidenceCorrection.reference());
```

[Inspection.java:400](../../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L400)

```text
400:     }
401: 
402:     private void requireStatus(InspectionStatus expected, String operation) {
403:         if (status != expected) {
404:             throw new DomainException("cannot " + operation + " inspection " + id + " while it is "
405:                     + status);
406:         }
407:     }
408: 
409:     @Override
410:     public boolean equals(Object other) {
411:         return other instanceof Inspection that && id.equals(that.id);
412:     }
413: 
414:     @Override
415:     public int hashCode() {
```

[CertificateFactory.java:213](../../../../core/src/main/java/ar/edu/itba/dps/certification/application/certification/CertificateFactory.java#L213)

```text
213:     private CertificationContext contextFor(Inspection inspection, CertificateScope scope, Instant at) {
214:         var liveCertificate = certificates.findNonExpiredForAsset(inspection.assetId(), scope)
215:                 .filter(certificate -> certificate.coversMoment(at))
216:                 .filter(certificate -> !certificate.backingInspectionId().equals(inspection.id()))
217:                 .map(Certificate::id);
218:         return new CertificationContext(inspection, findings.findingsOf(inspection.id()),
219:                 at.atZone(ZoneOffset.UTC).toLocalDate(), scope,
220:                 laterClosedInspectionOf(inspection), liveCertificate);
221:     }
222: 
223:     private void requireWholeAssetSchema(Inspection inspection) {
224:         Validate.ensure(inspection.certifiableSubsystems().isEmpty(),
225:                 "inspection " + inspection.id() + " certifies the subsystems "
226:                         + inspection.certifiableSubsystems() + " separately, so derive the global "
227:                         + "certificate from them instead of issuing one");
228:     }
229: 
230:     private void requireDeclaredSubsystem(Inspection inspection, Subsystem subsystem) {
231:         Validate.required(subsystem, "subsystem");
232:         Validate.ensure(inspection.certifiableSubsystems().contains(subsystem),
233:                 "inspection " + inspection.id() + " does not certify subsystem " + subsystem
```

[PlanCorrectiveAction.java:33](../../../../core/src/main/java/ar/edu/itba/dps/certification/application/finding/usecase/PlanCorrectiveAction.java#L33)

```text
33: 
34:     public Finding plan(FindingId findingId, String work, PartyId executor, LocalDate dueDate) {
35:         var actor = actors.requireUser();
36:         Finding finding = findings.require(findingId);
37:         CorrectionPlan plan = new CorrectionPlan(work, executor, dueDate);
38:         var at = clock.now();
39:         finding.planCorrection(actor.partyId(), plan, at.atZone(ZoneOffset.UTC).toLocalDate(), at);
40:         findings.save(finding);
41:         audit.recordAs(actor, AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
```

[VerifyCorrectiveAction.java:35](../../../../core/src/main/java/ar/edu/itba/dps/certification/application/finding/usecase/VerifyCorrectiveAction.java#L35)

```text
35:     public Finding verify(FindingId findingId, boolean satisfactory, String reason) {
36:         var actingUser = actors.requireUser();
37:         PartyId actor = actingUser.partyId();
38:         Finding finding = findings.require(findingId);
39:         Instant at = clock.now();
40:         boolean closed = finding.concludeCorrection(
41:                 new Verification(satisfactory, reason, actor, at), clock.today());
42:         findings.save(finding);
43:         audit.recordAs(actingUser, AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
```

[ExpireOverdueCorrectiveActions.java:31](../../../../core/src/main/java/ar/edu/itba/dps/certification/application/finding/usecase/ExpireOverdueCorrectiveActions.java#L31)

```text
31:     public List<Finding> sweep() {
32:         Instant at = clock.now();
33:         List<Finding> expired = new ArrayList<>();
34:         for (Finding finding : findings.findWithOpenActions()) {
35:             if (!finding.expireCorrectionIfOverdue(clock.today(), at)) {
36:                 continue;
37:             }
```

[CertificationReactions.java:61](../../../../core/src/main/java/ar/edu/itba/dps/certification/application/certification/CertificationReactions.java#L61)

```text
61:         }
62:         Inspection inspection = inspections.require(affected.get().inspectionId());
63:         var at = clock.now();
64:         var currentFindings = findings.findingsOf(inspection.id());
65:         for (Certificate certificate : backing) {
66:             if (!weighsOn(inspection, affected.get().criterionId(), certificate)) {
67:                 continue;
68:             }
69:             var context = new CertificationContext(
70:                     inspection, currentFindings, at.atZone(ZoneOffset.UTC).toLocalDate(),
71:                     certificate.scope(), Optional.empty(), Optional.empty());
72:             lifecycle.reconcile(certificate, context, at).ifPresent(change -> {
73:                 certificates.save(change.certificate());
```

[SystemClock.java:16](../../../../app/src/main/java/ar/edu/itba/dps/certification/app/config/SystemClock.java#L16)

```text
16: 
17:     @Override
18:     public Instant now() {
19:         return Instant.now();
20:     }
21: 
22:     @Override
23:     public LocalDate today() {
24:         return LocalDate.now(zone);
25:     }
```

[ApiExceptionHandler.java:34](../../../../app/src/main/java/ar/edu/itba/dps/certification/app/web/ApiExceptionHandler.java#L34)

```text
34:     @ExceptionHandler(DomainException.class)
35:     ResponseEntity<ApiError> businessRule(DomainException e) {
36:         return reply(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE", e.getMessage());
37:     }
38: 
39:     @ExceptionHandler(PublicationRefusedException.class)
```

[ApiExceptionHandler.java:72](../../../../app/src/main/java/ar/edu/itba/dps/certification/app/web/ApiExceptionHandler.java#L72)

```text
72:         }
73:         log.error("unexpected failure", e);
74:         return reply(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "unexpected error");
75:     }
76: 
77:     /** Spring's own client errors (unknown route, wrong method, unsupported media type) keep their status. */
78:     private static ResponseEntity<ApiError> standardClientError(ErrorResponse error) {
79:         int status = error.getStatusCode().value();
```

## Limits and earlier attempts

Run 007 exited 1 after proving the date and rectification failures: the F2 harness tried to assign a second inspection while one was open. This was an audit-fixture setup error; the final probe closes the earlier inspection before assigning another. Run 008 exited 0 for the corrected probe; run 011 exited 0 after adding local verification evidence; run 012 exited 0 after adding the F1/F2 coverage case. These are incremental evidence scripts, not product changes. Run 010 successfully established HTTP rectification rollback; run 013 added persisted unsafe global issuance. The final source files contain all assertions; earlier logs correspond to earlier versions of those probes.

No browser, injected-clock HTTP deadline/startup probe, concurrent-write/retry/restart/crash exercise, or repair validation was run in Step 2. Exact missing scenarios remain in correctness and AUD-005.
