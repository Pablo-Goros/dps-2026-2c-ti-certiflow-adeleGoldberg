# Baseline validation and evidence provenance

Reviewed application revision: `8a4815960670ac1d467df4a8f0932c8932d2fab4`. Initial dirty documentation state is in [README](README.md) and [initial status](evidence/initial-status.txt). No application code changed between the runs or afterward. Repository root working directory: `C:\Users\Mateo\Documents\Projects\tps\dps_tp2`.

| Run ID/time (UTC-03:00) | Revision/local changes | Working directory/command | Configuration | Exit/result | Evidence path | Limitations |
|---|---|---|---|---|---|---|
| 001-baseline / 2026-10-10 19:08:19.927–19:08:26.696 | Reviewed HEAD + initial docs + audit folder | Root; `mvn --batch-mode --no-transfer-progress verify` | Default JAVA_HOME JDK 21.0.10; no skips | 1; Java enforcer rejected JDK 21 before compilation/tests; infrastructure/app skipped | `target/audit-evidence/2026-10-10-8a48159/001-baseline/` | Environment failure, not an application defect. Existing report snapshots cannot be attributed to this run. |
| 002-jdk26 / 2026-10-10 19:08:53.204–19:11:05.185 | Same application state + audit docs | Root; `mvn -version`, then `mvn --batch-mode --no-transfer-progress verify` | Process-local JAVA_HOME `C:/Program Files/Java/jdk-26.0.2`; JDK 26.0.2, compiler release 25; no frontend/test skips | 0; all reactor modules SUCCESS; full verification and exec JAR packaging passed | `target/audit-evidence/2026-10-10-8a48159/002-jdk26/`; durable [summary](evidence/baseline-results.md), [build excerpt](evidence/baseline-build-excerpt.txt), [JAR hash](evidence/jar-sha256.txt) | Installed JDK 25 not found under Program Files/Java; JDK 26 used without changing build settings. Not a JDK 25 runtime test or packaged-process/browser exercise. |
| 003-settings / 2026-10-10 19:09:12–19:09:46 | Same local application state; remote main differs | Root; read-only `Invoke-WebRequest` GitHub repo, main, protection, rulesets, ruleset 24847806 | Unauthenticated API, Accept application/vnd.github+json; retry with UseBasicParsing | Shell captures succeeded; initial parser requests returned no usable response; repo/main/rulesets/details 200 after retry; legacy protection 401 | [settings](evidence/github-settings.txt), [ruleset](evidence/github-ruleset.json.txt) | Current settings inspection only; no failing PR merge experiment. Public ruleset suffices despite inaccessible legacy endpoint. |
| 004-environment / 2026-10-10 19:14:34 | Same reviewed HEAD | Root; `git rev-parse HEAD`, `git status --short`, `git remote -v`, `Get-Date -Format o`, `Get-TimeZone`, OSVersion, `java -version`, `mvn -version`; relevant Env names/config source inspection | Initial environment again shows default Maven JDK 21 and PATH Java 26; no relevant app env overrides observed | Successful inspection; Java emits version to stderr (PowerShell formats NativeCommandError records, not a failed Java execution) | [environment](evidence/environment.txt), [configuration locations](evidence/configuration-locations.txt), [initial status](evidence/initial-status.txt) | Only relevant application override keys inspected; no secrets dumped. Process-local JDK 26 run did not persistently change JAVA_HOME. |
| 005-report-generation / 2026-10-10T19:20:01.0766674-03:00 to 2026-10-10T19:20:02.1070282-03:00 | Same code, reports being written | Root; Python `build_reports.py` and `requirements_report.py` under bulk evidence directory | Read source, timestamp-filter snapshot XML, build evidence catalogue/matrix | Successful generation: 23 evidence groups, 113 requirement rows; zero stale TEST XML in run 002 | Generated [catalogue](evidence/requirements-catalogue.md), [static excerpts](evidence/static-findings.md), [requirements](requirements.md); local helper scripts under `target/audit-evidence/2026-10-10-8a48159/` | Helpers only create review evidence. Their first run detected a wrong source filename and stopped before writing; corrected to SearchAssets. Reports are not application regression tests. |

## Results attributable to run 002

Report integrity execution `006-report-check` ran `python target/audit-evidence/2026-10-10-8a48159/check_reports.py` from the root. The final run at 2026-10-10 19:22:01.595–19:22:02.807 UTC-03:00 exited 0 and checked 586 relative links/anchors/source line bounds, unique requirement IDs and final assessment counts, unchanged HEAD and empty application/contract diff. An earlier successful run at 19:21:37.686–19:21:39.060 checked 582 links before the final handoff references were added. [Recorded final result](evidence/report-checks.json) and [final status](evidence/final-status.txt) preserve the result. The initial check at 19:20:53–19:20:54 exited 1 because the empty diff-evidence file had not yet been created; the file was created and subsequent checks passed. This is report integrity validation, not application testing. Final requirements assessment was refined to count the existing findings list/detail as a basic summary: no particular UI summary endpoint is mandated.

| Module | Unit tests | Integration tests | Failures/errors/skips |
|---|---:|---:|---|
| core | 237 | 125 | 0 / 0 / 0 |
| infrastructure | 16 | 35 | 0 / 0 / 0 |
| app | 36 | 72 | 0 / 0 / 0 |

Total backend: **521** tests. Frontend: **20** tests in **2** Vitest files passed. These numbers are from the current snapshot XML and Maven log, not DESIGN or an earlier report. npm build (TypeScript/Vite) also passed in this full Maven execution.

Each of the three modules' `target/surefire-reports`, `target/failsafe-reports`, and `target/site/jacoco` was copied into the respective run directory, with original file timestamp/size manifests. All nine directories existed in run 002; its TEST XML timestamps fell between start/end and matched executed classes/counts. Run 001 produced no fresh test reports: any copied existing reports there are stale and are explicitly excluded. No missing report directory is claimed when it existed.

This was `verify`, not `clean verify`. JaCoCo HTML/XML was regenerated, but pre-existing execution data can be cumulative; no current-only coverage percentage is asserted. JDK 26 reflective-final-field/native/Unsafe warnings appeared without failing verification, consistent with documented codec/toolchain debt. Logs also contain deliberate failure-scenario exception traces; they are not test failures when the current XML and reactor result establish success.

## Application configuration examined

Defaults: HTTP 8080; H2 file `jdbc:h2:file:./data/certiflow`, user `sa`, empty password; Flyway disabled in Spring because `JdbcPersistence` owns migrations. `certiflow.zone=America/Argentina/Buenos_Aires`; `SystemClock.now()` returns an Instant and `today()` uses the configured zone. Certificate issuance context derives its calendar date in UTC (`CertificateFactory.contextFor`); corrective action scheduling uses `Clock.today()`. This distinction is recorded for Step 2 boundary review, not resolved by assuming one zone.

Jobs default enabled, sweep interval PT1M, dispatcher interval PT5S, initial delay PT30S; outbox max attempts 10. Logging notification default, blank webhook URL, timeout 3 seconds; best effort channel failure is documented. Profiles REFERENCE/AR-BA/AR-CBA are code-level example policy definitions, not actual regulations. The full baseline API suite disables scheduled jobs through the app Failsafe configuration, uses in-memory isolated H2, and runs maintenance on demand; time-sensitive process tests use SteerableClock. Repository tests use fresh UUID-named H2 memory databases. No default file database was intentionally used for a reproduction.

## Remaining validation

Baseline complete after environment correction. Step 2 is complete as a review pass, with executions below. Step 3 is now reviewed with executions below and explicit gaps. Step 4 browser workflows, exact controlled-clock HTTP successful renewal/F2 startup and zone-aware deadlines remain unverified; advanced failure/recovery gaps are in failure-scenarios.md. Narrow packaged-JAR/file-H2 probes do not substitute for those scenarios. No repairs or post-repair validation occurred.

## Step 2 executions

Checkout remained the baseline HEAD plus preserved dirty documentation and audit
reports. No application/test-source changes. All commands ran at repository root.
Exact commands and durable outcomes: [Step 2 evidence](evidence/step2-results.md).

| Run ID/time (UTC-03:00) | Command/configuration | Exit/result | Evidence | Limits |
|---|---|---|---|---|
| 007-step2-probes / 2026-10-10T19:25:57.7794525-03:00 to 2026-10-10T19:26:12.5805313-03:00 | JDK 26 Java source launcher, core compiled production/test-fixture classes; controlled clocks | 1; Date/rectification defects reproduced; F2 harness failed because preceding inspection was left open | `target/audit-evidence/2026-10-10-8a48159/007-step2-probes/` | In-memory ports; not real H2/API |
| 008-step2-probes / 2026-10-10T19:26:26.3441398-03:00 to 2026-10-10T19:26:31.4446321-03:00 | JDK 26 Java source launcher, core compiled production/test-fixture classes; controlled clocks | 0; Incremental probe passed | `target/audit-evidence/2026-10-10-8a48159/008-step2-probes/` | In-memory ports; not real H2/API |
| 009-step2-core / 2026-10-10T19:26:48.4421749-03:00 to 2026-10-10T19:27:46.5983622-03:00 | mvn --batch-mode --no-transfer-progress -pl core verify with explicit 8 unit/11 integration class selectors; process-local JAVA_HOME JDK 26 | 0; BUILD SUCCESS; 69 unit + 113 integration = 182 passed; 0 failures/errors/skips | `target/audit-evidence/2026-10-10-8a48159/009-step2-core/` | Focused core only; class/time-filtered XML snapshotted; no new full reactor/frontend run or coverage percentage |
| 010-step2-api / 2026-10-10T19:27:34.740511-03:00 to 2026-10-10T19:27:58.611742-03:00 | Python step2_api_probe.py; baseline exec JAR, JDK 26, port 18082, jobs disabled, isolated file H2 | 0; HTTP 500 on absent-part rectification; inspection/audit unchanged | `target/audit-evidence/2026-10-10-8a48159/010-step2-api/` | Actual system time; narrow HTTP/H2 evidence, no browser/restart or universal rollback proof |
| 011-step2-probes / 2026-10-10T19:28:10.1551010-03:00 to 2026-10-10T19:28:23.7600786-03:00 | JDK 26 Java source launcher, core compiled production/test-fixture classes; controlled clocks | 0; Incremental probe passed | `target/audit-evidence/2026-10-10-8a48159/011-step2-probes/` | In-memory ports; not real H2/API |
| 012-step2-probes / 2026-10-10T19:28:52.1446791-03:00 to 2026-10-10T19:28:55.2443034-03:00 | JDK 26 Java source launcher, core compiled production/test-fixture classes; controlled clocks | 0; Final probes passed; 3 defects reproduced; F2 -1ns/0/+1ns startup and frozen old version asserted | `target/audit-evidence/2026-10-10-8a48159/012-step2-probes/` | In-memory ports; not real H2/API |
| 013-step2-api / 2026-10-10T19:29:16.403604-03:00 to 2026-10-10T19:29:27.457378-03:00 | Python step2_api_probe.py; baseline exec JAR, JDK 26, port 18082, jobs disabled, isolated file H2 | 0; HTTP 500 on absent-part rectification; inspection/audit unchanged; GLOBAL/VALID certificate persisted for two uninspected factory parts | `target/audit-evidence/2026-10-10-8a48159/013-step2-api/` | Actual system time; narrow HTTP/H2 evidence, no browser/restart or universal rollback proof |

Baseline full run 002 is reused only for unchanged application code/JAR. Step 2 is completed with explicit unverified scenarios, not complete-system validation. No repairs/post-repair tests occurred. Report-generation/integrity execution is recorded in [Step 2 report checks](evidence/step2-report-checks.json).

Report-only helper `python target/audit-evidence/2026-10-10-8a48159/step2_reports.py`
exited 0, timestamp-filtered and snapshotted the 19 selected XML reports, and
generated the durable evidence/matrix/README/validation updates. Integrity helper
`python target/audit-evidence/2026-10-10-8a48159/step2_check.py` initially stopped
on Windows text encodings (CP1252 and UTF-16 baseline hash evidence); explicit
UTF-8/BOM-aware reads corrected the helper. Its first completed check exited 1
because its linked output JSON had not yet been created. Subsequent final check
checks the created output, links, 113 matrix rows/counts, 11 finding IDs, 35
scenario IDs, reviewed HEAD, empty source/contract diff and unchanged baseline
JAR hash. Exact final run times, command/result and preserved checkout status are
in [Step 2 checks](evidence/step2-report-checks.json) and
[status](evidence/step2-final-status.txt). Helper setup errors are audit-tool
issues, not application failures.

## Step 3 executions

All commands ran at repository root, reviewed baseline HEAD plus preserved dirty
documentation and audit reports. Application/test/contract sources unchanged.
Process-local JDK 26.0.2; isolated H2 only. Exact commands and evidence are in
[Step 3 results](evidence/step3-results.md).

| Run/time UTC-03:00 | Command/configuration | Exit/result | Evidence/limits |
|---|---|---|---|
| 014-step3-tests / log ends 19:38:16 | Focused Maven command, unquoted dotted properties in PowerShell | 1; unknown lifecycle phase .frontend=true; no tests | Bulk run 014 maven.log; argument parsing issue, not application defect; baseline JAR copied before execution |
| 015-step3-tests / 19:38:42.394–19:40:22.653 | Quoted focused `mvn --batch-mode --no-transfer-progress verify -Dskip.frontend=true`, explicit class selectors | 0; 73 backend tests, no failures/errors/skips | Bulk run 015 log/XML snapshot; 15 current XMLs, [summary](evidence/step3-test-summary.json). Frontend skipped; not full validation |
| 016-step3-probes / approximately 19:40–19:41 (file creation/command log; no dedicated start/end metadata) | Java source launcher, production core/infrastructure classes plus extracted baseline dependencies | 0; initial H2 probe assertions passed | Bulk run 016; superseded by timestamped final run 021; not a packaged API test |
| 017-step3-restart / 2026-10-10T19:40:59.1763395-03:00 to 2026-10-10T19:41:09.5980398-03:00 | Two Java source-launcher JVMs, `seed` then `recover`, isolated file-H2 URL | Both 0; ordinary reconstruction and once-only pending recovery passed | Bulk run 017 seed/recover logs; small fixture, normal exit, not complete certificate/history workflow or crash proof |
| 018-step3-api / approximately 19:42 (no complete metadata; transcript saved) | Python HTTP probe, baseline packaged JAR, port 18083, jobs disabled, isolated file H2 | 1; helper shadowed concurrent module; first repeat scenarios completed, race/reopen not reached | Bulk run 018 http-before.json/log; helper corrected; not counted as complete scenario execution |
| 019-step3-api / 19:43:21.819–19:43:53.590 | Same HTTP probe with corrected concurrency helper, new isolated file H2 | 1; all-audit equality assertion after forced termination failed (33 to 32 entries); assignment race reproduced | Bulk run 019 metadata/http/logs. Transfer fixture was invalid (source last type); not valid transfer-race evidence |
| 020-step3-api / 19:44:46.643–19:45:07.530 | HTTP probe with valid transfer fixture and explicit retained-state result, baseline JAR/file H2 | 0; repeat probes, assignment race, single transfer owner; forced stop lost recent audit state (33 to 12) | Bulk run 020; exit is probe completion, not all-state recovery success |
| 021-step3-probes / 19:44:56.706–19:45:06.176 | Final Java H2 source probe | 0; 3 outbox/rollback observations asserted including DONE resurrection | Bulk run 021; [durable output](evidence/step3-results.md). Memory H2 for concurrency/rollback; no generic exactly-once guarantee |
| 022-step3-api / 19:46:24.911–19:46:45.723 | Final Python HTTP probe adds repeat corrective verification and schema restart comparison; baseline JAR, port 18083, jobs disabled, isolated file H2 | 0; repeated operation assertions pass; concurrent assignment stores 3/4/2 opens; forced stop retained earlier certificate but lost recent schemas/audit | Bulk run 022; [metadata](evidence/step3-api-results.json), [selected requests](evidence/step3-http-excerpts.json). No browser/graceful shutdown/in-flight crash |

The snapshot helper timestamp-checked all 15 selected XMLs before copying them;
totals are 10 unit and 63 integration tests. No current-only coverage claim.
Run 015 repackaged app/target; Step 3 packaged probes use the full-baseline copy
made in run 014. Both hashes and commands are preserved in durable results.
Full run 002 remains the unchanged-source full/frontend baseline. No repair or
post-repair validation occurred; complete-system validation remains pending.

Report update/integrity helper executions and final preserved checkout status
are recorded in [Step 3 checks](evidence/step3-report-checks.json).

Report helper python target/audit-evidence/2026-10-10-8a48159/step3_reports.py initially exited 1 on UTF-16 PowerShell log decoding, then on an excerpt end line beyond its source length; BOM-aware decoding and bounded excerpts corrected it, and its final execution exited 0. The integrity helper first stopped because its expected scenario count was 30 while the report has 31 unique scenarios; the assertion was corrected, then it exited 0. These were report-tool issues, not application test failures. Final integrity execution checks 705 relative links/source line bounds, 113 requirement rows and counts, 15 findings, 31 failure scenarios, unchanged HEAD, empty source/contract diff and both JAR hashes. Exact final run times and result are in the linked checks JSON. Process inspection found no remaining Step 3 probe or packaged JVM.
