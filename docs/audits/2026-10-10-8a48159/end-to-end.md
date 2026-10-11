# End-to-end and delivery pass (Step 4)

Reviewed HEAD: `0b747c473f3ba6915ccaa3eb7754ec6e8693de43`, 2026-10-11,
Argentina Standard Time (UTC-03:00). The session began with a clean checkout.
The diff from baseline `8a4815960670ac1d467df4a8f0932c8932d2fab4` contains
documentation only: application, tests, build configuration, requirements,
contracts, README, DESIGN and CI are unchanged. This pass changes audit reports
and audit-only reproductions. No application repairs or requirement decisions.

Step 4 is **complete as a review with explicit verification gaps**. Packaged
HTTP/H2 workflows ran; browser workflows remain Unverified. API execution and
static frontend inspection do not demonstrate frontend usability. The complete
implementation remains unvalidated because confirmed defects and gaps remain.

## Setup and methods

The preserved full-run-002 executable JAR is
`target/audit-evidence/2026-10-10-8a48159/014-step3-tests/baseline-exec.jar`,
SHA-256 `91a192ed7dea02a09f3dca1a96ff4c8f73281a714ec132d535872dee11beb076`.
Full verification run 002 is reused for these unchanged sources and this artifact;
Step 3's focused build output is not used. No new Maven/test-suite run occurred.

Run **027-step4-packaged** uses JDK 26.0.2, `java -jar`, port **18084**, an isolated
file H2 at `target/audit-evidence/2026-10-10-8a48159/027-step4-packaged/db/certiflow`,
and `--certiflow.jobs.enabled=false`. Default Buenos Aires calendar zone and
logging notifications are retained. An audit-only `-javaagent` has no class
transformer and changes no business logic: a stdin shutdown command calls
`System.exit(0)`, allowing normal JVM/Spring/Hikari shutdown hooks. Both JVMs
exit 0; logs confirm graceful HTTP shutdown and Hikari closure. This is an
ordinary application restart, not an interrupted request, forced stop or crash.

Run **030-step4-controlled** extracts classes/dependencies from the same hashed
JAR and starts the actual Spring application on **18085** with a separate file
H2. An audit-only primary Clock reads explicitly selected Instants, deriving
`today()` in Buenos Aires. Production controllers, transactions, repositories,
outbox, handlers and policies are used. The second JVM registers a newer AR-BA
profile (revision 2, MEDIUM/HIGH/CRITICAL blocking, no conditional certificates,
three-month duration) through the existing policy registry. This is a composition
harness, **not** an unmodified `java -jar` run or a product policy-admin endpoint.
It supplies controlled time/configuration for real HTTP/H2 boundary checks.
`context.close()` explicitly shuts down HTTP/Hikari before normal JVM exit.

The browser skill was read and its required connection attempted twice through
the provided tool. Both attempts failed before executing JavaScript with
`codex/sandbox-state-meta: missing field sandboxPolicy`. No page, DOM, screenshot,
click, UI submission or UI error message was observed. No alternative browser
surface was substituted. Static packaged markup/script HTTP delivery is recorded
separately. See [durable results](evidence/step4-results.md),
[probe](evidence/step4_api_probe.py), [shutdown agent](evidence/AuditShutdownAgent.java),
[controlled composition](evidence/AuditControlledApp.java) and
[validation entries](validation.md#step-4-executions).

## Scenarios

Expected behavior is from Delivery 1's functional table/closed-inspection rule,
Delivery 2 F1/F2/F3 and minimal frontend/executable/CI obligations, and the
documented API/design contracts. Rows distinguish actual HTTP execution,
static inspection and previous evidence reused without rerunning.

| Scenario ID | Preconditions/action | Expected | Actual | Method/evidence | Outcome | Finding IDs |
|---|---|---|---|---|---|---|
| E-API-01 | Create owner/inspector/executor; register LABORATORY; publish document-backed schema; assign/start; answer hazardous; attach manual and note; close; rectify to clean with reason; read act, findings summary and certificate report | Progressive data retained; auditable correction preserves original; reports accessible; corrected result certifiable | All HTTP operations succeeded; act changed and retained reason; certificate issued; three report endpoints read | Packaged 027 and controlled 030, probe E-API-01, real file H2 | Passed | AUD-001/002 concern frontend access |
| E-ACTION-01 | Owner plans observed HK; executor records work/evidence; inspector verifies | Role-attributed action completes, finding resolved, conditional certificate remains valid | pendingNonConformity false; certificate VALID; history retained across restart | 027/030, E-F3-01 transcript and snapshots | Passed | — |
| E-F3-01 | Same MEDIUM observation and planned correction under AR-BA and AR-CBA | Jurisdiction-specific conditional permission/blockers recorded | BA issues CONDITIONAL; CBA returns 422 BLOCKED with ConditionalNotAllowed; no CBA certificate stored | 027/030 E-F3-01 | Passed | — |
| E-F3-02 | HIGH rejection in building safety under AR-BA; plan action, then verify | Rejection blocks until corrected; verification permits scoped issuance | Initial partial issuance 422; verified rejection permits REGULAR partial | 027/030 E-F1-01 | Passed | — |
| E-F1-01 | Shared clean; electrical clean, pressure observed, safety rejected; issue available partials; verify findings and issue remaining part | Partial isolation, independent validity, explicit global derivation | REGULAR/CONDITIONAL/REGULAR partials; NotDerivable while safety missing, Derived after verification; three PARTIAL rows, no stored global; mixed durations retained | 027/030 E-F1-01; before/after global replies | Passed | AUD-003 concerns wrong frontend global row |
| E-F1-02 | Shared HIGH rejection; all three part answers clean | Shared failure affects every applicable part | All three partial issuance requests return 422 | 027/030 E-F1-02 | Passed | — |
| E-F2-01 | Start v1 inspection; publish future v2 replacing HK with NEW; query future boundary at minus 1ns/exact/plus 1ns and invalid/preeffective dates | v1 before boundary, v2 at/after; old snapshot retained; 400 malformed/404 none | Queries return 1/2/2; original inspection still v1; error statuses match | 027 schedules 2099; 030 schedules 2026-10-12; actual HTTP/file H2 | Passed | — |
| E-F2-02 | Controlled Clock; three new assets start inspections at future boundary minus 1ns/exact/plus 1ns; reread old v1 | Startup selects 1/2/2; old version never rewritten | Exact startup selections and old snapshot asserted through HTTP | 030 E-F2-02; supplied Clock, production HTTP/H2 | Passed | AUD-005 narrowed |
| E-ERROR-01 | Missing/unknown actor on start; missing resource; unknown answer tag; invalid outbox status; close ASSIGNED; edit CLOSED | 401/404/400/422 errors; refused mutations preserve state | Expected HTTP codes; missing-actor inspection/audit unchanged; invalid answer inspection unchanged; closed mutation refused | 027/030 E-ERROR-01, stored GET/audit rereads | Passed | UI messages unverified |
| E-REPEAT-01 | Repeat issuance of corrected laboratory; premature renewal; close all-missing fixture | Retry does not issue another certificate; premature renewal refused; domain allows missing-data closure | ALREADY_ISSUED 200, premature renewal 422; zero-answer close 200 with findings | 027/030 E-ERROR-01 | Passed | AUD-004 remains frontend defect |
| E-ACTION-02 | Issue three AR-BA partials with one planned observation; advance beyond action deadline; sweep; verify late action | Only affected partial suspends; global unavailable; verification restores it | VALID/SUSPENDED/VALID; NotDerivable; affected certificate returns VALID and global Derived | 030 E-ACTION-02; actual jobs/outbox/handlers, controlled dates | Passed | — |
| E-TIME-01 | Plan pressure observation due Oct 23; assess at Oct 24 00:30Z (Oct 23 21:30 local); sweep | Inclusive deadline interpreted consistently across eligibility and expiry | Pre-fix eligibility and sweep disagreed; after repair the controlled-zone core and real HTTP/H2 tests accept the local due date, report no overdue blocker and keep the action PLANNED | Pre-fix run 030; `correctiveActionDeadlineUsesTheConfiguredCalendarDate` T-CORE and `CorrectiveActionDeadlineApiIT` T-APP | Passed | AUD-009 resolved |
| E-RESTART-01 | Snapshot 13 GET responses after workflows; graceful shutdown; reopen same DB; answer/close older v1 inspection; repeat prior issuance | Persisted records/policy/history and continuation retained | All 13 snapshots semantically equal; old HK inspection continues using v1; repeat issuance ALREADY_ISSUED | 027 JVM hooks and 030 context.close; explicit log excerpts/results | Passed | AUD-015 remains separate crash question |
| E-POLICY-01 | Restart with active AR-BA revision 2 after issuing revision 1 | New assessment uses current policy; issued certificates/reports/audit retain historical policy | Eligibility contains revision 2; prior report unchanged, including revision 1; snapshot equality covers audit/partials | 030 E-POLICY-01; audit-only registry configuration | Passed | AUD-005 narrowed |
| E-RENEW-01 | Advance to exact laboratory certificate expiry; sweep; new inspection with evidence; renew twice | Expiry exclusive; new linked certificate once; repeated renewal stable | EXPIRED at exact expiry, renewal 201 linked predecessor; repeat 200 ALREADY_ISSUED; audit/certificate lists unchanged | 030 E-RENEW-01; supplied Clock, real HTTP/file H2 | Passed | AUD-005 narrowed |
| E-MAINT-01 | Invoke jobs, inspect all/PENDING outbox, retry when no DEAD rows | Results and queue status consistent; empty retry harmless | Pending empty; retry returns revived=0/delivered=0; controlled sweep expires certificates/actions and delivers consequences | 027/030 transcript; expiration rows above | Passed | — |
| E-MAINT-02 | Operate nonempty DEAD recovery through packaged frontend | Monitor/retry exhausted rows and continue workflow | No browser connection; successful retry of nonempty DEAD not exercised by this Step 4 probe (Step 3 lower-layer evidence retained) | Processes.tsx static inspection; explicit runtime gap | Unverified | AUD-005 |
| E-PACKAGE-01 | Start preserved verified JAR; GET root and referenced JS | One packaged process serves frontend assets and API | Root and bundled script HTTP 200; API workflows executable | 027 E-PACKAGE-01; build 002 reused only for unchanged source/artifact | Passed | Browser usability not inferred |
| E-UI-01 | Actor selection/creation, asset/schema editor, inspection answers/evidence/closure through browser | Required general workflow usable | Tool connection fails before page access; HTTP analogues pass but no UI interaction observed | Browser setup attempts, static Party/Assets/SchemaDetail/InspectionDetail inspection | Unverified | AUD-005 |
| E-UI-02 | Correct mistaken closed result through supplied UI | Auditable correction available | Rendered component submits the documented correction with a mandatory reason; the packaged UI route now provides the action | `InspectionDetail.test.tsx` checks form interaction/request; existing HTTP rectification in E-API-01 | Passed for component workflow; browser run unavailable | AUD-001 resolved |
| E-UI-03 | Open inspection act from UI after correction | Historical original/corrected report available | Detail loads `/act`; rendered view shows frozen asset, original/corrected values and reason | `InspectionDetail.test.tsx`; existing HTTP act in E-API-01 | Passed for component workflow; browser run unavailable | AUD-002 resolved |
| E-UI-04 | Open certification panel for by-parts inspection and all valid partials | Derived global display agrees with scope policy | Panel displays derived-global notice and sends no whole-asset eligibility request; Step 4 API evidence confirms derived result | `CertificationPanel.test.tsx` plus 027/030 actual derivation endpoint | Passed for component workflow; browser run unavailable | AUD-003 resolved |
| E-UI-05 | Attempt closing IN_PROGRESS inspection with zero answers | Documented missing-data closure possible | Rendered close button is enabled and sends close request; actual HTTP closure and finding creation were observed in Step 4 | `InspectionDetail.test.tsx` plus 027/030 HTTP closure | Passed for component workflow; browser run unavailable | AUD-004 resolved |
| E-UI-06 | Invalid forms, missing actor/resource, failed request, repeated submission | Clear UI feedback and retained input/state | API statuses/state checked; actual UI messages, rendering and form recovery unseen | api.ts:120 and ui.tsx inspection; no browser | Unverified | AUD-005 |
| E-UI-07 | F1 partial/derived results; F2 datetime scheduling/lookup; F3 conditional/blocked display | Usable feature UI and correct timezone conversion | Consumers/form conversion inspected; packaged HTTP results recorded; no actual browser rendering or timezone selection | CertificationPanel, SchemaDetail, format.ts, Assets inspection | Unverified | AUD-003/005 |
| E-UI-08 | Reopen persisted app; restore actor and use maintenance/audit/report views | Browser workflow can continue after restart | HTTP continuation passes; browser storage/actor selection and views unexecuted | Party.tsx and Processes.tsx inspection; E-RESTART-01 | Unverified | AUD-005 |
| E-DELIVERY-01 | Compare build/run documentation, architecture/contract guidance and debt with executable behavior | Build/run instructions accurate; mandatory completeness assessed separately | README JAR/static/API instructions agree with 027; DESIGN now documents repaired rectification/act workflows and component coverage; remaining browser and advanced gaps are explicit | README.md:59/66/77; updated DESIGN.md sections 3, 5, 13, 15 and 16; frontend README.md:7 | Passed | AUD-005 remains open |
| E-CI-01 | Inspect workflow and live main ruleset | mvn verify CI and required Build and test block failed PRs | Workflow unchanged; active main ruleset requires PR and Build and test; legacy endpoint 401, ruleset endpoints 200 | .github/workflows/ci.yml:18/34; live evidence/step4-ci.json | Passed | No deliberately failing PR submitted |
| E-INTERACTION-01 | F1 asset applicability validated against future F2 version while startup selects uncovered current version | Unevaluated present parts cannot receive global certification | Applicability addition is rejected on uncovered published v1; undercovered start and certificate issuance fail closed | `FutureEffectiveSchemaIT`, `SchemaApplicabilityApiIT`, T-CORE/T-APP; real H2 for API boundary | Passed | AUD-011 resolved; historic certificates remain unchanged |
| E-INTERACTION-02 | Rectify an absent subsystem in closed inspection | Refuse before mutation and preserve stored history | HTTP returns 422/BUSINESS_RULE; inspection answer, rectification collection and audit count are unchanged | `InspectionApiIT` plus core `PartialCertificationIT`, T-APP/T-CORE; real HTTP/H2 | Passed | AUD-010 resolved |
| E-ADVANCED-01 | Cross-layer mixed-policy action resolution, absent-subsystem UI selection, nonempty retry UI, failure/crash interleavings beyond probes | Explicit evidence for remaining combinations | Targeted successful scenarios do not establish exhaustive interactions or crash recovery | Previous correctness/failure reports retain exact gaps; no inferred success | Unverified | AUD-005/015 |

## Delivery assessment and handoff

No new defect ID was added. Existing AUD-009 is now reproduced across real HTTP
and file H2; AUD-001–004 remain source-confirmed frontend defects, with backend
paths exercised. AUD-005 is narrowed by successful/repeated HTTP renewal, exact
HTTP F2 startup/retention, zone-aware API evidence, full workflow graceful restart,
and historical policy/partial/report retention. It stays Open for browser and
advanced combinations. All 15 findings remain Open; 10 defects, one coverage gap,
four questions. Requirement counts remain 97 Satisfied / 11 Partial / 2 Ambiguous /
3 Unverified (113); coverage closure alone repairs none of those defects.

Next work is to restore browser access and execute E-UI-01/06/07/08 and
E-MAINT-02, recording actual DOM/UI messages alongside API state. E-UI-02–05
need browser confirmation, but their precise source paths already prove defects.
Retain the confirmed concurrency/outbox/unsafe certification findings, obtain
owner decisions for AUD-006–008/015, then present triage. Step 5 application
repairs require a separate user request. No default `data/certiflow` DB was used;
probe JVMs are stopped. See [README handoff](README.md#completion-assessment-and-handoff).

## Step 5 scenario outcomes and Step 6 closure

The Step 4 handoff above is a point-in-time record and is superseded by the
Step 5 outcomes in the scenario rows and [repair report](step5-repairs.md).
Current total: **31 scenarios: 25 Passed, 0 Failed, 6 Unverified**. E-TIME-01,
E-UI-02–05 and E-INTERACTION-01/02 now pass via controlled-clock core/API,
component and real H2 regressions. E-UI-01/06/07/08, E-MAINT-02 and
E-ADVANCED-01 remain Unverified; browser bootstrap failed before execution with
`missing field sandboxPolicy`, and advanced crash/concurrency combinations were
not rerun as part of Step 5.

The ten confirmed defects are Resolved and final `mvn verify` passed as
V-STEP5. Step 6 closes the audit documentation while keeping AUD-005, AUD-006–008
and AUD-015 open. The implementation changes are validated for the repaired
scenarios; complete-system/browser validation is not claimed. See the [final
audit handoff](README.md#step-5-and-step-6-final-closure).
