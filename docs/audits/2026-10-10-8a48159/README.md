# System audit: 2026-10-10-8a48159

Initial review scope: complete system requirements from both deliveries, Steps 0–4. Step 4 continued on 2026-10-11 at HEAD `0b747c473f3ba6915ccaa3eb7754ec6e8693de43` (same application sources as baseline). Review started 2026-10-10 at 19:08 Argentina Standard Time (UTC-03:00). Reviewed baseline HEAD: `8a4815960670ac1d467df4a8f0932c8932d2fab4`. Step 5 later modified application code/tests, `DESIGN.md`, and audit reports in the local worktree; those changes remain uncommitted and were validated by `V-STEP5` below.

The checkout was already dirty: modified `AGENTS.md`, deleted `docs/correcciones-informe.md` and `docs/informe-certiflow-adeleGoldberg.md`, untracked `docs/entrega_1_correcciones.md` and `docs/system-audit-plan.md`. See [initial status](evidence/initial-status.txt), [agent rule diff](evidence/initial-agents.diff), and [input hashes](evidence/untracked-input-hashes.txt). These changes are not attributed to this audit. Requirement authorities are `docs/entrega_1.md` and `docs/entrega_2.md`; the untracked corrections document is not a third delivery specification.

- [x] Step 0: baseline complete, including environment failure, corrected full run and report snapshots.
- [x] Step 1: requirements complete; 113 independently checkable obligations assessed.
- [x] Step 2: correctness/boundaries/interactions complete as review; explicit runtime gaps remain.
- [x] Step 3: failure/recovery complete as review; explicit advanced recovery/runtime gaps remain.
- [x] Step 4: end-to-end/delivery complete as review; browser and advanced gaps explicitly Unverified.
- [x] Step 5: reproduce, regress, repair and validate all ten confirmed defects.
- [x] Step 6: close the audit with the remaining coverage gap, questions and unverified scenarios explicit.

Environment: Windows 11 / NT 10.0.26200.0; Maven 3.9.9; PATH Java 26.0.2; initial Maven JVM 21.0.10. Run `001-baseline` failed Java enforcer before tests because Maven requires JDK 25+. Run `002-jdk26` uses a process-local `JAVA_HOME` pointing to the installed JDK 26.0.2; no project or persistent environment setting was changed.

## Results and reports

[Requirements matrix](requirements.md): **113 obligations**, currently **104 Satisfied, 4 Partial, 2 Ambiguous, 3 Unverified**. Seven obligations were reclassified after the Step 5 fixes and verification. [Evidence catalogue](evidence/requirements-catalogue.md) provides the source and test evidence for the original requirements pass; [Step 5 repairs](step5-repairs.md) records updated evidence.

[Findings register](findings.md): **10 resolved defects**, **1 open coverage-gap record**, **4 open questions**. Resolved groups:

- [AUD-001–004](findings.md#step-5-resolution-evidence): closed-inspection UI workflows and by-parts certification display.
- [AUD-009–011](findings.md#aud-009): configured-zone deadlines, atomic inapplicable rectification, and version/part coverage guards.
- [AUD-012–014](findings.md#aud-012): outbox retry/cross-dispatcher consistency and concurrent assignment serialization.

AUD-005 remains open for browser and advanced recovery/interleaving coverage. AUD-006–008 retain scope/evidence/submission questions; AUD-015 retains the forced-stop durability question. No requirement changes or limitation acceptances were made.

[Validation](validation.md): final run **V-STEP5** passed `mvn --batch-mode --no-transfer-progress verify` on JDK 26.0.2. It passed **532 backend tests and 24 frontend tests**, with zero failures/errors/skips, frontend typecheck/build, and executable JAR packaging. The first full attempt stopped on three clock mocks that had not implemented the new overload; the mocks were corrected before the passing final run. Per-module XML counts are [snapshotted here](../../../target/audit-evidence/2026-10-10-8a48159/step5-full-verify/test-counts.txt); complete output is [here](evidence/step5-full-verify-final.log).

CI workflow inspection and live [GitHub ruleset evidence](evidence/github-ruleset.json.txt) establish active `main` PR blocking requiring **Build and test**. The legacy protection endpoint was inaccessible (401), but public ruleset details were available (200). Remote origin redirects to `Pablo-Goros/dps-2026-2c-ti-certiflow-adeleGoldberg`; its main was `f85a3537335bc7fcdc1e1f9c4b40065c3035594f` at inspection, distinct from the reviewed local revision. Remote CI results were not reused as local validation.

Baseline environment/configuration: [environment](evidence/environment.txt), [configuration locations](evidence/configuration-locations.txt), [JAR SHA-256](evidence/jar-sha256.txt). Local application defaults use Buenos Aires calendar dates, while issuance contexts use UTC dates; Step 2 confirmed inconsistent deadline decisions (AUD-009). Jobs default enabled (PT1M sweep/PT5S dispatch/PT30S startup), max outbox attempts 10; notification logging default, optional webhook with 3-second timeout. Tests isolate memory H2 and disable background scheduling; no default production file database reproduction was run.

Bulk evidence: `target/audit-evidence/2026-10-10-8a48159/001-baseline/` and `002-jdk26/` contain logs, start/end timestamps, exit codes and module report snapshots/manifests. Local report-generation helpers also live under this ignored evidence tree. These artifacts can disappear on cleanup; committed Markdown and selected durable excerpts preserve the essential conclusions.

## Step 2 continuation

[Correctness report](correctness.md): transitions, input/evidence/evaluation rules,
schema/deadline/validity boundaries, F1/F2/F3 and historical-policy interactions,
API/actor/serialization and UI comparison examined. Three new executed defects:

- [AUD-009](findings.md#aud-009), P2: UTC planning/eligibility disagrees with local-date expiry and verification.
- [AUD-010](findings.md#aud-010), P2: absent-subsystem rectification mutates the core aggregate before crashing; real HTTP returns 500, while H2 rollback preserves stored inspection/audit.
- [AUD-011](findings.md#aud-011), P1: applicability validates a future version's coverage while startup selects the current version without part checks, permitting persisted VALID/GLOBAL certification of uninspected parts.

[Durable Step 2 evidence](evidence/step2-results.md) includes controlled-clock
assertions, real HTTP/file-H2 request/response excerpts, source lines and current
focused-test results. Run 009 passed 182 core tests (69 unit, 113 integration),
with zero failures/errors/skips. Run 012 passed the audit probes and confirmed
F2 startup selection at minus 1ns/exactly/plus 1ns and retention. Run 013 launched
the baseline hashed JAR with jobs disabled on port 18082 and an isolated file-H2
database, reproduced two API cases, then terminated its process. No browser or
restart exercise was performed. Full build run 002 remains unchanged-code baseline
evidence; the audit is not declared fully validated.

## Step 3 continuation

[Failure/recovery report](failure-scenarios.md) covers per-operation repeats,
same-aggregate/certificate/applicability races, aggregate/audit/outbox rollback,
ordinary file-H2 reopening, pending/DEAD recovery, competing dispatchers and
notification failure/timeout. [Durable results](evidence/step3-results.md) preserve
commands, selected XML counts, probes, replies and source excerpts.

- AUD-012, P2: mixed outbox batches immediately retry failed rows; oldest failures delay healthy later events.
- AUD-013, P2: delayed failure bookkeeping resurrects a competing dispatcher's DONE event and allows duplicate committed handler effects.
- AUD-014, P2: six simultaneous assignment requests create multiple open inspections for one asset (3, 4 and 2 in final run's three rounds).
- AUD-015, Question: forced Windows termination after completed requests lost recent audit/schema state; clarify the required crash durability policy. Ordinary small-fixture JVM exit/reopen passed separately.

Run 015 passed **73 focused backend tests** (zero failures/errors/skips), with
frontend skipped. Run 021's real-H2 assertions reproduced outbox defects and
proved generic aggregate/audit/outbox and handler rollback. Run 017 used two
normally exiting JVMs and a file-H2 fixture; pending recovery delivered once.
Run 022 exercised actual packaged HTTP/file H2 repeats, assignment and transfer
races, then forced-stop reopening. Its exit 0 records all observed outcomes,
including failed all-state retention; it is not a passing graceful restart test.
No browser or full combined policy/history graceful-restart workflow executed.

## Historical Step 3 handoff

**Steps 0–3 complete as review passes. Step 4 and complete-system closure remain
pending.** All 15 findings remain Open: 10 defects, 1 coverage gap and 4 questions.
No application repairs, requirement changes or accepted limitations. HEAD remains
`8a4815960670ac1d467df4a8f0932c8932d2fab4`; source/test/contract diff is empty.
Pre-existing user documentation changes are preserved.

Last completed step: **3**. Read [failure/recovery](failure-scenarios.md),
[findings](findings.md), [validation](validation.md) and
[Step 3 evidence](evidence/step3-results.md) before continuing. Explicit remaining
gaps are grouped under AUD-005; forced-stop observations/interpretation under
AUD-015. Passing narrow tests do not close the confirmed failures.

Next session:

1. Recheck HEAD, dirty state and artifact hashes. Run 015 repackaged app/target using focused backend verification; it is not the full-baseline artifact. The preserved full-run-002 JAR is `target/audit-evidence/2026-10-10-8a48159/014-step3-tests/baseline-exec.jar`, SHA-256 `91a192ed7dea02a09f3dca1a96ff4c8f73281a714ec132d535872dee11beb076`. Use that copy for Step 4, or run full verification and record the new JAR.
2. Execute Step 4 on a separate port/isolated file H2. Browser-test required workflows and AUD-001–004, with backend scenarios AUD-009–014 retained as confirmed defects. Add controlled-clock real-API successful/repeated renewal, exact F2 startup/retention and zone-aware deadlines where practical.
3. Exercise deliberately graceful packaged shutdown/reopen and historical policy revision/partial-state retention. Do not reuse Windows terminate as graceful restart evidence. Resolve AUD-015 crash durability interpretation; the observed loss is not silently accepted.
4. Remaining Step 3 gaps: distinct-inspection issuance races, exhaustive save-boundary injection, corrupt event rows, external-send/commit failure and in-flight/hardware crashes. Their outcomes remain Unverified in the scenario report. Resolve AUD-006–008 with the requirement owner. Step 5 repairs require a separate request.

All audit probes and packaged processes have finished; no default data/certiflow
database was used. Bulky logs/isolated DBs/XML snapshots are under
`target/audit-evidence/2026-10-10-8a48159/`; durable probes/results are in this
audit's evidence folder. Baseline full run 002 is reused for unchanged sources
and its preserved artifact only; complete-system implementation is not validated.

Step 3 handoff recorded 2026-10-10T19:53:57.754204-03:00. Report integrity/status: [Step 3 checks](evidence/step3-report-checks.json).

## Step 4 continuation

[End-to-end/delivery report](end-to-end.md) records 31 scenarios (18 Passed,
7 Failed, 6 Unverified) with explicit
methods and outcomes. [Durable results](evidence/step4-results.md) retain final
launch metadata, selected actual HTTP replies and harness limitations. Final
packaged run **027** passes report/correction, actions, mixed F1/F3, future F2
lookup, invalid/repeat requests, static resource serving and **graceful restart**.
Final controlled run **030** passes exact HTTP F2 startup minus 1ns/exact/plus 1ns,
successful/repeated HTTP renewal, scoped expiration/suspension/reactivation,
and active-policy revision with persisted historical reports/partials/audit.
It also reproduces **AUD-009** through HTTP/file H2: eligibility calls an action
overdue while the local-date sweep retains it PLANNED on its inclusive due date.

The browser skill connection failed twice before any page/code execution
(`missing field sandboxPolicy`). Required browser workflows and UI error feedback
remain Unverified; API checks and served scripts do not establish UI usability.
AUD-001–004 remain source-confirmed defects, with their backend contrasts exercised.
No new finding ID or resolved/accepted finding. Live GitHub public settings were
refreshed: active main PR ruleset still requires Build and test; legacy endpoint
401 is supplemented by public ruleset 200 evidence.

## Completion assessment and handoff

**Steps 0–4 complete as review passes with explicit verification gaps.
Implementation is not validated.** All 15 findings remain Open: **10 defects,
1 coverage gap and 4 questions**. Requirement matrix remains **97 Satisfied /
11 Partial / 2 Ambiguous / 3 Unverified (113)**. Confirmed mandatory omissions
and unsafe certification remain unrepaired. Review only; no application/test/
contract/design changes and no accepted limitations.

Last completed step: **4**, including recorded Unverified browser scenarios.
Session HEAD `0b747c473f3ba6915ccaa3eb7754ec6e8693de43`; session began clean.
Earlier dirty documentation changes and Steps 0–3 audit outputs have since been
committed. Rechecked diff from baseline for core/infrastructure/app, build,
requirements, contracts, README/DESIGN and CI is empty. The baseline full-run-002
JAR preserved in `target/audit-evidence/2026-10-10-8a48159/014-step3-tests/baseline-exec.jar`
still has SHA-256 `91a192ed7dea02a09f3dca1a96ff4c8f73281a714ec132d535872dee11beb076`.
Full build run 002 is reused under the unchanged-source/artifact rule; no new
Maven/npm suite or current test counts claimed. Runs 027/030 both exit 0, with
two ordinary JVM exits each; probe success includes confirmation of AUD-009.

Next session:

1. Read this README, [findings](findings.md), [Step 4](end-to-end.md),
   [validation](validation.md) and [durable results](evidence/step4-results.md).
   Recheck HEAD/source diff/JAR hash before reusing any evidence.
2. Restore the provided browser connection and execute E-UI-01/06/07/08 and
   E-MAINT-02 against the packaged app on a new isolated file H2/port. Record
   actual UI messages/DOM and HTTP/state outcomes. Browser-confirm AUD-001–004;
   their source-confirmed defects do not depend on browser availability.
3. Keep advanced gaps visible: nonempty retry UI, exhaustive policy-revision
   action-resolution combinations, distinct-inspection issuance races, every
   save-boundary injection, corrupt event rows, external-send/commit failure,
   and in-flight/hardware crashes. Ordinary graceful retention now passes; it
   does not resolve AUD-015's forced-stop interpretation/observations.
4. Obtain requirement-owner decisions/evidence for AUD-006–008/015. Present
   consolidated triage before application repairs. Step 5 repairs require a
   separate user request; this review does not authorize them. Step 6 closure
   must distinguish completed review from unvalidated implementation.

Final run evidence lives under `target/audit-evidence/2026-10-10-8a48159/027-step4-packaged/`
and `030-step4-controlled/`; intermediate 023–029 metadata explain harness
failures and superseded runs. Essential evidence is saved under this audit's
`evidence/`. All probe JVMs stopped; ports 18084/18085 released. No default
`data/certiflow` database used. Report integrity/status: [Step 4 checks](evidence/step4-report-checks.json).

Step 4 handoff recorded 2026-10-11T11:07:48.727469-03:00.

## Step 5 and Step 6 final closure

**Steps 0–6 are complete as audit work.** Step 5 reproduced all ten confirmed
defects, added regressions, repaired them and recorded red/green evidence in
[Step 5 repairs](step5-repairs.md) and [findings](findings.md). Step 6 assessed
all 113 requirements and all 31 planned end-to-end scenarios. The current
scenario result is **25 Passed, 0 Failed, 6 Unverified** in
[end-to-end](end-to-end.md). The current requirement matrix is **104 Satisfied,
4 Partial, 2 Ambiguous, 3 Unverified** in [requirements](requirements.md).

### Final revision and validation

- Baseline HEAD for this worktree: `0b747c473f3ba6915ccaa3eb7754ec6e8693de43`.
  Step 5 application, test, design and audit changes are local and uncommitted.
- Final validation run `V-STEP5` executed `mvn --batch-mode --no-transfer-progress
  verify` from the repository root on JDK 26.0.2 (compiler release 25), with no
  skips. It passed **532 backend tests** (core 367, infrastructure 53, app 112)
  and **24 frontend tests in 4 files**, with zero failures, errors or skips.
  TypeScript, Vite and executable JAR packaging also passed. The exact run window,
  first-attempt clock-mock failure and final result are in [validation](validation.md);
  final output is [step5-full-verify-final.log](evidence/step5-full-verify-final.log).
- Per-module XML report snapshots and counts are under
  `target/audit-evidence/2026-10-10-8a48159/step5-full-verify/`.
- Final report integrity check resolved all **785 local Markdown links** with no
  broken targets; `git -c core.whitespace=cr-at-eol diff --check` passed (Git
  emitted only CRLF normalization notices for the audit reports).
- Step 4's live GitHub ruleset evidence showed `main` requires the **Build and
  test** check. The workflow/settings were not changed; no remote CI run was
  performed for these uncommitted local repairs.

### Resolved defects

AUD-001–004 now have rendered component regressions for closed rectification,
historical act display, by-parts derived-global display and missing-answer
closure. AUD-009–011 have controlled-date, atomic-rectification and schema
coverage regressions, including real HTTP/H2 checks. AUD-012/013 have H2
outbox-pass and stale-failure regressions. AUD-014 has a real concurrent
HTTP/H2 assignment regression. All ten are marked **Resolved** in the register.

No REST payload or database migration was needed. Rectification and act consume
the existing API; assignment locks the asset row transactionally; outbox row
schema is unchanged. Published schema versions, inspection snapshots, and
existing certificate snapshots were not rewritten. New issuance fails closed
for uncovered legacy inspections. Any previously issued certificate remains a
historical record; Step 5 did not inspect or migrate a user's default database.

### Open gaps and questions

- **AUD-005 remains Open:** `E-UI-01`, `E-UI-06/07/08`, `E-MAINT-02` and
  `E-ADVANCED-01` remain Unverified. In-app browser setup was rejected before
  page execution with `missing field sandboxPolicy`; jsdom component coverage
  does not establish packaged-browser usability. Distinct-inspection issuance
  races, exhaustive save-boundary failures, corrupt outbox documents,
  external-send/commit interleavings, and in-flight or hardware crashes also
  remain unverified.
- **AUD-006–008 remain Open questions:** assignment scope interpretation,
  reference-only evidence sufficiency, and the original submission/team records
  need owner or repository evidence.
- **AUD-015 remains Open:** forced Windows termination previously lost recent
  audit/schema state. Graceful restart passed, but the delivery durability
  guarantee and forced-stop result remain unresolved.

The audit is closed as a review with all confirmed defects repaired and the
repair set validated by full local verification. **Complete-system
implementation validation is not claimed** while the six scenario gaps and
four questions above remain open. The browser limitation is inherited from
Step 4; no browser or external CI execution is asserted for the repaired code.

Step 6 handoff recorded 2026-10-11. No probe JVM or temporary baseline worktree
remains, and no default `data/certiflow` database was used.
