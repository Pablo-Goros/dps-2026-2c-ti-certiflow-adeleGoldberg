# CertiFlow system audit plan

## Purpose and scope

Audit the completed application against both deliveries, including domain behavior,
use cases, JDBC persistence, event processing, REST API, frontend, documentation,
and CI. The output is an evidence-based assessment followed by a separate repair
and verification stage. This document defines the task sequence; repository rules
remain in [AGENTS.md](../AGENTS.md).

The plan has not been executed. Unchecked steps and example report rows are not
evidence that a requirement or scenario has been verified.

## Report locations

At the start, choose an audit ID such as `2026-10-10-8a48159`, using the local start
date and the short Git SHA actually being reviewed. Append `-02`, `-03`, etc. for
another audit of the same revision. Record uncommitted changes separately: the
SHA alone does not identify a dirty checkout.

Save the review documents under `docs/audits/<audit-id>/`:

| File | Contents |
|---|---|
| `README.md` | Revision/environment, scope, pass checklist, report links, handoff notes, final assessment, and remaining risks. |
| `requirements.md` | Requirement-by-requirement implementation and test evidence, including gaps and ambiguities. |
| `correctness.md` | State transitions, business rules, boundary cases, and feature interactions reviewed. |
| `failure-scenarios.md` | Repeated requests, concurrency, rollback, restart, and delivery/retry scenarios reviewed. |
| `end-to-end.md` | Frontend/API workflow scenarios with steps, expected outcomes, and actual outcomes. |
| `findings.md` | Single register of defects, coverage gaps, questions, and their eventual resolutions. |
| `validation.md` | Commands, working directories, checkout/revision, run times, exit codes, results, and evidence locations. |
| `evidence/` | Small, durable reproductions, sanitized request/response excerpts, and selected screenshots needed to substantiate findings. |

During execution, create each pass report when its pass starts. Link it from the
audit README. Do not create empty reports that appear complete.

Large generated artifacts belong in `target/audit-evidence/<audit-id>/<run-id>/`,
which is already excluded from Git. Give each run a unique timestamp or sequence
ID. Save command output, application logs, test-report snapshots, and bulk
screenshots there. These local artifacts can disappear after cleanup; preserve
the essential reproduction and result in the committed Markdown reports. For
remote CI runs, record the run URL and artifact name as well.

Use relative Markdown links between review documents. Cite repository files with
paths, line numbers, and the reviewed revision, since lines can move after repairs.
Refer to one finding ID from multiple passes instead of copying the finding.

## Step 0: Establish the baseline

- [ ] Read `AGENTS.md`, `README.md`, both delivery documents, and the relevant parts
  of `DESIGN.md`, `docs/API.md`, and `docs/arquitectura-hexagonal.md`.
- [ ] Record `git rev-parse HEAD`, `git status --short`, local date/time with zone,
  operating system, `java -version`, and `mvn -version`. Record relevant application
  configuration, especially clock/timezone, jobs, and notification settings.
- [ ] Create the audit folder and README with scope, baseline state, and pass status.
- [ ] Run `mvn --batch-mode --no-transfer-progress verify` from the repository root.
  Capture the exit code and output in a new evidence run directory. Inspect failed
  tests and build failures; distinguish environment problems from application bugs.
- [ ] Snapshot reports from `core`, `infrastructure`, and `app` under the run
  directory: `target/surefire-reports`, `target/failsafe-reports`, and
  `target/site/jacoco`, when produced. Record missing outputs and avoid attributing
  stale reports from previous builds to the current run.
- [ ] Enter the result in `validation.md`. A failed baseline does not prevent code
  review, but dependent runtime scenarios remain unverified until executable.

Example PowerShell capture, after selecting the real audit and run IDs:

```powershell
$auditId = 'YYYY-MM-DD-shortsha'
$auditRunId = '001-baseline'
$auditRunPath = Join-Path 'target/audit-evidence' "$auditId/$auditRunId"
New-Item -ItemType Directory -Path $auditRunPath -Force | Out-Null
mvn --batch-mode --no-transfer-progress verify 2>&1 |
    Tee-Object -FilePath (Join-Path $auditRunPath 'maven-verify.log')
$auditExitCode = $LASTEXITCODE
Set-Content -LiteralPath (Join-Path $auditRunPath 'exit-code.txt') -Value $auditExitCode
```

Baseline completion: checkout, environment, build result, and limitations are
recorded. Passing tests are supporting evidence rather than a completeness claim.

## Step 1: Requirements pass

- [ ] Extract each independently checkable obligation from `docs/entrega_1.md` and
  `docs/entrega_2.md`. Include functional behavior, architecture, test types,
  documentation, runnable packaging, and CI integration blocking.
- [ ] Assign stable audit requirement IDs, such as `D1-CATALOG-01`, `D1-AUDIT-01`,
  `D2-F1-01`, `D2-F2-01`, `D2-F3-01`, and `D2-CI-01`. Cite the source subsection or
  page and split obligations when they need separate evidence.
- [ ] Trace each obligation through the relevant domain/use case, persistence,
  REST endpoint, and frontend interaction. Mark irrelevant layers as `N/A` with
  a reason rather than interpreting an empty cell as satisfaction.
- [ ] Inspect test assertions, fixtures, and collaborators. A matching test name
  is insufficient; identify what behavior the assertions establish and whether
  the test uses real persistence/API where required.
- [ ] Compare implementation with documented design decisions. Record conflicts
  between requirements, documentation, and code as findings or questions.
- [ ] Inspect CI configuration and available GitHub branch-protection evidence.
  If repository settings are inaccessible, mark integration blocking unverified;
  the workflow file alone does not demonstrate it.
- [ ] Write `requirements.md` and add linked findings to `findings.md`.

Suggested matrix:

| ID/source | Obligation | Implementation evidence | Persistence/API/UI evidence | Test evidence | Assessment | Finding IDs |
|---|---|---|---|---|---|---|
| D2-F2-01 / F2 | New inspections select the effective version | To inspect | To inspect | To inspect | Unverified | — |

Assessments: `Satisfied`, `Partial`, `Missing`, `Unverified`, or `Ambiguous`.
Document the reason. Existing tests can support satisfaction without claiming a
runtime scenario was executed. Record executed validation separately.

Pass completion: every obligation has a row, evidence or an explicit gap, and an
assessment. This pass is complete even if it identifies incomplete requirements.

## Step 2: Correctness and edge-case pass

- [ ] Map allowed and rejected transitions for schemas, inspections, findings,
  corrective actions, and certificates. Inspect every mutation entry point,
  including API and maintenance paths, for bypasses of the domain rules.
- [ ] Review missing/invalid answers, measurement boundaries, mandatory evidence,
  criterion applicability, closure, and auditable rectification. Check preservation
  of original results and the effect of repeated rectifications on obligations.
- [ ] Review schema selection immediately before, exactly at, and after an
  effective instant; no effective version; multiple scheduled versions; draft
  changes; and inspection snapshots after publication or asset changes.
- [ ] Review deadline and certificate validity boundaries, renewal, suspension,
  reactivation, and superseding inspections. Resolve timezone and precision from
  the implementation and documented behavior; use controlled time for tests.
- [ ] Review F1 with mixed subsystem results, shared criteria, absent subsystems,
  incomplete partial coverage, independently expired/suspended certificates, and
  the global derivation policy.
- [ ] Review F3 across blocking/nonblocking severities, regular/conditional modes,
  action planning and verification, unknown jurisdictions, policy revisions,
  eligibility versus issuance, and historical policy/report retention.
- [ ] Review interactions: F1 plus F3, F2 plus ongoing inspections, rectification
  after issuance, and corrective-action resolution after policy changes.
- [ ] Compare REST errors, actor handling, serialization, frontend types, form
  validation, and displayed results with the same domain rules.
- [ ] Record each scenario in `correctness.md`, its method of examination, outcome,
  and linked findings. Identify coverage gaps separately from proven bugs.

Pass completion: the relevant transitions and boundary/interaction scenarios have
been examined, with evidence or a documented reason they remain unverified.

## Step 3: Failure and recovery pass

- [ ] Establish expected behavior for repeated operations individually. Exercise
  repeat issuance/renewal, close/verification, publication, job runs, and event
  retries; check for unintended duplicate state, effects, or audit entries.
- [ ] Examine concurrent changes to the same aggregate, certificate issuance for
  the same scope, and schema applicability transfers. Check database constraints,
  optimistic conflicts, API responses, and absence of partial writes.
- [ ] Examine failures between related saves. Check that aggregate changes, audit,
  and outbox enqueueing commit or roll back together using real H2 evidence.
- [ ] Check persistence after reopening the database, historical references,
  reconstructed state, pending events, and retry recovery. Separate ordinary
  restart evidence from abrupt-crash evidence; do not imply one proves the other.
- [ ] Examine event-handler failure, competing dispatchers, attempt limits,
  `DEAD` events, manual retry, and eventual effects. Treat external webhook
  delivery separately from database handler transactions when checking duplicates.
- [ ] Examine notification timeout/failure and whether certificate reactions and
  the original committed operation remain consistent with the intended behavior.
- [ ] Use existing integration tests where applicable and record exact missing
  scenarios. Keep temporary reproductions and databases under the evidence tree,
  separate from the default `data/certiflow` database.
- [ ] Save scenario results in `failure-scenarios.md`, executions in
  `validation.md`, and confirmed issues/questions in `findings.md`.

Pass completion: repeat, concurrency, rollback, restart, and delivery scenarios
have evidence-backed outcomes or explicit coverage/verification gaps.

## Step 4: End-to-end and delivery pass

- [ ] Use the JAR produced by the current verified build and an isolated H2 file.
  Record its path and configuration. A separate port avoids interference with an
  existing application instance. For example, from the repository root:

  ```powershell
  java -jar app/target/certiflow-app-1.0.0-SNAPSHOT-exec.jar `
      --server.port=8081 `
      --spring.datasource.url=jdbc:h2:file:./target/audit-evidence/AUDIT-ID/e2e-db/certiflow
  ```

- [ ] Exercise the packaged frontend: select/create an actor, register an asset,
  create and publish a schema, assign and start an inspection, record answers and
  evidence, close it, and inspect eligibility and certification results.
- [ ] Exercise corrective-action planning, execution, verification, certificate
  consequences, audit records, and all required reports.
- [ ] Exercise F1 partial issuance and global derivation, F2 future publication
  and effective-version lookup, and F3 differing jurisdiction outcomes, including
  conditional and blocked cases. Include the feature interactions from Step 2.
- [ ] Exercise invalid inputs, missing actors/resources, illegal transitions,
  blocked issuance, repeated submissions, and failed requests. Inspect the HTTP
  response as well as the UI message and subsequent stored state.
- [ ] Reopen persisted data after restart and confirm the workflow can continue.
  Check maintenance views, expiration processing, outbox status, and retry controls.
- [ ] Record setup, steps, expected and actual outcomes, scenario IDs, and evidence
  in `end-to-end.md`. Record browser/tool limitations explicitly. API tests do not
  substitute for evidence that a frontend workflow is usable.
- [ ] Assess packaging, documentation accuracy, and CI evidence against the
  requirements matrix. Update assessments and linked findings.

Pass completion: required workflows have recorded outcomes, including invalid
paths, or explicit unverified areas. Reuse the baseline full-build result if the
checkout has not changed; otherwise run full verification on the reviewed state.

## Findings and validation formats

Use stable finding IDs such as `AUD-001`. Group records by kind: `Defect`,
`Coverage gap`, `Question`, or `Optional improvement`. For defects, use `P1`
(major correctness/data loss or blocked required workflow), `P2` (material defect
in a narrower scenario), or `P3` (minor impact). Record any urgency separately.

Each finding record contains:

- ID, kind, severity where applicable, title, and affected requirement/scenario IDs.
- Reviewed revision, file/line, preconditions, and concrete triggering steps.
- Expected behavior and its source; actual behavior and consequence.
- Evidence: executed reproduction/test, or a precise code path if not executed.
- Suggested correction or missing check, without presenting speculation as fact.
- Status: `Open`, `Resolved`, `Accepted limitation`, or `Rejected`, with supporting
  rationale. Acceptance needs an explicit decision and cannot silently satisfy a
  missing mandatory requirement.
- For resolution: changed revision/files, regression test, and validation run IDs.

Use this scenario table in correctness, failure, and end-to-end reports:

| Scenario ID | Preconditions/action | Expected | Actual | Method/evidence | Outcome | Finding IDs |
|---|---|---|---|---|---|---|

Outcomes: `Passed`, `Failed`, `Unverified`, or `N/A` with a reason. Methods can be
code inspection, existing test inspection, executed test, API reproduction, or
browser execution. State the method so an inspected test is not counted as a run.

In `validation.md`, keep one entry per execution:

| Run ID/time | Revision and local changes | Working directory/command | Configuration | Exit code/result | Evidence path | Limitations |
|---|---|---|---|---|---|---|

Report test counts only from that run's outputs. Never carry a successful result
forward as validation of later code changes without checking its applicability.

## Step 5: Triage, repair, and regression verification

- [ ] Consolidate and deduplicate findings; prioritize requirement omissions and
  correctness defects. Resolve questions that affect the intended behavior first.
- [ ] Present the review outcome before repairs. Review sessions change reports;
  application repairs follow a request to fix the confirmed findings, as specified
  in `AGENTS.md`. The audit plan itself does not begin that repair stage.
- [ ] For each authorized repair, reproduce the problem and add a meaningful
  regression test when appropriate. Record evidence of the failure before the
  fix, then the successful result afterward. Preserve the original finding.
- [ ] Review compatibility, transaction boundaries, historical state, and related
  consumers affected by the fix. Update affected design/contract documentation.
- [ ] Run targeted tests and recheck related scenarios; include real persistence
  or API checks when the defect crosses those boundaries.
- [ ] Run full `mvn verify` after repairs, save a new validation entry and report
  snapshots, and update finding resolutions and requirement assessments.

## Step 6: Close the audit or hand it off

- [ ] Every requirement has an assessment; every planned relevant scenario has an
  outcome or an explicit verification gap.
- [ ] Each finding has a status and evidence; unresolved decisions and accepted
  limitations remain visible, with no silently dropped findings.
- [ ] Full validation has run on the final reviewed code state, with failures and
  environment limitations reported accurately. Required runtime workflows have
  their own evidence rather than an inference from build success.
- [ ] Update the audit README with final revision, validation run IDs, open defects,
  requirement gaps, unverified areas, and a completion assessment.

Distinguish **review completed** from **implementation validated**. The review can
finish with defects; implementation validation requires mandatory requirements
and confirmed defects to be resolved, relevant workflow evidence, and successful
full verification. If a required check is inaccessible, state the limitation.

For a session handoff, update the audit README with the last completed step,
partially reviewed scenarios, next actions, and exact report/evidence locations.
The next session reads this plan, `AGENTS.md`, the audit README, and findings before
continuing; it rechecks the checkout state before reusing previous evidence.

Suggested first task:

> Follow `docs/system-audit-plan.md` and `AGENTS.md`. Establish the current baseline
> and execute Steps 0 and 1. Save reports in the audit folder defined by the plan.
> Update the handoff notes when finished. Review only; do not change application code.
