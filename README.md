# CertiFlow

CertiFlow is an asset certification application developed for Delivery 2 of the
project. It manages inspections, versioned inspection schemas, findings,
corrective actions, and certificates governed by jurisdiction-specific policies.

The backend uses Java 25 and Spring Boot 4. The frontend uses React and TypeScript,
with a Spanish-language interface. Data is stored in an embedded H2 database.

## Features

- **Inspection management:** asset and inspector assignment, criterion responses,
  evidence, notes, closure, and rectification of closed inspections.
- **Partial certification (F1):** independently valid certificates for asset
  subsystems, with global certification derived through an explicit policy.
- **Schema scheduling (F2):** published versions with future effective dates and
  historical version lookup. Inspections retain the version selected at startup.
- **Jurisdiction policies (F3):** configurable blocking severities, validity
  periods, and conditional certification, with the applied policy recorded.
- **Corrective actions:** planning, execution, verification, and expiration,
  linked to findings and certificate lifecycle changes.
- **Audit and reporting:** actor-attributed audit records, inspection acts,
  findings summaries, and certificate reports.
- **Background processing:** expiration sweeps, transactional event delivery,
  retries, and corrective-action expiration notifications.

The main workflow connects asset registration and schema publication to
inspection, corrective action, eligibility assessment, and certification. The
interface also provides audit views and on-demand maintenance processes.

## Project structure

| Module | Contents |
|---|---|
| `core` (`certification-domain`) | Domain model, application use cases, and core adapters. |
| `infrastructure` | JDBC persistence, Flyway migrations, event outbox, and notifications. |
| `app` | Spring Boot composition, REST controllers, scheduled jobs, and the frontend in `app/frontend`. |

The project follows a hexagonal architecture. Architecture tests check dependency
boundaries across the modules and within the core.

## Build and runtime

The build requires JDK 25 and Maven 3.9 or later. Maven provisions Node for the
frontend; the initial build downloads the required tools and dependencies.

```sh
mvn verify
```

This command builds all three modules, runs backend unit and integration tests
and frontend tests, and packages an executable JAR containing the frontend.
Repository and API integration tests use a real H2 database. Test and coverage
reports are generated under each module's `target/surefire-reports`,
`target/failsafe-reports`, and `target/site/jacoco` directories.

The packaged application starts with:

```sh
java -jar app/target/certiflow-app-1.0.0-SNAPSHOT-exec.jar
```

The default interface address is <http://localhost:8080/> and the REST API is
available at <http://localhost:8080/api>. The database is created at
`./data/certiflow`; local database files are excluded from version control.
Spring configuration supports overrides such as `SERVER_PORT` and
`SPRING_DATASOURCE_URL`.

Corrective-action expiration notifications use logging by default. The
`certiflow.notifications.webhook-url` property configures a JSON POST webhook.

## Continuous integration

The [CI workflow](.github/workflows/ci.yml) runs `mvn verify` with JDK 25 on pull
requests and pushes to `main`, and uploads test and coverage reports.

## Documentation

- [Delivery 1 requirements](docs/entrega_1.md) and
  [Delivery 2 requirements](docs/entrega_2.md): functional and delivery scope.
- [Design record](DESIGN.md): design decisions, classes added or modified for
  F1-F3, refactorings, and deliberate technical debt.
- [REST API](docs/API.md): endpoints, actor identification, and error contracts.
- [Architecture](docs/arquitectura-hexagonal.md): hexagonal architecture overview.
- [Frontend README](app/frontend/README.md): frontend structure and development.
- [Project instructions](AGENTS.md): contributor and agent rules, validation,
  and review workflow.
