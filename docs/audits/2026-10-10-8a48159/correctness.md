# Correctness and edge-case pass

**Step 2 complete as a review pass; three new confirmed defects remain open.**
Reviewed revision: `8a4815960670ac1d467df4a8f0932c8932d2fab4`, unchanged from
the baseline. Existing dirty documentation is recorded in [README](README.md).
Application code and test sources were not changed. Sources of intended behavior
are both deliveries, DESIGN sections 3–9 and 12/17, API and architecture documents;
contradictions are recorded rather than resolved by preferring one source.

Methods: mutation-path inspection, assertion/fixture inspection, focused existing
test execution (run 009), controlled-clock core probes (final run 012), and narrow
packaged-JAR HTTP/file-H2 probes (run 013). A `Passed` inspection outcome describes
the examined path, not exhaustive runtime coverage. See [Step 2 evidence](evidence/step2-results.md)
for exact locations, executed commands/results and durable reproductions.
The [requirements catalogue](evidence/requirements-catalogue.md) supplies the
cross-layer paths for previously reviewed behavior; the scenarios below refine it.

## Transitions and mutation entry points

| Aggregate | Allowed transitions / updates | Rejected transitions and examined paths |
|---|---|---|
| Schema | No draft → open; draft edits/removals → publish or discard; successful publish appends immutable version and removes draft; applicability add/transfer | Duplicate draft, editing without draft, empty/duplicate/incomplete rules, backwards effective times, duplicate owner/orphan removal refused. `CreateSchema`, `OpenDraft`, `EditDraft`, `DiscardDraft`, `PublishSchemaVersion`, `ChangeSchemaApplicability` and SchemaController examined. Future/latest coverage check bypass: AUD-011. |
| Inspection | ASSIGNED → IN_PROGRESS → CLOSED; reassign only ASSIGNED; record/remove answers, evidence and notes only IN_PROGRESS; closed → audited rectification without reopening | Wrong inspector, unknown criteria, invalid answer, wrong evidence label, free closed writes, close before start, rectification before close/time refused. `AssignInspection`, `ReassignInspection`, `StartInspection`, all input/removal use cases, `CloseInspection`, `RectifyClosedInspection`, controller DTO conversions examined. CriterionRecord writes are package-private. Rectification lacks absent-part guard: AUD-010. |
| Finding | Closure/revealed failure creates finding/action; persistent failure revises; approval voids obligation; recurrence reuses finding with new action after terminal predecessor | Approved findings and empty reasons refused. `FindingService`, `RectificationConsequences` and finding query/report paths examined. Reference-only correction refreshes presented evidence without reopening obligations. Findings have no independent status enum; obligation and revisions define state. |
| Corrective action | PENDING_PLANNING → PLANNED → EXECUTION_REPORTED; failed verification → PLANNED; satisfactory → CLOSED; nonclosed obligation voiding → VOIDED | Responsible alone plans; executor distinct from inspector reports; assigned inspector verifies; no planning after first plan; no execution before plan or verification before report. Expiry latches breach without terminal state, preserving late execution/verification. `PlanCorrectiveAction`, `ReportCorrectiveActionExecution`, `VerifyCorrectiveAction`, expiry sweep and FindingController examined. Mixed calendar dates: AUD-009. |
| Certificate | Factory issuance → VALID; historical-policy reconciliation → SUSPENDED; all causes resolved within validity → VALID; sweep → EXPIRED; renewal creates linked successor | No public constructor or public suspend/reactivate; direct global rejected for by-parts; undeclared partial refused; open/superseded inspection and competing live scope block; renewal before expiry/same inspection/old inspection refused. Issue/renew/derive/eligibility/report paths and CertificateController examined. No EXPIRED → VALID. Missing coverage can misclassify whole-asset scope: AUD-011. |

Maintenance paths: `MaintenanceJobs.runAll` invokes action sweep, outbox dispatch,
certificate sweep, pending-event publication and dispatch; scheduled and manual
paths share use cases. `PublishPendingDomainEvents` publishes pending events;
`CertificationReactions` rebuilds current scoped facts using the certificate's
historical policy. None directly writes inspection answers or changes frozen rules.
Delivery ordering, failures, concurrency and restart remain Step 3 work. HTTP
mutations use Transactions; the executed invalid rectification confirmed rollback
for that particular path, not for every aggregate/audit/outbox combination.

## Scenario results

| Scenario ID | Preconditions/action | Expected | Actual | Method/evidence | Outcome | Finding IDs |
|---|---|---|---|---|---|---|
| C-SCHEMA-01 | Open/edit/publish/discard draft; invalid empty/duplicate/incomplete draft | Only valid draft publishes; failures retain draft/history | Guards and publication-result behavior agree | InspectionSchema/SchemaDraft; SchemaPublicationTest asserts empty/duplicate IDs and all numeric gaps/overlaps; run 009 | Passed | — |
| C-SCHEMA-02 | Transfer/add applicability; duplicate owner/no-op/orphan removal | Single owner; all present parts covered | Ordinary latest-version guards hold; future interaction fails separately | SchemaApplicability, ChangeSchemaApplicability; ReviewCorrectionsIT and PartialCertificationIT assertions, run 009 | Passed | AUD-011 for future case |
| C-INS-01 | Reassign/start/load/close as wrong actor or wrong state | Reject without authorized transition | Root guards and actor captured by use cases; ordinary writes cannot change CLOSED | Inspection; SecondReviewIT actor and role assertions; InspectionExecutionIT; run 009 | Passed | — |
| C-EVAL-01 | Missing answer or mandatory evidence; multiple evidence labels/counts | REJECTED with preserved reasons, greatest severity | MissingAnswer HIGH; missing evidence adds reason without losing rule verdict | CriterionEvaluatorTest assertions of result/reasons/severity/counts; run 009 | Passed | AUD-004 at UI |
| C-EVAL-02 | Wrong answer type/unit/unknown option/out-of-domain measurement; inclusive and exclusive band edges | Invalid input refused; valid measurements evaluated exactly once | BigDecimal comparisons and adjacent-band publication validation agree | NumericRangeRule/NumericBand, MappedOptionsRule, YesNoRule; NumericAndYesNoRuleEdgeTest and SchemaPublicationTest, run 009 | Passed | — |
| C-EVAL-03 | Attach undeclared evidence/remove unknown input; load incomplete values progressively | Evidence matched to frozen label/type; incomplete inspection may continue/close | Input guards hold; closure evaluates missing data instead of accepting supplied verdicts | Inspection and CriterionRecord; ReviewCorrectionsIT direct-loading/closure assertions; run 009 | Passed | AUD-004 at UI; AUD-007 evidence representation question |
| C-RECT-01 | Rectify answers, notes or evidence references after closure | Preserve original, author/time/reason; evaluate frozen rules | Original and current values differ in act; root emits events when result or reasons change | RectificationIT, ReportingIT, ReviewCorrectionsIT, run 009 | Passed | AUD-001/002 at UI |
| C-RECT-02 | Repeated rectify: fail → approve → fail; prior verified correction | Old obligations remain historical; recurrence requires new correction | Same finding retained; terminal predecessor preserved; replacement action blocks and suspension follows | RecurringNonConformityIT asserts 2/3 actions, predecessor plans/status, reactivation; run 009 | Passed | — |
| C-RECT-03 | Correct only evidence reference; invalid target elsewhere in correction list | Refresh evidence without action/revision reset; invalid request atomic | Existing valid-reference case updates finding; unknown target prevalidation prevents preceding note mutation | RectificationIT evidence-refresh and atomicity assertions, run 009; exception below is a separate uncovered destination | Passed | — |
| C-RECT-04 | Rectify a closed inspection's criterion for an absent subsystem | Reject as business rule without mutation | Core appends rectification and answer then throws NoSuchElementException; HTTP 500; H2 inspection/audit unchanged after rollback | Executed Step2Probe run 012; HTTP/JAR/file-H2 run 013 | Failed | AUD-010 |
| C-F2-01 | Startup at effective instant minus 1ns, exactly, plus 1ns | Version 1/2/2; earlier inspection remains version 1 | All asserted; old inspection still version 1 after later closure | Step2Probe run 012; StartInspection selection uses captured Instant | Passed | AUD-005 cross-layer exact-startup gap remains |
| C-F2-02 | No effective version; multiple future schedules including equal effective times | Startup refused until effective; deterministic maximum version at tie | ASSIGNED retained on refusal; tied future lookup selects version 4 | Step2Probe run 012; InspectionSchema comparator and monotonic guard | Passed | — |
| C-F2-03 | Edit/discard draft or publish while inspection ongoing; relocate/change responsible | Existing inspection evaluates original schema and reports frozen asset | Immutable version/sections/rules and asset snapshot used; ownership of new finding uses current responsible by deliberate DESIGN policy | FrozenSchemaVersionTest asserts old result/severity, ReportingIT frozen owner; run 009; catalogue E03/E13 | Passed | — |
| C-TIME-01 | Planned due March 2; at March 3 00:30Z (March 2 21:30 Buenos Aires), sweep and eligibility | Same inclusive date interpretation | Local sweep expires 0, action is not overdue; UTC eligibility returns OverdueOpenAction | Executed controlled zone-aware Clock with real use cases, run 012 | Failed | AUD-009 |
| C-TIME-02 | Same instant; plan replacement obligation due local today | DESIGN says deadline may be Clock.today() | Plan rejects local today as before UTC planning date | Executed PlanCorrectiveAction with zone-aware Clock, run 012 | Failed | AUD-009 |
| C-TIME-03 | Verify at C-TIME-01 instant before local deadline ends | Shared deadline interpretation and retained breach if late | Local verification records metItsDeadline=true and clears UTC issuance blocker | Executed VerifyCorrectiveAction, run 012 | Failed | AUD-009 |
| C-TIME-04 | Due date itself, next date; late planning before sweep; late successful closure | Inclusive due date; breach survives extension/closure | `isAfter` includes due date; latch preserves first breached date; late plan does not erase unswept breach | CorrectiveAction; ReviewCorrectionsIT late-plan assertion; ordinary lifecycle tests run 009; zone exception above | Passed | AUD-009 for zone difference |
| C-CERT-01 | Before issuance/exact issuance/exact expiry; suspended expiry/reactivation | Validity is `[issuedAt, expiresAt)`; expired cannot reactivate | Exact endpoints honored independently of stored status; resolved causes remain historical | ValidityPeriod/Certificate; CertificateSuspensionTest and PolicyAwareCertificateLifecycleTest, run 009 | Passed | — |
| C-CERT-02 | Month-based validity at calendar boundary | UTC calendar Period, not fixed 30-day duration | FixedDurationValidityPolicy uses UTC plus Period; policy snapshot validates durations | Source inspection; jurisdiction duration assertions run 009; leap-day parameter coverage was inspected in existing policy tests, not newly executed here | Passed | — |
| C-CERT-03 | Renew before expiry/same/older inspection; new inspection after expiry | Refuse invalid renewal; link successor and preserve predecessor | Factory validates expiry and later startup; renewal references predecessor and current policy; old actions do not suspend successor | RenewalIT, ReviewCorrectionsIT, JurisdictionCertificationIT; run 009 | Passed | AUD-005 HTTP renewal still unexecuted |
| C-CERT-04 | Older inspection after later closed inspection; suspended competing scope | New issuance must use latest facts and avoid duplicate live scope | Factory detects later started CLOSED inspection; suspended certificate still covers its period and blocks another scope-equivalent certificate | SecondReviewIT superseded assertion; CertificateFactory/ValidityPeriod inspection; run 009 | Passed | — |
| C-F1-01 | Mixed results and shared criterion across present subsystems | Rejection isolated; shared result affects each applicable scope | Electrical rejection blocks electrical, pressure issues; shared rejection blocks all | PartialCertificationIT result/blocker assertions; run 009; catalogue E15 real API baseline evidence | Passed | — |
| C-F1-02 | Asset lacks one part or declares no parts | Absent-part criterion excluded; explicit whole-asset plan when no parts | No finding/evaluation for absent part; all criteria apply for explicitly empty asset parts | PartialCertificationIT absent-part act/answer assertions and whole-asset cases; run 009 | Passed | AUD-010 for rectification |
| C-F1-03 | Incomplete partials; independently expired/suspended partials | Global requires every present part in force | Explicit AllSubsystemsMustBeInForce rejects missing/suspended/expired/outside-window; derived validity intersects partial windows | Parameterized AllSubsystemsMustBeInForceTest assertions; run 009 | Passed | AUD-003 at UI |
| C-F1-04 | Expire/resolve one part or shared action; multiple suspension causes | Scope isolation; reactivate only when all causes resolved | One part reacts independently; shared action affects all; old inspection cannot affect renewal | PartialCertificationIT and RenewalIT assertions; run 009 | Passed | — |
| C-F1-F2-01 | Latest future version covers parts, current version has none; add new asset-type applicability and inspect now | Coverage safeguards prevent certification of uninspected parts | Core issues direct GLOBAL with 3 unevaluated facility parts; API/H2 persists VALID GLOBAL for 2 unevaluated factory parts | Step2Probe run 012; real HTTP/JAR/file-H2 run 013 | Failed | AUD-011 |
| C-F3-01 | Blocking/nonblocking severities, no plan, overdue action; regular/conditional profiles | Jurisdiction-specific decision plus common structural checks | Common checks enforced even for strategies; profiles differ; permitted pending result yields CONDITIONAL | ConfiguredJurisdictionCertificationPolicy/IssuanceRequirements; JurisdictionCertificationIT decisions/audit assertions, run 009 | Passed | AUD-009 deadline semantics |
| C-F3-02 | Unknown jurisdiction; changed/incorrect strategy snapshot | No fallback/mismatched policy certificate | Registry/factory refuse unresolved or mismatched definitions; unknown failure recorded | JurisdictionCertificationIT unknown-policy/registration assertions run 009; registry tests inspected, baseline evidence E17 | Passed | — |
| C-F3-03 | Policy revision between eligibility and issuance/retry/renewal | New request uses current policy; retry/report preserves historical policy | Issuance reevaluates rather than reserving eligibility; repeat returns existing policy; renewal uses revision 2; report preserves original | Factory code inspection; JurisdictionCertificationIT history assertions run 009 | Passed | — |
| C-F3-04 | Rectification/action resolution after policy revision | Reconcile current scoped facts under issued snapshot; keep mode/validity | Historically permitted HIGH rejection remains conditional; CRITICAL suspends; plan/verification resolves; regular cannot silently become conditional | CertificateLifecycle/CertificationReactions; JurisdictionCertificationIT historical and regular-mode tests, run 009 | Passed | — |
| C-F1-F3-01 | Conditional electrical partial revision 1 and regular pressure revision 2 | Global conditional with per-part provenance; unsafe electrical does not suspend pressure | Global carries revisions 1/2 and earliest expiry; electrical suspended while pressure VALID | JurisdictionCertificationIT.partialPoliciesAndModesArePreservedInConditionalGlobal assertions, run 009 | Passed | — |
| C-API-01 | DTO serializations, actor header, invalid input/error responses | Domain rules reflected in API types/status/body | Controllers invoke use cases; registered actor captured; ordinary 400/401/404/422 mappings match; absent-part rectification unexpectedly 500 | Controller/DTO/ActorInterceptor/ApiExceptionHandler inspection; run 013 actual response and reread | Failed | AUD-010 |
| C-UI-01 | Forms/types/rendered eligibility and deadlines versus domain | Spanish UI supports valid transitions and explains results | Forms carry actor and typed answer/evidence; disabled closed editing and absent-part input agree; missing rectification/act, invalid global row and zero-answer close restriction persist | CriterionCard, FindingDetail, InspectionDetail, CertificationPanel, types.ts and api.ts inspection; no browser run | Failed | AUD-001–004; AUD-009 local date contradiction |
| C-X-01 | Exact F2 startup plus persisted old snapshot over HTTP; zone-aware deadline over real API | Same verified boundaries across adapters | Core probes prove nanosecond startup and timezone inconsistency; no injected-clock HTTP execution for these exact cases | Explicit gap; real H2 probe uses actual system time, not controlled time | Unverified | AUD-005 |
| C-X-02 | Runtime policy revision followed by H2/process restart and historical report | Original policy/history survives configuration change and restart | Core revision/report assertions and stored snapshot inspection support intent; this session did not execute restart/configuration-change combination | Deferred Step 3; no crash/restart claim | Unverified | AUD-005 |

## Contradictions, precision and handoff

Three new defects are [AUD-009](findings.md#aud-009), [AUD-010](findings.md#aud-010)
and [AUD-011](findings.md#aud-011). DESIGN's `Clock.today()` planning description
contradicts UTC planning code, and UTC certification disagrees with zone-based
verification/sweeps. DESIGN's applicability coverage guarantee examines the
latest version instead of the selected effective version. DESIGN's prevalidated
rectification guarantee omits inapplicable destinations. Existing absent-part
documentation also inconsistently says criteria are evaluated: actual closure
skips them (DESIGN D23 and code agree; D22/17.1 wording is stale).

Schema and certificate boundaries use Instant nanoseconds in the core; schema
effective instant is inclusive, certificate expiry exclusive. REST uses ISO-8601
Instant and preserves the source value in document JSON. Date-only action
deadlines are inclusive, but the calendar basis is inconsistent as shown above.
Numeric rules use BigDecimal. Frontend datetime-local conversion uses browser
local time and ISO conversion; this pass did not prove browser/round-trip handling
of arbitrary fractional-second input or SQL timestamp precision under restart.

Next: Step 3 failure/recovery. Prioritize repeats/conflicts on scope and schema
applicability, atomic inspection/finding/audit/outbox writes, real file-H2 reopening,
dispatcher failures/DEAD retry and competing dispatchers. Run 013's invalid-input
rollback is narrow evidence. Step 4 still needs real frontend workflows, exact
HTTP renewal/F2 startup boundaries and policy retention after restart. Probes exit
their app processes, use isolated evidence databases, and never touch `data/`.
No fixes, requirement reinterpretations or debt acceptance were made.
