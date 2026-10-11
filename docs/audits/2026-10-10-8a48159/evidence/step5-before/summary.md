# Step 5 pre-fix regression evidence

Reviewed baseline: `0b747c473f3ba6915ccaa3eb7754ec6e8693de43`. The repair tests below were run against that unchanged application code before the corresponding fix. The initial frontend red run was repeated in an isolated detached worktree so its output could be retained; the backend pre-fix Surefire reports were copied from their original run.

| Finding | Pre-fix reproduction | Durable evidence |
|---|---|---|
| AUD-001 | `InspectionDetail.test.tsx`: closed-inspection correction action was absent; expected rectification POST was never made. | [frontend red run](frontend-baseline-vitest.log), exit 1 |
| AUD-002 | The closed detail never requested or rendered the act. | [frontend red run](frontend-baseline-vitest.log), exit 1; actual act HTTP success in [Step 4](../step4-results.md) |
| AUD-003 | By-parts certification requested direct whole-asset eligibility and rendered a global issuance row. | [frontend red run](frontend-baseline-vitest.log), exit 1; actual endpoint behavior in [Step 4](../step4-http-excerpts.json) |
| AUD-004 | The zero-answer inspection close button remained disabled. | [frontend red run](frontend-baseline-vitest.log), exit 1; HTTP closure succeeded in [Step 4](../step4-results.md) |
| AUD-009 | Zone-aware core and HTTP probes disagreed between local-date expiry/verification and UTC planning/eligibility. | [Step 2 probe](../step2-results.md), [Step 4 HTTP reproduction](../step4-http-excerpts.json) |
| AUD-010 | Known but inapplicable correction mutated the in-memory inspection then threw `NoSuchElementException`; the real API returned 500. | [core report](core-PartialCertificationIT.txt), [API report](app-InspectionApiIT.txt), [Step 2 HTTP evidence](../step2-results.md) |
| AUD-011 | Applicability addition and inspection start accepted an undercovered version; unsafe global issuance was also reproduced through actual HTTP/H2. | [core report](core-FutureEffectiveSchemaIT.txt), [Step 2 HTTP evidence](../step2-results.md) |
| AUD-012 | A failed outbox row was retried within one call and an earlier failing backlog hid a healthy later row. | [outbox report](infrastructure-OutboxIT.txt), [Step 3 real-H2 evidence](../step3-results.md) |
| AUD-013 | Late failure bookkeeping changed a completed row away from `DONE`; the real-H2 interleaving committed duplicate handler effects. | [outbox report](infrastructure-OutboxIT.txt), [Step 3 real-H2 evidence](../step3-results.md) |
| AUD-014 | Simultaneous assignment requests produced two successful responses and two open inspections in the API regression. | [concurrency report](app-ConcurrencyApiIT.txt), [Step 3 HTTP evidence](../step3-results.md) |

The captured frontend output has **4 tests failed of 4**. Core pre-fix runs report 2 failures in `FutureEffectiveSchemaIT` and 1 failure plus 1 error in `PartialCertificationIT`; outbox reports 2 failures in 8 tests; application reports 1 failure in `ConcurrencyApiIT` and 1 failure in `InspectionApiIT`. These are intentionally red baseline results, not validation of the fixed code.
