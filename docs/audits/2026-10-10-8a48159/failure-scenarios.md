# Step 3: failure and recovery

Reviewed revision: `8a4815960670ac1d467df4a8f0932c8932d2fab4`; review only.
The continuation rechecked HEAD, dirty documentation and the baseline executable
JAR SHA-256. Application and test sources remain unchanged.

Step 3 is complete as a review pass, including explicit verification gaps. The
expected behavior comes from both deliveries, AGENTS atomicity/history rules,
DESIGN sections 13–15/18 and the REST contract. A repeated operation need not
always succeed: issuance/closure reuse prior state, while publication without a
draft and verification of a closed action must reject without additional writes.

Evidence: [durable results and commands](evidence/step3-results.md),
[H2 probe](evidence/Step3Probe.java), [HTTP probe](evidence/step3_api_probe.py),
[validation](validation.md). Existing tests were inspected by their assertions
and executed in run 015. Each method below distinguishes execution from inspection.
Isolated databases/logs are under `target/audit-evidence/2026-10-10-8a48159/`;
the default database was not used.

| Scenario ID | Preconditions/action | Expected | Actual | Method/evidence | Outcome | Finding IDs |
|---|---|---|---|---|---|---|
| F-REPEAT-01 | Issue twice for one closed whole-asset inspection; also repeat a partial issuance | First creates, second identifies existing; no duplicate issuance audit | HTTP whole-asset retry ALREADY_ISSUED and audit identical; CertificationApiIT asserts partial retry 200 and only 3 partials | Runs 022 and 015; IssueCertificate:52 saves/audits only Issued | Passed | — |
| F-REPEAT-02 | Close twice | No duplicate evaluations/findings/closure audit | HTTP inspection and complete audit unchanged; core CertificationLifecycleIT checks finding count on repeat closure | Runs 022/015; CloseInspection:57–75 | Passed | — |
| F-REPEAT-03 | Publish again with no draft | Reject; preserve version/audit | 422; schema audit unchanged | Run 022 real API/file H2 | Passed | — |
| F-REPEAT-04 | Verify a satisfactorily closed action again | Reject without duplicate verification/audit/events | 422; finding, audit and outbox identical | Run 022 real API/file H2; CorrectiveAction:114 requires EXECUTION_REPORTED | Passed | — |
| F-REPEAT-05 | Repeat maintenance after action expiry | Single breach/notification; stable suspension | ProcessesApiIT asserts one notification and SUSPENDED after second sweep | Run 015; controlled SteerableClock | Passed | — |
| F-REPEAT-06 | Successful renewal then retry same new inspection | Reuse linked successor without extra audit | Factory:175 returns AlreadyIssued; RenewCertificate:57 audits only Issued; existing RenewalIT proves one successful link but does not retry it | Code/test inspection; successful renewal run 015 | Unverified: repeated real-H2/HTTP renewal not executed | AUD-005 |
| F-REPEAT-07 | Renew before predecessor expiry | Reject without unrelated successor | RenewalIT rejects under controlled time; HTTP returns 422 | Runs 015/022 | Passed | — |
| F-CONCUR-01 | Two transactions edit same stored asset | Losing stale save rejects; preserve winning state | JdbcTransactionsIT coordinates reads/commits with latches and asserts StaleAggregateException plus winning location | Run 015 real H2 | Passed | — |
| F-CONCUR-02 | Six issuance requests for same inspection/scope; six create requests for same schema type | One winner; others 200/409/422; no duplicate certificate or applicability | ConcurrencyApiIT asserts exactly one 201 and one stored certificate/type owner | Run 015 real HTTP/H2; V1 unique inspection/scope and applicability PK | Passed | — |
| F-CONCUR-03 | Six simultaneous assignments to an asset with no open inspection | At most one open inspection | Targeted concurrent HTTP/H2 regression returns one 201 and persists one open inspection after per-asset row locking | `ConcurrencyApiIT.ofSeveralSimultaneousAssignmentsForOneAssetOnlyOneOpenInspectionIsCommitted`, T-APP | Passed | AUD-014 resolved |
| F-CONCUR-04 | Six transfers of EQUIPMENT from a source retaining LABORATORY to two published targets | One complete transfer, single owner; losers conflict/business rejection | One 200, three 409, two 422; one current owner | Run 022; transaction wraps all saves; applicability PK and row_version checked | Passed for this interleaving | — |
| F-CONCUR-05 | Parallel first issuance/renewal using distinct backing inspections for one asset/scope | No parallel live certificates or broken predecessor chain | Latest-inspection/live-certificate guards inspected; unique DB index covers inspection/scope, not asset/scope | Factory:214; V1:73; no coordinated distinct-inspection race executed | Unverified | AUD-005 |
| F-ROLLBACK-01 | Throw after aggregate save, audit append and outbox enqueue | All three absent | Party, audit and outbox all empty | Run 021 injected exception, production transactions and real H2 | Passed | — |
| F-ROLLBACK-02 | Handler saves aggregate/audit then throws | Handler writes rollback; original event remains retryable | Handler party/audit absent; event PENDING attempts=1 | Run 021 real H2; dispatcher separate failure transaction | Passed | — |
| F-ROLLBACK-03 | Use-case refusal / constraint conflict / nested transaction / invalid rectification | No partial persisted aggregate/audit state | JdbcLifecycleIT refusal leaves state/audit unchanged; invalid absent-part rectification now rejects before mutation and HTTP rereads preserve inspection/audit | Run 015 plus `InspectionApiIT` T-APP and `PartialCertificationIT` T-CORE | Passed for covered paths | AUD-010 resolved |
| F-ROLLBACK-04 | Inject failure between every pair of closure/finding, transfer, expiry and renewal saves | Aggregate, audit and events atomic for each mutation | Outer controller/jobs transactions and shared adapters inspected; generic real-H2 rollback proven; every application save boundary not injected | Code inspection; JdbcLifecycleIT uses synchronous test publisher, not production outbox | Unverified: exhaustive boundary injections | AUD-005 |
| F-RESTART-01 | Seed party/audit/PENDING event in file H2; exit normally; reopen in a separate JVM and dispatch twice | Reconstruct committed state; recover pending event once | Party/audit/event retained; reaction persisted, DONE=1, first dispatch=1/repeat=0 | Run 017, two Java source-launcher JVMs, actual file H2 | Passed: ordinary small-state reopen | — |
| F-RESTART-02 | Packaged process, completed requests, forced Windows termination, same file database reopened | Record what survives; do not infer graceful or in-flight crash recovery | Inspection/act/certificate/frozen policy/report/outbox equal; audit and schemas differ; repeat issuance ALREADY_ISSUED. Run 022 audit 44 before versus 31 after; schema count 3 before versus 1 after | Runs 019/020/022; subprocess terminate is forced termination, not graceful shutdown | Failed: all-state retention under this forced stop | AUD-015 |
| F-RESTART-03 | Historical policy revision, partial suspension/expiry and scheduled schema changes across graceful process restart | Preserve historical references and continue required workflows | JdbcLifecycleIT reconstructs certificate/actions/rectification from same memory datasource; Step 2 tests preserve policy snapshots; full combined file/process workflow absent | Existing executed tests plus source inspection; not equivalent to process restart | Unverified | AUD-005 |
| F-RESTART-04 | Abrupt interruption during transaction/claim/commit or hardware/storage failure | Defined recovery/durability guarantees | No in-flight interruption or disk/power-failure experiment; forced stop above only after requests | No execution | Unverified | AUD-005, AUD-015 |
| F-OUTBOX-01 | Publish inside a unit of work; rollback or commit | No precommit handling; enqueue follows original transaction | OutboxIT asserts visibility only after commit and no row on rollback | Run 015 real H2 | Passed | — |
| F-OUTBOX-02 | Handler fails once, then recovers | Original save retained; attempt/error recorded; eventual DONE | OutboxIT asserts party present, attempts=1/error and successful retry | Run 015 real H2 | Passed for isolated event | — |
| F-OUTBOX-03 | Permanent failure with maxAttempts=3; manual revival | DEAD at limit; no automatic delivery from DEAD; revival resets attempts | OutboxIT and ProcessesApiIT assert DEAD, skip, retry-dead and DONE; AdminApiIT checks empty retry | Run 015 H2 and HTTP; settings default max=10, test limit=3 | Passed for isolated event | — |
| F-OUTBOX-04 | Four healthy dispatchers race on 20 rows | One committed effect per event | OutboxIT asserts exactly 20 deliveries/DONE | Run 015 real H2 | Passed for healthy handlers | — |
| F-OUTBOX-05 | Corrupt/unknown event document or type | Other rows remain visible; broken row retryable/parked | Decode failure is recorded per row; corrupt-row recovery itself remains unexecuted; sequence cursor prevents an earlier row from hiding later rows | Code inspection and F-OUTBOX-07 regression, T-OUTBOX; no corrupt-document execution | Unverified | AUD-005 |
| F-OUTBOX-06 | One failing row mixed with healthy rows in one call | Failed row waits for next pass per DESIGN:591 | Real H2 regression leaves one attempt on the failing row while all 51 later healthy events are delivered once | `OutboxIT.aFailingRowWaitsForTheNextPassAndDoesNotHideLaterHealthyRows`, T-OUTBOX | Passed | AUD-012 resolved |
| F-OUTBOX-07 | First 50 rows fail; row 51 is healthy | Examine recovery fairness and availability | Pass cursor advances past the first 50 failures and delivers the healthy 51st row; each failure remains at one attempt | Same 51-row real-H2 regression, T-OUTBOX | Passed | AUD-012 resolved |
| F-OUTBOX-08 | Dispatcher A fails, pause its failure bookkeeping; B succeeds; resume A | B's DONE must remain terminal; successful DB effects once | Conditional failure update leaves `DONE` terminal; regression confirms one committed handler effect | `OutboxIT.delayedFailureBookkeepingCannotReopenAnAlreadyDoneRow`, T-OUTBOX | Passed | AUD-013 resolved |
| F-NOTIFY-01 | Real webhook succeeds, replies 500, is unreachable or exceeds timeout | Channel success/failure observable with finite timeout | WebhookNotificationSenderIT asserts JSON/status/exception; NotificationWebhookApiIT observes actual HTTP POST | Run 015; timeout test delays endpoint only, not domain clock | Passed | — |
| F-NOTIFY-02 | Notification sender fails during action-expiry handling | Original operation retained; suspension completes; no DEAD for channel outage | ProcessesApiIT asserts SUSPENDED and no pending/dead expiry event; unit test asserts failure callback | Run 015 real API/H2, controlled clock, substituted failing sender | Passed | — |
| F-NOTIFY-03 | Webhook accepted, then later DB handler/commit fails | Distinguish external side effect from DB rollback | Notification is inside handler transaction; DB rollback cannot undo HTTP; DESIGN:857 explicitly documents possible loss/duplicate, no independent retry | Code/debt inspection; later-commit failure with real webhook not injected | Unverified runtime; documented limitation, no new defect solely for this | AUD-005 |

## Assessment and remaining checks

Three new defects are reproducible without application changes: batch retries,
completed-event resurrection and concurrent assignment. Retry-limit and healthy
dispatcher tests passing do not cover these failure interleavings. The
forced-stop observation is a separate recovery-policy question (AUD-015), with
the actual retained/lost results preserved; no database corruption, graceful
restart failure or storage-failure mechanism is inferred.

Continue Step 4 with required browser workflows and a deliberately graceful
packaged-process shutdown/restart. Add controlled-clock HTTP successful renewal
and repeated renewal, F2 startup/retention and policy/history cases where
practical. Distinct-inspection issuance races, every save-boundary injection,
corrupt event rows, in-flight crashes and external-send/commit failure remain
explicit gaps under AUD-005. No repairs or accepted limitations were made.

## Step 4 recovery evidence update

Final packaged run 027 uses JVM shutdown hooks, graceful HTTP shutdown and
Hikari closure; final controlled run 030 uses context.close and ordinary exit.
Both compare 13 persisted workflow GET snapshots and continue an old-version
inspection after reopening file H2. Successful/repeated HTTP renewal, policy
revision and historical reports/partials/audit are now executed. See
[Step 4](end-to-end.md) and [durable results](evidence/step4-results.md).
This supplements the earlier gaps and narrows AUD-005. AUD-015 remains Open:
ordinary clean shutdown cannot settle forced-stop durability or loss previously
observed. Advanced concurrency/rollback/delivery/crash gaps remain as listed.

## Step 5 repair update

F-CONCUR-03, F-ROLLBACK-03's inapplicable-rectification case, and F-OUTBOX-06/07/08
pass post-fix regressions (`T-APP`, `T-CORE`, `T-OUTBOX`). F-OUTBOX-05 remains
Unverified for corrupt documents; its prior fairness concern is fixed but no
corrupt payload was injected. Distinct-inspection issuance races, exhaustive
save-boundary injections, external-send/commit interleavings and forced-stop
durability remain open under AUD-005/AUD-015. See [Step 5 repairs](step5-repairs.md).
