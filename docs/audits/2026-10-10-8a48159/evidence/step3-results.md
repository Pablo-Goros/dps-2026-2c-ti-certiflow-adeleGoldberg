# Step 3 durable evidence

Reviewed HEAD: `8a4815960670ac1d467df4a8f0932c8932d2fab4`. No application,
test-source or contract changes. All executions used repository root and JDK
26.0.2. [Scenario report](../failure-scenarios.md) records limits for each result.

## Focused existing tests: run 015

Time: 2026-10-10T19:38:42.394069-03:00 to 2026-10-10T19:40:22.652562-03:00, exit 0 / BUILD SUCCESS.
Exact PowerShell command (JAVA_HOME set only in that process):

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-26.0.2'
mvn --batch-mode --no-transfer-progress verify '-Dskip.frontend=true' '-Dtest=MaintenanceJobsTest,NotifyCorrectiveActionExpiryTest' '-Dit.test=RenewalIT,CertificationLifecycleIT,OutboxIT,JdbcTransactionsIT,JdbcLifecycleIT,JdbcAuditTrailIT,DatabaseConstraintsIT,WebhookNotificationSenderIT,ConcurrencyApiIT,ProcessesApiIT,AdminApiIT,CertificationApiIT,NotificationWebhookApiIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dfailsafe.failIfNoSpecifiedTests=false'
```

| Module/report | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| core/surefire-reports | 5 | 0 | 0 | 0 |
| core/failsafe-reports | 15 | 0 | 0 | 0 |
| infrastructure/failsafe-reports | 31 | 0 | 0 | 0 |
| app/surefire-reports | 5 | 0 | 0 | 0 |
| app/failsafe-reports | 17 | 0 | 0 | 0 |

**73 backend tests**, zero failures/errors/skips. Fifteen selected current-run
XML reports were copied into run 015; every selected XML's mtime lies within
start/end. [Durable XML summary](step3-test-summary.json) preserves class names,
timestamps and counts. This is focused backend verification: frontend was skipped,
and this is not a new full-system validation or coverage percentage.

Run 014's unquoted dotted PowerShell properties were split before Maven;
`.frontend=true` was treated as an unknown lifecycle phase. No tests ran. Run 015
quoted the arguments. Run 018's HTTP helper shadowed its concurrent module and
stopped after the repeat probes; run 019 corrected this but stopped on its forced
restart all-audit equality assertion. These are separately recorded executions,
not passing complete probes. The underlying loss in 019 was observed, not hidden
as a helper failure. Final runs 020/022 explicitly record unequal retained state.

## H2 failure probes: final run 021

The [Java source](Step3Probe.java) uses production transactions, persistence,
outbox and dispatchers. The JDBC pause wrapper delegates all statements to real
H2 and stops only the delayed failure statement to control the race.

```powershell
& 'C:/Program Files/Java/jdk-26.0.2/bin/java.exe' --class-path 'core/target/classes;infrastructure/target/classes;target/audit-evidence/2026-10-10-8a48159/016-step3-probes/BOOT-INF/lib/*' docs/audits/2026-10-10-8a48159/evidence/Step3Probe.java probes
```

Dependencies were extracted from the full-baseline executable JAR copy into the
ignored run 016 directory using Python zipfile. Run 016 first passed the same
probe; final timestamped run 021 exited 0. A zero exit means assertions of the
observed defects and rollback outcomes passed, not that the application is correct.

Time: 2026-10-10T19:44:56.7056025-03:00 to 2026-10-10T19:45:06.1760827-03:00.

```text
F-ROLLBACK-01/02 aggregate+audit+outbox rollback; handler writes rollback and pending attempts=1
F-OUTBOX-06 mixed batch: delivered=2 failing row attempts=2 in ONE dispatchPending call
F-OUTBOX-07 50 failing oldest rows: healthy row 51 unattempted; delivered=0 pending=51
F-OUTBOX-08 failed dispatcher resumes AFTER competing success: DONE->PENDING; successful effects=2 audit entries=2
```

## Ordinary file-H2 reopen and recovery: run 017

The same Java command above was executed in two separate JVMs with respectively
`seed` and `recover` arguments, each followed by:
`jdbc:h2:file:./target/audit-evidence/2026-10-10-8a48159/017-step3-restart/db/certiflow`.
Both exited normally with code 0. Time:
2026-10-10T19:40:59.1763395-03:00 to 2026-10-10T19:41:09.5980398-03:00.

```text
F-RESTART-01 seed committed party=1 audit=1 pending=1; normal JVM exit
F-RESTART-01 fresh JVM reconstructed party/audit/event; recovery delivered=1 repeat=0 DONE=1
```

This is ordinary process reopening for a small persisted fixture and pending
event. It does not establish certificate/policy-revision workflows, in-flight
crashes or machine/storage-failure guarantees.

## Real packaged API/file-H2 probes: final run 022

```powershell
python docs/audits/2026-10-10-8a48159/evidence/step3_api_probe.py 022-step3-api
```

Time: 2026-10-10T19:46:24.910954-03:00 to 2026-10-10T19:46:45.722674-03:00, probe exit 0. The script starts two
packaged processes on port 18083 with jobs disabled and one unique file H2.
Full command/configuration and results: [metadata](step3-api-results.json).
[Selected requests/replies](step3-http-excerpts.json) preserve concurrent
assignment and transfer responses; complete HTTP transcript and app logs remain
in the ignored run directory. No browser execution occurred.

```json
{
  "F-REPEAT-03": {
    "repeatPublicationHTTP": 422,
    "auditUnchanged": true
  },
  "F-REPEAT-01/02": {
    "repeatCloseUnchanged": true,
    "repeatIssue": "ALREADY_ISSUED",
    "auditUnchanged": true,
    "prematureRenewalHTTP": 422
  },
  "F-REPEAT-04": {
    "repeatVerificationHTTP": 422,
    "findingAuditOutboxUnchanged": true
  },
  "F-CONCUR-03": {
    "0": {
      "HTTP": [
        422,
        201,
        422,
        201,
        422,
        201
      ],
      "storedOpen": 3
    },
    "1": {
      "HTTP": [
        201,
        201,
        201,
        201,
        422,
        422
      ],
      "storedOpen": 4
    },
    "2": {
      "HTTP": [
        422,
        201,
        201,
        422,
        422,
        422
      ],
      "storedOpen": 2
    }
  },
  "F-CONCUR-04": {
    "HTTP": [
      409,
      422,
      200,
      422,
      409,
      409
    ],
    "equipmentOwners": [
      "bdede5fb-7b71-4952-ac20-1df2f8524e52"
    ]
  },
  "F-RESTART-02": {
    "equality": {
      "/inspections/52cc0265-0ae9-4e17-95aa-0a3032771520": true,
      "/inspections/52cc0265-0ae9-4e17-95aa-0a3032771520/act": true,
      "/certificates/af6e588d-efaf-41ec-a079-e0d8c17e8bc5": true,
      "/certificates/af6e588d-efaf-41ec-a079-e0d8c17e8bc5/report": true,
      "/audit": false,
      "/admin/outbox": true,
      "/schemas": false
    },
    "repeatIssueAfterReopen": "ALREADY_ISSUED",
    "termination": "Windows terminate after requests completed; not graceful JVM shutdown or in-flight crash test"
  }
}
```

Assignment requests created 3, 4 and 2 open inspections on three fresh assets.
Transfer kept one owner with one successful 200, three 409 and two 422 replies.
Earlier run 019 transfers were all refused because the fixture tried removing
the source's last type; it is not evidence of concurrent valid transfer. Runs
020/022 correct that fixture by retaining LABORATORY on the source.

On forced Windows termination after all requests completed, then reopening,
audit count was **44 before / 31 after**, schemas
**3 before / 1 after**. The recent transfer was
absent; the source regained EQUIPMENT. Certificate/inspection/report/outbox
snapshots matched. This is a limited forced-stop observation, not a graceful
shutdown or a failure injected during commit. Run 019 lost one audit entry;
run 020 retained only 12 of 33 audit entries. Loss quantity varies; no precise
flush mechanism or database corruption is claimed. See AUD-015.

## Build-artifact provenance

The full-run-002 JAR was copied before run 015 to
`target/audit-evidence/2026-10-10-8a48159/014-step3-tests/baseline-exec.jar`.
SHA-256 used in every Step 3 packaged probe:
`91a192ed7dea02a09f3dca1a96ff4c8f73281a714ec132d535872dee11beb076`.
Focused backend run 015 repackaged the default `app/target` JAR, whose new hash is
`9bc535f7b10d16f243da9794d639ce3be82448d1e370b741d9d91c951261c2f6`.
That file is not attributed to full run 002. Step 4 should use the preserved
full-baseline copy, or run full verification again and record the new artifact.

## Relevant source excerpts at reviewed HEAD

### `infrastructure/src/main/java/ar/edu/itba/dps/certification/infrastructure/events/OutboxDispatcher.java`

```text
45:         if (busy.isHeldByCurrentThread() || !busy.tryLock()) {
46:             return 0;
47:         }
48:         try {
49:             int delivered = 0;
50:             int deliveredInPass;
51:             do {
52:                 deliveredInPass = 0;
53:                 for (JdbcEventOutbox.Pending row : pendingSafely()) {
54:                     if (deliver(row)) {
55:                         deliveredInPass++;
56:                     }
57:                 }
58:                 delivered += deliveredInPass;
59:             } while (deliveredInPass > 0);
60:             return delivered;
61:         } finally {
62:             busy.unlock();
63:         }
64:     }
65: 
66:     private List<JdbcEventOutbox.Pending> pendingSafely() {
67:         try {
68:             return outbox.pending(BATCH);
69:         } catch (RuntimeException e) {
70:             LOG.log(System.Logger.Level.ERROR, "could not read the event outbox", e);
71:             return List.of();
72:         }
73:     }
74: 
75:     private boolean deliver(JdbcEventOutbox.Pending row) {
76:         try {
77:             return transactions.execute(() -> {
78:                 if (!outbox.claim(row.seq())) {
79:                     return false;
80:                 }
81:                 DomainEvent event = row.event();
82:                 handlers.forEach(handler -> handler.handle(event));
83:                 return true;
84:             });
85:         } catch (RuntimeException failure) {
86:             LOG.log(System.Logger.Level.WARNING,
87:                     "event " + row.seq() + " (" + row.eventType() + ") failed: " + failure, failure);
88:             try {
89:                 transactions.execute(() -> outbox.recordFailure(row.seq(), String.valueOf(failure), maxAttempts));
90:             } catch (RuntimeException e) {
91:                 LOG.log(System.Logger.Level.ERROR, "could not record the failure of event " + row.seq(), e);
92:             }
93:             return false;
94:         }
```

### `infrastructure/src/main/java/ar/edu/itba/dps/certification/infrastructure/persistence/jdbc/JdbcEventOutbox.java`

```text
47:                 event.getClass().getName(), PENDING, 0, System.currentTimeMillis(), codec.write(event));
48:     }
49: 
50:     /** Oldest pending rows first. */
51:     public List<Pending> pending(int limit) {
52:         return db.query("SELECT seq, event_type, attempts, doc FROM domain_event_outbox "
53:                         + "WHERE status = 'PENDING' ORDER BY seq FETCH FIRST " + Math.max(1, limit) + " ROWS ONLY",
54:                 row -> new Pending(row.getLong(1), row.getString(2), row.getInt(3), row.getString(4), codec));
55:     }
56: 
57:     /**
58:      * Takes the row for the running transaction: true for exactly one caller. Another transaction
59:      * trying the same row waits for this one to end and then finds it no longer pending.
60:      */
61:     public boolean claim(long seq) {
62:         return db.update("UPDATE domain_event_outbox SET status = 'DONE' WHERE seq = ? AND status = 'PENDING'", seq) == 1;
63:     }
64: 
65:     /** Counts a failed attempt; after {@code maxAttempts} the row is parked as DEAD instead of retried forever. */
66:     public void recordFailure(long seq, String error, int maxAttempts) {
67:         String shortened = error == null ? "" : error.substring(0, Math.min(error.length(), 900));
68:         db.update("UPDATE domain_event_outbox SET attempts = attempts + 1, last_error = ?, "
69:                         + "status = CASE WHEN attempts + 1 >= ? THEN 'DEAD' ELSE 'PENDING' END WHERE seq = ?",
70:                 shortened, maxAttempts, seq);
71:     }
72: 
73:     /** Gives dead rows a fresh set of attempts; returns how many were revived. */
74:     public int retryDead() {
75:         return db.update("UPDATE domain_event_outbox SET status = 'PENDING', attempts = 0 WHERE status = 'DEAD'");
76:     }
```

### `core/src/main/java/ar/edu/itba/dps/certification/application/inspection/usecase/AssignInspection.java`

```text
36:     public Inspection assign(AssetId assetId, PartyId inspector, LocalDate expectedDate) {
37:         assets.assetTypeOf(assetId);
38:         PartyId assignedInspector = parties.require(inspector).asInspector();
39:         inspections.findNonClosedByAsset(assetId).ifPresent(open -> {
40:             throw new DomainException("asset " + assetId + " already has inspection " + open.id()
41:                     + " in progress");
42:         });
43:         Inspection inspection =
44:                 new Inspection(new InspectionId(ids.newIdentifier()), assetId, assignedInspector, expectedDate);
45:         inspections.save(inspection);
46:         audit.record(AuditedElementRef.inspection(inspection.id().value()),
47:                 AuditAction.INSPECTION_ASSIGNED,
48:                 AuditDetail.created("inspection of asset " + assetId + " assigned to " + inspector
49:                         + ", expected " + expectedDate));
50:         return inspection;
51:     }
52: }
```

### `infrastructure/src/main/resources/db/migration/V1__create_persistence_schema.sql`

```text
41:     id          VARCHAR(200) NOT NULL PRIMARY KEY,
42:     row_version BIGINT       NOT NULL,
43:     asset_id    VARCHAR(200) NOT NULL,
44:     status      VARCHAR(20)  NOT NULL,
45:     doc         CLOB         NOT NULL
46: );
47: CREATE INDEX ix_inspection_asset ON inspection (asset_id, status);
48: 
49: CREATE TABLE finding (
50:     seq           BIGINT GENERATED ALWAYS AS IDENTITY,
51:     id            VARCHAR(200) NOT NULL PRIMARY KEY,
52:     row_version   BIGINT       NOT NULL,
53:     inspection_id VARCHAR(200) NOT NULL,
54:     criterion_id  VARCHAR(200) NOT NULL,
55:     action_open   BOOLEAN      NOT NULL,
56:     doc           CLOB         NOT NULL
57: );
58: CREATE INDEX ix_finding_inspection ON finding (inspection_id, criterion_id);
59: CREATE INDEX ix_finding_open_action ON finding (action_open);
60: 
61: CREATE TABLE certificate (
62:     seq                   BIGINT GENERATED ALWAYS AS IDENTITY,
63:     id                    VARCHAR(200) NOT NULL PRIMARY KEY,
64:     row_version           BIGINT       NOT NULL,
65:     asset_id              VARCHAR(200) NOT NULL,
66:     backing_inspection_id VARCHAR(200) NOT NULL,
67:     scope_key             VARCHAR(300) NOT NULL,
68:     status                VARCHAR(20)  NOT NULL,
69:     expires_at_ms         BIGINT       NOT NULL,
70:     doc                   CLOB         NOT NULL
71: );
72: -- At most one certificate of each scope per inspection, even under concurrent issuance.
73: CREATE UNIQUE INDEX ux_certificate_inspection_scope ON certificate (backing_inspection_id, scope_key);
74: CREATE INDEX ix_certificate_asset_scope ON certificate (asset_id, scope_key);
```

