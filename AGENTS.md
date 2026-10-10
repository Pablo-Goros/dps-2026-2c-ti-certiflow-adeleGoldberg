# Project instructions

## Project context

CertiFlow is a Java 25 / Spring Boot application with a React / TypeScript frontend
and JDBC persistence on H2. Read `README.md` for build and run commands.

- Requirements: `docs/entrega_1.md` and `docs/entrega_2.md`.
- Design decisions and deliberate technical debt: `DESIGN.md`.
- Architecture: `docs/arquitectura-hexagonal.md`.
- REST contract: `docs/API.md`; frontend guidance: `app/frontend/README.md`.

Use these documents to establish intended behavior, then check the implementation
and tests. Report contradictions explicitly rather than assuming any one source
proves correctness.

## Architecture and data rules

- Preserve `app -> infrastructure -> core` and, within the core,
  `adapter -> application -> domain`. Keep Spring, JDBC, and delivery concerns out
  of the core. Controllers invoke application use cases rather than repositories.
- Preserve published schema versions, inspection snapshots, and certification
  policy snapshots. Changes to current configuration must not rewrite historical
  inspections, certificates, audit records, or reports.
- Keep related aggregate changes, audit writes, and outbox enqueueing atomic.
  Event handlers run after commit; a delivery failure must preserve the committed
  operation and leave failed delivery recoverable under the documented retry policy.
- Preserve partial certification isolation and the explicit policy that derives
  global certification. Shared criteria must retain their documented effect on
  each applicable subsystem.
- Domain class and field names participate in the persisted document format.
  Before renaming or changing stored state, assess existing-data compatibility
  and provide a migration or explicitly document the limitation.
- For REST changes, check frontend types and consumers, actor handling, HTTP
  statuses, and error payloads against `docs/API.md`. Update affected contracts
  together. Keep user-facing frontend text in Spanish.

## Validation and review

- Establish review scope: working changes, a commit range, or the complete system.
  Review requests produce findings; modify code when fixes are requested.
- For a complete system audit, follow `docs/system-audit-plan.md` for the pass
  sequence, report locations, evidence formats, and session handoffs.
- For a system review, trace both deliveries' requirements through implementation,
  tests, persistence, API, and frontend. A passing suite is evidence, not proof of
  requirement completeness. Distinguish missing coverage from confirmed defects.
- Prioritize invalid state transitions, exact time boundaries, historical data,
  partial/global interactions, jurisdiction differences, repeat requests,
  concurrent writes, rollback, restart recovery, and event delivery failures.
- Each defect finding must include severity, file/line, a concrete triggering
  scenario, and its consequence. Separate questions and optional improvements.
  Treat documented debt as context; explain any requirement violation it causes.
- For behavior changes, run relevant existing tests and add regression coverage
  when it demonstrates a meaningful failure. Persistence/API checks should use
  real H2/API behavior rather than only mocks; time-sensitive tests use a
  controlled clock instead of sleeps.
- `mvn verify` is the complete validation command, including frontend checks and
  integration tests. `mvn verify -Dskip.frontend=true` is a backend-only check;
  `npm test` and `npm run build` in `app/frontend` check the frontend independently.
  Run full verification before declaring a system audit validated.
- Report commands actually run, results, and unverified areas. Do not repeat old
  test counts or coverage claims as evidence for the current checkout.
- Update `DESIGN.md` when a design decision or deliberate debt changes, and update
  affected requirements/contract documentation when agreed behavior changes.

## Documentation and CI rules

- Keep the root `README.md` in English and descriptive: project purpose,
  capabilities, structure, build/runtime reference, and documentation links.
  Put contributor and agent rules in `AGENTS.md`.
- Keep `mvn verify` as the CI validation entry point. Configure GitHub branch
  protection to require the **Build and test** check before merging into `main`.
  A workflow alone does not enforce branch protection; verify the repository
  setting when auditing delivery compliance.

## Response formatting

- Do not use LaTeX display-math delimiters (`\[` / `\]` or `$$`). Render
  mathematical expressions as inline code unless the user requests another
  format or LaTeX.
