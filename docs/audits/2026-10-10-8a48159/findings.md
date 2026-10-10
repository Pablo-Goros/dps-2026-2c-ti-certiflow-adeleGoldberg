# Findings register

Reviewed revision for all records: `8a4815960670ac1d467df4a8f0932c8932d2fab4`, with the pre-existing documentation changes recorded in [README](README.md). All records are **Open**. No repairs or accepted limitations were authorized. Step 2 added three executed defects (AUD-009–011); [correctness](correctness.md) and [Step 2 evidence](evidence/step2-results.md) distinguish core probes, focused tests and real HTTP/H2 evidence. No browser reproduction was performed. Step 3 added AUD-012–014 (three executed P2 defects) and AUD-015 (forced-stop durability question); see [failure/recovery](failure-scenarios.md) and [Step 3 evidence](evidence/step3-results.md).

| ID | Kind | Severity | Result | Requirements |
|---|---|---|---|---|
| AUD-001 | Defect | P1 | Required closed-inspection correction cannot be performed in the frontend | D1-CLOSED-02, D2-UI-01, D2-APP-02 |
| AUD-002 | Defect | P2 | Inspection act is inaccessible from frontend | D1-REPORT-01, D2-UI-01, D2-APP-02 |
| AUD-003 | Defect | P2 | By-parts inspection always renders an invalid direct global eligibility/issuance row | D2-F1-06, D2-UI-02, D2-APP-02 |
| AUD-004 | Defect | P2 | UI prevents documented closure of an inspection with all answers missing | D2-UI-01, D2-APP-02 |
| AUD-005 | Coverage gap | N/A | Browser, successful renewal HTTP, F2 boundaries and advanced recovery evidence remain incomplete | D1-CERT-03, D2-UI-01/02/03/04, D2-F2-02/03/04, D2-APP-01 |
| AUD-006 | Question | N/A | Whether assignment scope may be implicit full-schema scope | D1-ASSIGN-03 |
| AUD-007 | Question | N/A | Whether photo/document references satisfy the evidence obligation | D1-EXEC-03 |
| AUD-008 | Question | N/A | Original formal repository/SHA submission and five-member roster unavailable | D1-REPO-01/02, D1-TEAM-01 |
| AUD-009 | Defect | P2 | UTC planning/eligibility disagrees with local-date expiry and verification | D1-ACTION-01/02/04, D1-CERT-01 |
| AUD-010 | Defect | P2 | Absent-part rectification mutates core state then crashes; HTTP returns 500 | D1-CLOSED-02, D2-F1-01, D2-APP-02 |
| AUD-011 | Defect | P1 | Future-version applicability allows global certification of uninspected parts | D1-CERT-01, D2-F1-06, D2-APP-02 |
| AUD-012 | Defect | P2 | Outbox batches retry failures within one pass and delay healthy later rows | D2-APP-02, failure/recovery contract DESIGN 15 |
| AUD-013 | Defect | P2 | Delayed failure bookkeeping revives a competing dispatcher's DONE event | D1-AUDIT-03, D2-APP-02, failure/recovery contract DESIGN 15 |
| AUD-014 | Defect | P2 | Concurrent assignment persists multiple open inspections for one asset | D1-ASSIGN-01, D2-APP-02 |
| AUD-015 | Question | N/A | Required crash durability and forced-stop recovery policy undefined; acknowledged recent state lost | D1-AUDIT-01/02/03, D2-APP-01/02 |

## AUD-001

**Defect, P1 — expose the required auditable correction workflow in the frontend.** Status: Open.

Location: [InspectionDetail.tsx:93](../../../app/frontend/src/pages/InspectionDetail.tsx#L93) and [InspectionDetail.tsx:114](../../../app/frontend/src/pages/InspectionDetail.tsx#L114); API exists at [InspectionController.java:196](../../../app/src/main/java/ar/edu/itba/dps/certification/app/web/InspectionController.java#L196). Affected requirements: D1-CLOSED-02, D2-UI-01, D2-APP-02.

Preconditions/trigger: an assigned inspector closes an inspection, then discovers a misread measurement, incorrect observation or wrong evidence reference. Open that inspection in the supplied UI and attempt to correct it with a reason. Expected: a rectification workflow retains the original values, author, time and reason and reevaluates with frozen rules, as required by Delivery 1 p2 and Delivery 2's minimal frontend obligation. Actual: criterion controls are editable only IN_PROGRESS; CLOSED renders certification and findings navigation, with no rectification form/action or request consumer anywhere in frontend sources. The inspector must leave the required frontend to use raw HTTP; incorrect closed results cannot be corrected through the delivered interface.

Evidence: [static excerpts](evidence/static-findings.md), [E14](evidence/requirements-catalogue.md#e14-auditable-rectification). `InspectionApiIT.aRejectedMeasurementIsRectifiedAndTheEvaluationChanges` proves the backend path changes REJECTED to APPROVED and retains a rectification; it does not prove UI access. [DESIGN.md:659](../../../DESIGN.md#L659) explicitly declares this omission as debt. That does not accept the resulting Delivery 2 violation. Suggested correction: add closed-inspection correction controls with mandatory reason and original/current result presentation, then exercise them through the real API. No repair performed.

## AUD-002

**Defect, P2 — expose the inspection act in the frontend.** Status: Open.

Location: [InspectionDetail.tsx:17](../../../app/frontend/src/pages/InspectionDetail.tsx#L17) (ordinary inspection reads) and [InspectionDetail.tsx:53](../../../app/frontend/src/pages/InspectionDetail.tsx#L53) (findings navigation). Backend act endpoint: [InspectionController.java:205](../../../app/src/main/java/ar/edu/itba/dps/certification/app/web/InspectionController.java#L205). Affected requirements: D1-REPORT-01, D2-UI-01, D2-APP-02.

Preconditions/trigger: close an inspection, optionally rectify it through the API, then attempt to obtain its inspection act from the supplied frontend. Expected: the required act is available, preserving historical asset/rules and original/rectified values; Delivery 1 p2 requires the act, findings summary and certificate report, and Delivery 2 requires minimal frontend access. Actual: no frontend request or navigation consumes `/act`; the ordinary inspection view displays current criterion values rather than `ReportedValue` original/corrected pairs and rectification provenance. The rich backend act is inaccessible to UI users. Findings list/detail supplies a basic summary, but equivalence to the dedicated summary's history and obligations has not been established; absence of that endpoint consumer alone does not prove every summary requirement is missing. Certificate report is accessible and is not part of the omission.

Evidence: [E13](evidence/requirements-catalogue.md#e13-required-reports), [static excerpts/search](evidence/static-findings.md). `ReportingIT.theActDistinguishesOriginalFromRectified` asserts original/corrected values and reason; `theActCarriesTheFrozenAsset` asserts historical owner. API report tests establish HTTP availability; no UI report workflow is tested. Suggested correction: add act access/rendering, retaining historical rather than current-only values. The findings list/detail counts as a basic summary; the sources do not mandate consumption of the dedicated endpoint. No PDF/export format is mandated by these sources.

## AUD-003

**Defect, P2 — render a derived-global result for by-parts inspections without requesting whole-asset issuance eligibility.** Status: Open.

Location: [CertificationPanel.tsx:27](../../../app/frontend/src/components/CertificationPanel.tsx#L27), [CertificationPanel.tsx:83](../../../app/frontend/src/components/CertificationPanel.tsx#L83), [CertificationPanel.tsx:108](../../../app/frontend/src/components/CertificationPanel.tsx#L108). Domain guard: [CertificateFactory.java:223](../../../core/src/main/java/ar/edu/itba/dps/certification/application/certification/CertificateFactory.java#L223). Affected requirements: D2-F1-06, D2-UI-02, D2-APP-02.

Preconditions/trigger: create/start/close an inspection whose asset has certifiable subsystems; open the certification panel, then issue all partials. Expected: global certification derives from valid partials under explicit policy (Delivery 2 F1 and DESIGN D20/section 6). Actual: `targets` always appends a null subsystem global target. That row requests `/eligibility` without subsystem. `CertificateFactory.assess` invokes `requireWholeAssetSchema` and rejects the by-parts inspection even after all partials exist. The error is shown in the row, eligibility data remains absent and its global issuance button stays disabled. The separate derivation notice correctly says a global is composed, while the global row shows a persistent failed operation; the introduction also says a global can be issued. This contradicts the domain's derived-global workflow and presents an unusable action to every by-parts user.

Evidence: static UI-to-controller-to-factory trace in [E15](evidence/requirements-catalogue.md#e15-f1-partialglobal) and [static excerpts](evidence/static-findings.md). `PartialCertificationIT.aGlobalCertificateCannotBeIssuedDirectly` asserts the guard; `CertificationApiIT.eachSubsystemIsCertifiedOnItsOwnAndTheGlobalOneIsDerivedFromThem` asserts successful derivation separately. Those existing tests passed in run 002, but do not render this component. Suggested correction: choose targets from the certification plan, and show the global projection/provenance for by-parts instead of the direct-issuance row. No browser reproduction claimed.

## AUD-004

**Defect, P2 — permit the documented missing-data closure case in the frontend.** Status: Open.

Location: [InspectionDetail.tsx:110](../../../app/frontend/src/pages/InspectionDetail.tsx#L110). Underlying allowed behavior: [Inspection.java:217](../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L217) and [CriterionEvaluator.java:35](../../../core/src/main/java/ar/edu/itba/dps/certification/domain/evaluation/CriterionEvaluator.java#L35). Affected requirements: D2-UI-01, D2-APP-02; relates to D1-EVAL-03.

Preconditions/trigger: start an inspection with applicable mandatory criteria and leave every answer empty (for example, no measurements could be obtained). Try to close it in the frontend. Expected under [DESIGN.md:146](../../../DESIGN.md#L146): closure evaluates missing mandatory data as rejected with explicit reasons; UI should allow the agreed workflow. Actual: `disabled={busy || answered === 0}` makes closure impossible. Domain closure has no answered-count prerequisite and evaluates every applicable criterion; missing answers are rejected. User must enter an answer that was not obtained or leave the UI to close honestly. This is a confirmed UI/domain/design contradiction; the delivery wording does not itself define an answer-count boundary.

Evidence: [static excerpts](evidence/static-findings.md), `CriterionEvaluatorTest.missingAnswerRejects` and `ReviewCorrectionsIT.directClosureEvaluatesMissingAnswersAndEvidenceInsteadOfAcceptingCallerVerdicts` establish missing-data behavior. No component or browser execution of this trigger occurred. Suggested correction: allow closure with zero answers, making the missing-data outcome clear, unless an explicitly agreed behavior change updates domain and documentation together.

## AUD-005

**Coverage gap — existing checks do not establish browser workflow usability or all cross-layer feature cases.** Status: Open; no defect severity assigned.

Locations: [FrontendIT.java:19](../../../app/src/test/java/ar/edu/itba/dps/certification/app/FrontendIT.java#L19), [api.test.ts:13](../../../app/frontend/src/lib/api.test.ts#L13), [ruleForm.test.ts:7](../../../app/frontend/src/lib/ruleForm.test.ts#L7), [SchemaVersionAtDateApiIT.java:68](../../../app/src/test/java/ar/edu/itba/dps/certification/app/SchemaVersionAtDateApiIT.java#L68), and [RenewalIT.java:72](../../../core/src/test/java/ar/edu/itba/dps/certification/RenewalIT.java#L72). Affected IDs are in the register table.

Expected evidence: source assertions and real persistence/API checks for cross-layer behavior; plan Steps 2–4 require exact boundaries, failure/restart recovery and packaged frontend workflows. Step 1 assessment: FrontendIT checks root markup, JavaScript HTTP availability and API JSON, without executing React; Vitest covers mocked-fetch client and helper conversions, without components or browser. The packaged exec JAR was built but not yet launched. No `app/src/test` test invokes `/certificates/renewal`. F2 API tests query effective versions at the exact boundary, but never start inspections before/at/after it and reread the old inspection after switching. Core covers retention and later startup, so this is missing cross-layer evidence, not proof of a defect. `JdbcLifecycleIT` reopens repositories on the same memory datasource; it is not a file-database process restart/crash proof.

Step 2 update: run 012 executes exact core startup at minus 1ns/exactly/plus 1ns and asserts frozen old-version retention. Runs 010/013 launch the baseline packaged JAR with isolated file H2 and execute narrow HTTP defect probes, so JAR launch itself is now verified. These do not close the browser-workflow, exact controlled-clock HTTP startup/retention, HTTP renewal, zone-aware real-API deadline, or process restart/crash gaps. Policy revision plus persisted restart/report retention also remains unexecuted in this continuation. See [correctness](correctness.md) and [Step 2 results](evidence/step2-results.md). Keep Open for the remaining scenarios; do not infer that a file database used in one process proves restart recovery.

Evidence: [E19](evidence/requirements-catalogue.md#e19-runnable-complete-application), [E20](evidence/requirements-catalogue.md#e20-required-test-types-and-collaborators), [E16](evidence/requirements-catalogue.md#e16-f2-future-versions), [baseline results](evidence/baseline-results.md). DESIGN 16 declares component/E2E omissions. Required test types exist and passed; their existence does not prove these scenarios. Next check: controlled-clock real HTTP renewal/F2 startup evidence, isolated file H2 restart, packaged JAR launch and browser scenarios in their scheduled passes. No new tests or runtime scenarios were added during this requirements-only session.

### AUD-005: Step 3 coverage update

Runs 015/017/021/022 now establish focused failure/recovery checks, ordinary
file-H2 reopening of a small fixture, pending event recovery in a new JVM,
actual repeat whole-asset issuance/closure/publication/verification and HTTP
premature renewal refusal. Packaged launch is verified. These close those narrow
gaps only. Successful/repeated HTTP renewal, exact HTTP F2 startup/retention,
zone-aware deadlines, historical policy revision/partial states across graceful
packaged restart, browser usability, corrupt events, distinct-inspection issuance
races, every application save-boundary injection and in-flight crash tests remain
unverified. Forced-stop observations are in AUD-015. See the explicit scenario
outcomes in [Step 3](failure-scenarios.md); keep this coverage record Open.

## AUD-006


**Question — does “alcance” require independently selectable assignment scope?** Status: Open.

Sources/location: [entrega_1.md:31](../../entrega_1.md#L31), [DESIGN.md:118](../../../DESIGN.md#L118), [InspectionController.java:98](../../../app/src/main/java/ar/edu/itba/dps/certification/app/web/InspectionController.java#L98). Requirement D1-ASSIGN-03.

Trigger: assign an inspection intended to cover only selected criteria/subsystems rather than the entire applicable schema. Source says designate inspector, planned date and scope; DESIGN adopts full schema, and assignment DTO/use case accepts asset, inspector and date only. F1 certificate scope does not add selectable inspection assignment scope. Consequence if selectable scope was intended: the model/API/UI cannot express it; if full applicable schema counts as scope, the current implementation suffices. Evidence: [E04](evidence/requirements-catalogue.md#e04-assignment). Resolve with the requirement owner before treating this as a defect or accepting the interpretation; no assumed acceptance.

## AUD-007

**Question — are persistent photo/document references sufficient evidence?** Status: Open.

Sources/location: [entrega_1.md:18](../../entrega_1.md#L18), [DESIGN.md:148](../../../DESIGN.md#L148), [Inspection.java:183](../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L183), frontend `CriterionCard.tsx`. Requirement D1-EXEC-03.

Trigger: attach a photo/document and expect the system to retain or resolve its bytes; instead the UI/API stores a free-text reference. Source mentions photographs/documents/evidence but does not explicitly require binary upload/storage or link verification. DESIGN intentionally adopts references and mandatory presence/type/count checks. Consequence if actual file retention was intended: evidence bytes may disappear or references may be inaccessible; this has not been reproduced and is not classified as confirmed data loss. Evidence: [E05](evidence/requirements-catalogue.md#e05-progressive-inspection-input). Resolve expected evidence representation with the requirement owner; do not silently invent an upload mandate.

## AUD-008

**Question — historical submission and team evidence unavailable.** Status: Open.

Sources/location: [entrega_1.md:59](../../entrega_1.md#L59) (repository URL and delivery SHA), [entrega_1.md:9](../../entrega_1.md#L9) (five-member group). Affected requirements: D1-REPO-01/02, D1-TEAM-01.

The checkout identifies a GitHub remote and HEAD; [live repository evidence](evidence/github-settings.txt) establishes its renamed public destination. No original submission manifest declaring the Delivery 1 URL/SHA or authoritative five-member roster was available in the reviewed material. Neither present HEAD, repository contributor count nor this audit report proves what was submitted or the membership. Consequence: these academic delivery obligations remain Unverified rather than Missing. Next check: obtain the actual submission record/roster; no application change needed.

## AUD-009

**Defect, P2 — use one calendar basis for corrective-action deadlines.** Status: Open.
Affected requirements: D1-ACTION-01/02/04, D1-CERT-01; scenarios C-TIME-01/02/03.

Locations at reviewed HEAD: [PlanCorrectiveAction.java:39](../../../core/src/main/java/ar/edu/itba/dps/certification/application/finding/usecase/PlanCorrectiveAction.java#L39), [CertificateFactory.java:219](../../../core/src/main/java/ar/edu/itba/dps/certification/application/certification/CertificateFactory.java#L219), [CertificationReactions.java:70](../../../core/src/main/java/ar/edu/itba/dps/certification/application/certification/CertificationReactions.java#L70), versus [VerifyCorrectiveAction.java:41](../../../core/src/main/java/ar/edu/itba/dps/certification/application/finding/usecase/VerifyCorrectiveAction.java#L41), [ExpireOverdueCorrectiveActions.java:35](../../../core/src/main/java/ar/edu/itba/dps/certification/application/finding/usecase/ExpireOverdueCorrectiveActions.java#L35) and [SystemClock.java:24](../../../app/src/main/java/ar/edu/itba/dps/certification/app/config/SystemClock.java#L24).

Trigger: plan a nonblocking observation due March 2, then at `2026-03-03T00:30:00Z`
(March 2 21:30 under the default Buenos Aires zone) assess eligibility and sweep
expiry. Expected: an inclusive deadline has the same meaning throughout planning,
certification, expiry and verification. [DESIGN.md:178](../../../DESIGN.md#L178)
explicitly permits a plan due `Clock.today()` and makes the due date inclusive;
the delivery requires action expiry and certification, without imposing UTC or
Buenos Aires. Choosing either consistently is possible, but the current mixture
cannot satisfy both descriptions.

Actual: expiry finds zero expired actions and `overdueAndOpen(localToday)` is false,
while eligibility returns `OverdueOpenAction[count=1]`. At the same instant local
verification records `metItsDeadline=true` and clears the blocker. A new plan due
local today is refused as preceding the UTC planning date. These disagreements
occur daily during 21:00–23:59 Buenos Aires time under default configuration;
UTC `certiflow.zone` masks them. Certification reconciliation also uses the UTC
date, so an event during this window can decide against a different deadline than
the sweep. That particular event/suspension case was inspected, not executed.

Evidence: executed zone-aware controlled Clock with actual core use cases in
[Step2Probe.java](evidence/Step2Probe.java), final run 012, reproduced all three
date contradictions. [Results and source excerpts](evidence/step2-results.md).
No sleep or changed system clock; no HTTP timezone reproduction claimed. Existing
core TestClock uses UTC, which hides this production configuration interaction.
Suggested correction: establish one business date from the configured Clock for
deadline comparisons (or document and enforce UTC consistently), capture date
and instant consistently, and add zone-aware boundary regression coverage.
No fix or calendar-policy change made.

## AUD-010

**Defect, P2 — reject inapplicable rectification destinations before mutation.** Status: Open.
Affected requirements: D1-CLOSED-02, D2-F1-01, D2-APP-02; scenario C-RECT-04.

Locations at reviewed HEAD: [Inspection.java:281](../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L281) only checks record existence for AnswerCorrection; [Inspection.java:260](../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L260) appends the rectification before [Inspection.java:263](../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L263) requires a previous evaluation. HTTP entry point: [InspectionController.java:196](../../../app/src/main/java/ar/edu/itba/dps/certification/app/web/InspectionController.java#L196).

Trigger: publish a facility schema with electrical/pressure/safety criteria;
register an electrical-only asset; start and close it; submit an ANSWER
rectification for pressure with a valid option and reason as the assigned
inspector. Expected: absent parts remain inapplicable; invalid rectification is
refused before altering the inspection, per [DESIGN.md:280](../../../DESIGN.md#L280)
and the input applicability guards. API business refusals use 422/BUSINESS_RULE.

Actual: the existing inapplicable record passes validation. The answer changes
and a rectification is appended; reevaluation then throws NoSuchElementException
because closure skipped this record. The caller-owned aggregate is left partially
changed. The packaged API returns 500/INTERNAL_ERROR instead of a business
refusal. Executed file-H2 rereads showed the stored inspection and its audit
trail remained unchanged because the enclosing HTTP transaction rolled back;
**no persistent data corruption is claimed for that API path**. The P2 impact is
the core aggregate's broken invalid-operation guarantee plus the exposed API
crash. Existing invalid-target atomicity tests cover unknown destinations, not
known-but-inapplicable records.

Evidence: controlled core probe run 012 asserts changed absent answer and appended
rectification after the exception; real packaged HTTP/file-H2 runs 010 and 013
assert 500 and unchanged inspection/audit. Durable [probe](evidence/step2_api_probe.py)
and [request/response excerpts](evidence/step2-results.md). Suggested correction:
prevalidate applicability and prior evaluation of every affected criterion before
applying any correction; reject as a domain rule, with core and real-H2/API
regression coverage. No fix performed.

## AUD-011

**Defect, P1 — validate effective subsystem coverage when changing applicability and starting inspections.** Status: Open.
Affected requirements: D1-CERT-01, D2-F1-06, D2-APP-02; scenario C-F1-F2-01.

Locations at reviewed HEAD: [SchemaApplicability.java:55](../../../core/src/main/java/ar/edu/itba/dps/certification/domain/schema/SchemaApplicability.java#L55) validates only latestPublishedVersion; [StartInspection.java:46](../../../core/src/main/java/ar/edu/itba/dps/certification/application/inspection/usecase/StartInspection.java#L46) selects effectiveVersionFor; [Inspection.java:104](../../../core/src/main/java/ar/edu/itba/dps/certification/domain/inspection/Inspection.java#L104) intersects version-declared and asset-present parts; [CertificateFactory.java:223](../../../core/src/main/java/ar/edu/itba/dps/certification/application/certification/CertificateFactory.java#L223) treats an empty intersection as whole-asset certification.

Trigger: create a laboratory schema, publish v1 with only a shared check, then
publish v2 effective ten days later containing criteria for every facility/factory
part. Add FACILITY (core probe) or FACTORY (HTTP probe) applicability now. Register
an asset with its parts present, start before v2 is effective, approve the sole v1
check, close and request direct global issuance.

Expected: the applicability/publication coverage guarantee must prevent global
certification of unevaluated parts; F1 derives global from partials for a parted
asset. [InspectionSchema.java:152](../../../core/src/main/java/ar/edu/itba/dps/certification/domain/schema/InspectionSchema.java#L152)
explicitly explains this safety rule; [PartialCertificationIT.java:633](../../../core/src/test/java/ar/edu/itba/dps/certification/PartialCertificationIT.java#L633)
asserts that ordinary applicability cannot bypass it. F2 must continue selecting
the current version, so simply selecting the not-yet-effective v2 would violate F2.

Actual: latest v2 passes applicability validation, but startup freezes v1 with
no part criteria. The intersection is empty, so issuance accepts a direct global
certificate rather than partials and their derivation. Core reproduced a facility
with three unevaluated parts. Real HTTP/file-H2 reproduced a factory with two
unevaluated parts, received successful issuance, and reread a persisted
GLOBAL/VALID certificate. This is unsafe certification, not just missing coverage
or a frontend omission. `transfer` calls the same latest-only validator; that
variant is implicated by code inspection, not separately executed.

Evidence: Step2Probe final run 012 and packaged-JAR HTTP run 013; durable
[requests, certificate result and source excerpts](evidence/step2-results.md).
Suggested correction: protect every version that can be selected for newly added
applicability, including current and intermediate scheduled versions; also reject
startup/certification when asset-present parts are not fully covered by the frozen
version. Preserve historical versions instead of rewriting their content. Add
combined F1/F2/applicability core and API/H2 regression cases. No fix performed.

## AUD-012

**Defect, P2 — keep failed outbox rows out of subsequent batches in the same pass.** Status: Open.
Scenarios F-OUTBOX-06/07; requirement D2-APP-02 and DESIGN section 15 recovery contract.

Locations: [OutboxDispatcher.java:51](../../../infrastructure/src/main/java/ar/edu/itba/dps/certification/infrastructure/events/OutboxDispatcher.java#L51), loop continuation at line 59 and [JdbcEventOutbox.java:51](../../../infrastructure/src/main/java/ar/edu/itba/dps/certification/infrastructure/persistence/jdbc/JdbcEventOutbox.java#L51), oldest pending batch selection. Reviewed baseline revision applies.

Trigger: enqueue one permanently failing event followed by two healthy events,
then call dispatchPending once with maxAttempts=3. DESIGN:591 and dispatcher
Javadoc:16 promise a failed row waits for a later pass rather than an immediate
retry. Actual real-H2 run 021: two good rows delivered, but the failing row's
attempts rises to **2 within that one call**. Any successful batch causes the loop
to reread the oldest pending rows, including the just-failed row. With a larger
healthy backlog, attempts can reach DEAD within one call, exhausting the intended
recovery interval while a transient outage has no chance to recover.

Related trigger: place 50 failing rows before a healthy row 51. The first call
delivers zero and never attempts row 51. Subsequent passes advance only when old
failures recover or reach DEAD. This is a bounded delay under successful failure
bookkeeping, not a claim that healthy rows are permanently lost. The main defect
is the explicit retry-policy contradiction; the oldest-batch delay adds impact.

Evidence: [Step3Probe.java](evidence/Step3Probe.java), run 021 assertions and
[durable output](evidence/step3-results.md). Existing OutboxIT failure tests use
one isolated event and passed in run 015. Suggested correction: track attempted
sequence IDs or traverse a stable pass boundary so failed rows wait for the next
call and do not hide subsequent healthy rows. Add mixed/backlog H2 regressions.
No application changes made.

## AUD-013

**Defect, P2 — prevent failure bookkeeping from reviving an already completed event.** Status: Open.
Scenario F-OUTBOX-08; D1-AUDIT-03, D2-APP-02 and DESIGN section 15 competing-dispatcher guarantee.

Locations: [JdbcEventOutbox.java:68](../../../infrastructure/src/main/java/ar/edu/itba/dps/certification/infrastructure/persistence/jdbc/JdbcEventOutbox.java#L68) updates by seq without a status/attempt condition; [OutboxDispatcher.java:89](../../../infrastructure/src/main/java/ar/edu/itba/dps/certification/infrastructure/events/OutboxDispatcher.java#L89) records failure in a separate transaction after releasing the original claim.

Trigger: dispatcher A's handler fails and its transaction rolls back. Pause A
before preparing the failure UPDATE. Dispatcher B claims the pending event,
commits its handler audit write and DONE status. Resume A. Expected under
DESIGN:587–607: B's successful database effects remain applied once and DONE is
terminal. Actual: A's UPDATE sets that DONE row back to PENDING; B's next pass
handles it again. Real H2 run 021 asserted **two committed handler audit entries**
for one event. A late update can also set the completed row DEAD if the limit is
reached. Healthy-only dispatcher competition does not exercise this race.

The source-launcher probe uses two production dispatchers and a DataSource
wrapper that pauses the real SQL preparation; all queries, claims, commits,
rollback and audit writes run on real H2. It does not mock persistence or use
sleeps. This establishes duplicate committed database effects for generic
handlers; duplicate suspension records for current CertificateLifecycle are not
claimed, since its state reconciliation can be idempotent. External webhook
duplicates are separately documented debt and not the basis of this defect.

Evidence: [probe](evidence/Step3Probe.java) and [results](evidence/step3-results.md).
Suggested correction: make failure updates conditional on the still-pending
attempt/generation, ensure stale failure bookkeeping cannot overwrite success,
and add a coordinated real-H2 regression. No fix made.

## AUD-014

**Defect, P2 — serialize assignment's open-inspection invariant per asset.** Status: Open.
Scenario F-CONCUR-03; D1-ASSIGN-01 and D2-APP-02.

Locations: [AssignInspection.java:39](../../../core/src/main/java/ar/edu/itba/dps/certification/application/inspection/usecase/AssignInspection.java#L39), check before insertion at line 45; [V1__create_persistence_schema.sql:41](../../../infrastructure/src/main/resources/db/migration/V1__create_persistence_schema.sql#L41), inspection table has an asset/status index but no open-slot uniqueness; [JdbcInspectionRepository.java:54](../../../infrastructure/src/main/java/ar/edu/itba/dps/certification/infrastructure/persistence/jdbc/JdbcInspectionRepository.java#L54), unlocked nonclosed lookup.

Trigger: register a valid asset/inspector; with no open inspection, submit six
POST /inspections requests for that asset simultaneously. Expected: the use
case's explicit rule rejects a second open inspection; InspectionApiIT's
sequential test verifies this rule. Actual final run 022: three fresh assets
ended with **3, 4 and 2 persisted open inspections** respectively; each successful
creation returned 201 and had its own assignment audit. Runs 019/020 also
reproduced duplicates. The reads can all see no open row; newly generated IDs
mean optimistic locking of an existing inspection never conflicts, and the
asset is only read. A transaction alone does not protect this absent-row check.

Consequence: simultaneous users can create contradictory active assignments and
parallel work for one asset; subsequent assignment is then rejected against
only the first open inspection. No parallel certificate issuance or global
corruption is inferred from this reproduction. The six-request existing
ConcurrencyApiIT covers schema creation and same-inspection certificate issuance,
not assignment.

Evidence: [HTTP probe](evidence/step3_api_probe.py), real packaged baseline JAR and
isolated file-H2 run 022; [durable replies/counts](evidence/step3-results.md).
Suggested correction: enforce an open-inspection slot atomically in persistence
or coordinate via a per-asset database lock/version write; return a documented
409/422 for losing requests and roll back their audit. Add real API/H2 race
coverage. No fix made.

## AUD-015

**Question — what durability is promised for acknowledged operations after forced process termination?** Status: Open.
Scenario F-RESTART-02/04; D1-AUDIT-01/02/03, D2-APP-01/02.

Location/configuration: [application.properties:5](../../../app/src/main/resources/application.properties#L5), embedded file-H2 URL; [JdbcTransactions.java:56](../../../infrastructure/src/main/java/ar/edu/itba/dps/certification/infrastructure/persistence/jdbc/JdbcTransactions.java#L56), commit before returning. The plan explicitly separates ordinary restart from abrupt-crash evidence. Deliveries and DESIGN do not specify an acknowledged-commit survival guarantee or a forced-stop recovery procedure.

Observed trigger: complete HTTP operations, snapshot their GET responses, force
terminate the packaged JVM using Windows subprocess terminate, reopen the same
isolated file H2 immediately. Run 022 reopened inspection, act, issued certificate,
frozen policy/report and outbox identically, but audit fell **44 to 31 entries**,
schemas **3 to 1**, and the recent applicability transfer was absent (source
again contained EQUIPMENT). Run 020 also lost recent state; run 019 audit fell
33 to 32. These are observations of forced termination following completed
requests, not a graceful shutdown test. No interrupted transaction, power loss,
disk failure, corrupt database or exact low-level flush mechanism was tested.

Consequences: immediate forced restart cannot currently be reported as retaining
all acknowledged changes in this configuration. Resolve whether crash durability
is required, then verify/configure it accordingly or document the required clean
shutdown and its recovery limits. Do not silently accept the observed loss, or
classify a graceful-restart defect from this method. Separate ordinary two-JVM
file-H2 seed/recovery run 017 passed with normal exits. Evidence: [durable
results](evidence/step3-results.md) and [HTTP probe](evidence/step3_api_probe.py).
No configuration change or limitation acceptance made.
