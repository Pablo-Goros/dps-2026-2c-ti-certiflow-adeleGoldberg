# System audit: 2026-10-10-8a48159

Review scope: complete system requirements from both deliveries, Steps 0–3; Step 3 continued in this session. Review started 2026-10-10 at 19:08 Argentina Standard Time (UTC-03:00). Reviewed HEAD: `8a4815960670ac1d467df4a8f0932c8932d2fab4`. Application code is read only; audit documents and generated build outputs are the only task writes.

The checkout was already dirty: modified `AGENTS.md`, deleted `docs/correcciones-informe.md` and `docs/informe-certiflow-adeleGoldberg.md`, untracked `docs/entrega_1_correcciones.md` and `docs/system-audit-plan.md`. See [initial status](evidence/initial-status.txt), [agent rule diff](evidence/initial-agents.diff), and [input hashes](evidence/untracked-input-hashes.txt). These changes are not attributed to this audit. Requirement authorities are `docs/entrega_1.md` and `docs/entrega_2.md`; the untracked corrections document is not a third delivery specification.

- [x] Step 0: baseline complete, including environment failure, corrected full run and report snapshots.
- [x] Step 1: requirements complete; 113 independently checkable obligations assessed.
- [x] Step 2: correctness/boundaries/interactions complete as review; explicit runtime gaps remain.
- [x] Step 3: failure/recovery complete as review; explicit advanced recovery/runtime gaps remain.
- [ ] Step 4: end-to-end/delivery — not started as an audit pass.
- [ ] Step 5: repair — not authorized in this review-only session.
- [ ] Step 6: complete-system closure — pending; this session ends with a handoff.

Environment: Windows 11 / NT 10.0.26200.0; Maven 3.9.9; PATH Java 26.0.2; initial Maven JVM 21.0.10. Run `001-baseline` failed Java enforcer before tests because Maven requires JDK 25+. Run `002-jdk26` uses a process-local `JAVA_HOME` pointing to the installed JDK 26.0.2; no project or persistent environment setting was changed.

## Results and reports

[Requirements matrix](requirements.md): **113 obligations**, with **97 Satisfied, 11 Partial, 2 Ambiguous, 3 Unverified**. Satisfied is a requirements-pass assessment backed by inspected code and assertions, not a claim that every runtime scenario or all later passes were executed. [Evidence catalogue](evidence/requirements-catalogue.md) provides 23 groups with source paths, original line numbers, all relevant layers, assertion/fixture details and explicit N/A reasons.

[Findings register](findings.md): **10 open defects**, **1 coverage-gap record**, **4 questions**. Confirmed issues:

- [AUD-001](findings.md#aud-001), P1: required auditable rectification unavailable in the frontend.
- [AUD-002](findings.md#aud-002), P2: inspection act unavailable in the frontend.
- [AUD-003](findings.md#aud-003), P2: by-parts certification renders an invalid direct-global eligibility/issuance row alongside its derived-global result.
- [AUD-004](findings.md#aud-004), P2: UI prevents documented closure when all answers are missing.

Documented debt explains the rectification and test omissions but does not accept mandatory requirement violations. Questions retain full-schema assignment scope, reference-only photo/document evidence, and unavailable formal submission/SHA/team records. No application fixes, requirement changes or debt acceptance occurred.

[Validation](validation.md): `001-baseline` failed before tests due to Maven JDK 21; `002-jdk26` **passed full `mvn --batch-mode --no-transfer-progress verify`** with a process-local JDK 26.0.2. **521 backend tests and 20 frontend tests passed**, with no backend failures/errors/skips. Frontend TypeScript/Vite build and executable JAR packaging passed. [Durable result](evidence/baseline-results.md) uses only current run XML timestamps; regenerated JaCoCo reports may contain prior execution data, so no current-only coverage percentage is claimed.

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

## Completion assessment and handoff

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
