# Step 4 durable execution evidence

Session reviewed `0b747c473f3ba6915ccaa3eb7754ec6e8693de43` on 2026-10-11
(UTC-03:00), initial checkout clean. `git diff 8a4815960670ac1d467df4a8f0932c8932d2fab4 HEAD -- core infrastructure app pom.xml README.md DESIGN.md docs/API.md docs/arquitectura-hexagonal.md docs/entrega_1.md docs/entrega_2.md .github`
was empty. Full baseline run 002 remains applicable to those unchanged sources
and the preserved JAR; no new test-suite count or coverage claim is made.

[End-to-end scenarios](../end-to-end.md) give methods, expected/actual outcomes
and limitations. [Final launch commands/results](step4-run-metadata.json) retain
exact command arrays, local start/end times, revision, hash, process exits and
scenario assertions. [Selected HTTP requests/replies](step4-http-excerpts.json)
retain original/corrected act values, F2 boundary queries/startups, renewal,
deadline inconsistency and empty retry. IDs/names are synthetic audit fixtures.
Bulk full transcripts/application logs/isolated databases live under
`target/audit-evidence/2026-10-10-8a48159/<run-id>/` and may disappear on cleanup.

## Reproduction

From repository root, with the preserved baseline JAR available and JDK
`C:/Program Files/Java/jdk-26.0.2/bin`, run:

```powershell
python docs/audits/2026-10-10-8a48159/evidence/step4_api_probe.py UNIQUE-packaged packaged
python docs/audits/2026-10-10-8a48159/evidence/step4_api_probe.py UNIQUE-controlled controlled
```

Run names must be new. The helper creates only audit evidence paths; the real
application writes only that run's file H2. Packaged mode compiles
[AuditShutdownAgent.java](AuditShutdownAgent.java), builds an agent JAR, and
launches the unchanged executable with `-javaagent:... -jar ...`, port 18084,
isolated datasource and disabled automatic jobs. The agent adds stdin-controlled
`System.exit(0)` only; no transformations. Controlled mode extracts baseline
classes/libraries and source-launches [AuditControlledApp.java](AuditControlledApp.java)
on port 18085; its primary Clock and second-start registry revision are audit
composition overrides. Controllers/JDBC/H2/business/event handlers remain real.
No product time-travel or policy-admin API is implied. Sleeps are readiness
polls only; business time changes use explicit Clock values, with no timing sleeps.

Restart comparisons canonicalize only JSON arrays representing sets:
blockingSeverities, restrictions and coveredSubsystems. Audit sequence,
provenance, criteria, responses and all other lists remain ordered. Exact raw
JSON array order is not a stable representation of those domain sets across
different JVMs; the initial strict comparisons were harness failures, with no
observed lost state or changed policy membership.

## Execution ledger

Every command below was `python docs/audits/2026-10-10-8a48159/evidence/step4_api_probe.py <run-id> <mode>`
at repository root on the reviewed HEAD plus audit-only files.

| Run | Local start/end | Exit | Result/limitation |
|---|---|---|---|
| 023-step4-packaged | 2026-10-11T11:00:02.529170-03:00 to 2026-10-11T11:00:15.352659-03:00 | 1 | Fixture rejected: laboratory used undeclared purpose characteristic. Corrected to empty characteristics; no workflow completion. |
| 024-step4-packaged | 2026-10-11T11:00:26.783257-03:00 to 2026-10-11T11:00:40.580932-03:00 | 1 | Report/correction and F3 passed; harness expected NotDerived instead of actual contract NotDerivable. Corrected assertion; no complete run. |
| 025-step4-packaged | 2026-10-11T11:00:57.970579-03:00 to 2026-10-11T11:01:29.130109-03:00 | 1 | Workflows reached graceful reopen; strict equality failed on set-valued policy arrays changing order. Not an application data-loss result. |
| 026-step4-controlled | 2026-10-11T11:01:08.234392-03:00 to 2026-10-11T11:01:41.639831-03:00 | 1 | F2 boundaries/partial expiration reached graceful reopen; audit equality differed only in policy set order. Superseded by semantic comparison. |
| 027-step4-packaged | 2026-10-11T11:02:04.845983-03:00 to 2026-10-11T11:02:38.403315-03:00 | 0 | Final packaged workflows and all 13 graceful-restart snapshots passed. Both JVMs exited 0; no browser execution. |
| 028-step4-controlled | 2026-10-11T11:02:04.848313-03:00 to 2026-10-11T11:02:44.145313-03:00 | 0 | Controlled F2/renewal, partial isolation, policy revision and restart passed. Superseded for final evidence by run 030 adding deadline probe. |
| 029-step4-controlled | 2026-10-11T11:03:22.714038-03:00 to 2026-10-11T11:03:37.757002-03:00 | 1 | Deadline probe reached correct OverdueOpenAction reply, but expected the wrong blocker label OverdueAction. Corrected harness; no complete run. |
| 030-step4-controlled | 2026-10-11T11:04:01.350698-03:00 to 2026-10-11T11:04:29.968813-03:00 | 0 | Final controlled HTTP/H2 assertions passed, including reproduction of AUD-009. Both JVMs exited 0. Exit 0 confirms the probe observations, not absence of defects. |

## Final observations

### 027-step4-packaged

```json
{
  "E-API-01": {
    "reports": 3,
    "rectificationRetained": true,
    "labInspection": "/inspections/3f55fdd4-a1b8-4752-b74b-cca9ecaf620b"
  },
  "E-F3-01": {
    "BA": "CONDITIONAL",
    "CBA": "BLOCKED",
    "verifiedConditionalStatus": "VALID"
  },
  "E-F1-01": {
    "modes": [
      "REGULAR",
      "CONDITIONAL",
      "REGULAR"
    ],
    "derivedAfterVerification": true,
    "persistedOnlyPartials": true,
    "inspection": "/inspections/8f87ff63-1e79-4d09-b6e8-44dd29c6cabd"
  },
  "E-F1-02": {
    "sharedFailureBlocksAll": true
  },
  "E-ERROR-01": {
    "actorHTTP": 401,
    "missingHTTP": 404,
    "invalidHTTP": 400,
    "illegalHTTP": 422,
    "zeroAnswerCloseHTTP": 200,
    "repeatIssue": "ALREADY_ISSUED",
    "refusedInputStoredUnchanged": true
  },
  "E-F2-01": {
    "lookupMinusExactPlus": [
      1,
      2,
      2
    ],
    "oldInspection": "/inspections/07b4a7b3-4bdb-481d-ac09-7ff612e7740d",
    "v1": "2fd2dfbf-b768-49ba-a2e1-27494bc4cd29#v1",
    "v2": "2fd2dfbf-b768-49ba-a2e1-27494bc4cd29#v2"
  },
  "E-PACKAGE-01": {
    "rootHTTP": 200,
    "scriptsHTTP": 200,
    "scripts": [
      "/assets/index-C25jdxkU.js"
    ],
    "browserExecuted": false
  },
  "E-RESTART-01": {
    "equalSnapshots": {
      "/inspections/3f55fdd4-a1b8-4752-b74b-cca9ecaf620b": true,
      "/inspections/3f55fdd4-a1b8-4752-b74b-cca9ecaf620b/act": true,
      "/inspections/3f55fdd4-a1b8-4752-b74b-cca9ecaf620b/findings-summary": true,
      "/inspections/2747cff5-bb8a-4b3a-8d4c-a1efb3c28a7b": true,
      "/inspections/8f87ff63-1e79-4d09-b6e8-44dd29c6cabd": true,
      "/inspections/8f87ff63-1e79-4d09-b6e8-44dd29c6cabd/global-derivation": true,
      "/inspections/07b4a7b3-4bdb-481d-ac09-7ff612e7740d": true,
      "/certificates": true,
      "/certificates/1977f24e-586a-4229-b846-3ebc28499c20/report": true,
      "/findings": true,
      "/audit": true,
      "/schemas": true,
      "/admin/outbox": true
    },
    "continuedOldVersion": true,
    "shutdown": "JVM shutdown hooks / Spring context.close"
  }
}
```

### 030-step4-controlled

```json
{
  "E-API-01": {
    "reports": 3,
    "rectificationRetained": true,
    "labInspection": "/inspections/10b4180f-caea-4c85-9ef3-32cd0a9cddf4"
  },
  "E-F3-01": {
    "BA": "CONDITIONAL",
    "CBA": "BLOCKED",
    "verifiedConditionalStatus": "VALID"
  },
  "E-F1-01": {
    "modes": [
      "REGULAR",
      "CONDITIONAL",
      "REGULAR"
    ],
    "derivedAfterVerification": true,
    "persistedOnlyPartials": true,
    "inspection": "/inspections/a6d04a4a-6cd3-4d63-962c-6d700dc69d5a"
  },
  "E-F1-02": {
    "sharedFailureBlocksAll": true
  },
  "E-ERROR-01": {
    "actorHTTP": 401,
    "missingHTTP": 404,
    "invalidHTTP": 400,
    "illegalHTTP": 422,
    "zeroAnswerCloseHTTP": 200,
    "repeatIssue": "ALREADY_ISSUED",
    "refusedInputStoredUnchanged": true
  },
  "E-F2-01": {
    "lookupMinusExactPlus": [
      1,
      2,
      2
    ],
    "oldInspection": "/inspections/1724c4a9-9621-48b6-ad7a-30ab22ac4319",
    "v1": "3c651a5d-02ab-4d85-9353-0f7fda4c67ff#v1",
    "v2": "3c651a5d-02ab-4d85-9353-0f7fda4c67ff#v2"
  },
  "E-F2-02": {
    "startupMinusExactPlus": [
      1,
      2,
      2
    ],
    "frozenRetained": true
  },
  "E-ACTION-02": {
    "sweep": {
      "expiredCertificates": 0,
      "expiredActions": 2,
      "deliveredEvents": 0
    },
    "partStatusesAfterExpiry": [
      "VALID",
      "SUSPENDED",
      "VALID"
    ],
    "reactivatedAfterVerification": true
  },
  "E-TIME-01": {
    "instant": "2026-10-24T00:30:00Z",
    "localDate": "2026-10-23",
    "eligibilityOverdue": true,
    "actionStatusAfterSweep": "PLANNED",
    "deadlineBreached": false,
    "finding": "AUD-009"
  },
  "E-PACKAGE-01": {
    "rootHTTP": 200,
    "scriptsHTTP": 200,
    "scripts": [
      "/assets/index-C25jdxkU.js"
    ],
    "browserExecuted": false
  },
  "E-RESTART-01": {
    "equalSnapshots": {
      "/inspections/10b4180f-caea-4c85-9ef3-32cd0a9cddf4": true,
      "/inspections/10b4180f-caea-4c85-9ef3-32cd0a9cddf4/act": true,
      "/inspections/10b4180f-caea-4c85-9ef3-32cd0a9cddf4/findings-summary": true,
      "/inspections/f36663d2-b348-495a-a86e-0fc1c378691c": true,
      "/inspections/a6d04a4a-6cd3-4d63-962c-6d700dc69d5a": true,
      "/inspections/a6d04a4a-6cd3-4d63-962c-6d700dc69d5a/global-derivation": true,
      "/inspections/1724c4a9-9621-48b6-ad7a-30ab22ac4319": true,
      "/certificates": true,
      "/certificates/66e13e4f-05c5-4465-a717-76f448e04c6a/report": true,
      "/findings": true,
      "/audit": true,
      "/schemas": true,
      "/admin/outbox": true
    },
    "continuedOldVersion": true,
    "shutdown": "JVM shutdown hooks / Spring context.close"
  },
  "E-POLICY-01": {
    "activeRevision": 2,
    "historicalCertificateReportRetained": true
  },
  "E-RENEW-01": {
    "expiredExactlyAtBoundary": true,
    "sweep": {
      "expiredCertificates": 6,
      "expiredActions": 3,
      "deliveredEvents": 0
    },
    "renewHTTP": 201,
    "predecessorRetained": true,
    "repeatHTTP": 200,
    "repeatAuditCertificatesUnchanged": true
  }
}
```

## Shutdown, browser and CI evidence

The final packaged logs show `Commencing graceful shutdown`, `Graceful shutdown
complete`, `HikariPool-1 - Shutdown initiated...` and `Shutdown completed.`
for each JVM. The controlled launcher logs the same sequence after context.close.
The exit array for each final run is `[0, 0]`. Reopening compared 13 complete GET
snapshots and continued the previously in-progress v1 inspection. Ordinary
restart evidence does not resolve AUD-015's earlier forced-stop observations.

The browser tool failed twice before any code execution. First attempt used the
skill's absolute browser-client import/setup and complete documentation call;
the retry was a minimal connection check. Both returned:

```text
Mcp error: -32602: js: codex/sandbox-state-meta: missing field sandboxPolicy
```

There was no browser observation. Root and bundled JavaScript HTTP 200 are
packaging evidence only. Actual UI usability, UI error feedback, browser timezone
conversion, actor restoration and nonempty retry controls remain Unverified.

Read-only CI settings check at 2026-10-11T11:03:48.630751-03:00 fetched public
rulesets and rule 24847806 with HTTP 200. [Captured responses](step4-ci.json)
establish active `refs/heads/main` PR enforcement with required `Build and test`
(integration 15368). Legacy protection returns 401; public rulesets supply the
evidence. Workflow uses full `mvn --batch-mode --no-transfer-progress verify`.
No deliberately failing PR was submitted, and remote CI does not validate this
local checkout. The web tool's initial direct API open was inaccessible; the
standard-library urllib requests succeeded and are the actual evidence.

No new application defect was added and no existing record resolved. The
HTTP/file-H2 deadline reproduction extends AUD-009. All 15 records remain Open;
browser/advanced gaps under AUD-005 are narrowed explicitly. Reports were updated
without changing application source, tests, requirements or design decisions.
