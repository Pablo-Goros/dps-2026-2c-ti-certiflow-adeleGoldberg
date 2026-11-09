# CertiFlow

CertiFlow is an asset inspection and certification application, built in three deliveries: a domain module
(Delivery 1), a complete application with partial certification, scheduled schema versions and jurisdiction policies
(Delivery 2), and an AI inspection assistant with its evaluation harness (Delivery 3). It manages inspections,
versioned inspection schemas, findings, corrective actions, and certificates governed by jurisdiction-specific
policies.

The backend uses Java 25 and Spring Boot 4. The frontend uses React and TypeScript, with a Spanish-language interface.
Data is stored in an embedded H2 database.

## Features

- **Inspection management:** asset and inspector assignment, criterion responses, evidence, notes, observed
  non-conformities, closure, and rectification of closed inspections.
- **Partial certification (F1):** independently valid certificates for asset subsystems, with global certification
  derived through an explicit policy.
- **Schema scheduling (F2):** published versions with future effective dates and historical version lookup.
  Inspections retain the version selected at startup.
- **Jurisdiction policies (F3):** configurable blocking severities, validity periods, and conditional certification,
  with the applied policy recorded.
- **Inspection assistant (Delivery 3):** a language model reads the inspector's notes and evidence and suggests
  findings and severities that the rules do not reflect. It only suggests: the inspector accepts, edits, or discards
  each suggestion, nothing is applied on its own, and what the AI proposed and what the inspector decided is traced
  in the consultation history, the inspection act, and the findings.
- **Evaluation harness (Delivery 3):** a golden set of cases measures correct findings, right severities, and
  invented findings for each model and prompt version.
- **Corrective actions:** planning, execution, verification, and expiration, linked to findings and certificate
  lifecycle changes.
- **Audit and reporting:** actor-attributed audit records, inspection acts, findings summaries, and certificate
  reports.
- **Background processing:** expiration sweeps, transactional event delivery, retries, and corrective-action
  expiration notifications.

The main workflow connects asset registration and schema publication to inspection, corrective action, eligibility
assessment, and certification. The interface also provides audit views and on-demand maintenance processes.

## Project structure

| Module | Contents |
|---|---|
| `core` (`certification-domain`) | Domain model, application use cases, and core adapters. |
| `infrastructure` | JDBC persistence, Flyway migrations, event outbox, notifications, the model adapter and its versioned prompts, and the evaluation harness (tests). |
| `app` | Spring Boot composition, REST controllers, scheduled jobs, and the frontend in `app/frontend`. |

The project follows a hexagonal architecture. Architecture tests check dependency boundaries across the modules and
within the core.

Core ports belong to the domain. Application use cases implement inbound ports and open their own transactions; web
controllers and scheduled jobs depend on those interfaces. JDBC adapters implement outbound ports, and the
application configuration composes the implementations. The assistant follows the same rule: the domain defines the
`InspectionAssistant` port, and the provider adapter lives in `infrastructure`.

## Build and runtime

The build requires JDK 25 and Maven 3.9 or later. Maven provisions Node for the frontend; the initial build downloads
the required tools and dependencies.

```sh
mvn verify
```

This command builds all three modules, runs backend unit and integration tests and frontend tests, and packages an
executable JAR containing the frontend. Repository and API integration tests use a real H2 database; the assistant is
tested against a deterministic stub of its port, never a real model. Test and coverage reports are generated under
each module's `target/surefire-reports`, `target/failsafe-reports`, and `target/site/jacoco` directories.

The packaged application starts with:

```sh
java -jar app/target/certiflow-app-1.0.0-SNAPSHOT-exec.jar
```

The JAR needs a Java 25 runtime; if the `java` on the path is older, call the JDK 25 binary explicitly.

The default interface address is <http://localhost:8080/> and the REST API is available at
<http://localhost:8080/api>. The database is created at `./data/certiflow`; local database files are excluded from
version control. Spring configuration supports overrides such as `SERVER_PORT` and `SPRING_DATASOURCE_URL`.

Corrective-action expiration notifications use logging by default. The `certiflow.notifications.webhook-url`
property configures a JSON POST webhook.

## Inspection assistant

The assistant is off by default, because turning it on sends inspection notes to a model. The model runs wherever the
server points to, not on each user's machine: users only need a browser. Any service that speaks the
OpenAI-compatible chat API works (Ollama, OpenAI, Gemini's compatible endpoint, llama.cpp, vLLM).

| Property | Meaning |
|---|---|
| `certiflow.assistant.enabled` | `true` publishes the assistant's endpoints; otherwise the interface says it is off. |
| `certiflow.assistant.provider` | `openai-compatible`, or `none` (every consultation answers 503). |
| `certiflow.assistant.base-url` | The provider's compatible API, for example `http://localhost:11434/v1` for Ollama. |
| `certiflow.assistant.model` | The model name at that provider. |
| `certiflow.assistant.prompt` | The versioned prompt, for example `suggest-findings/v2`. |
| `certiflow.assistant.timeout-seconds` | Limit for a complete answer (60 by default; local models may need more). |

The API key, when the provider needs one, goes in the `CERTIFLOW_ASSISTANT_API_KEY` environment variable.

With a local model through [Ollama](https://ollama.com):

```sh
ollama pull llama3.1:8b
java -jar app/target/certiflow-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=demo \
  --certiflow.assistant.enabled=true \
  --certiflow.assistant.provider=openai-compatible \
  --certiflow.assistant.base-url=http://localhost:11434/v1 \
  --certiflow.assistant.model=llama3.1:8b \
  --certiflow.assistant.prompt=suggest-findings/v2 \
  --certiflow.assistant.timeout-seconds=300
```

Use a model and prompt version that were measured: the [evaluation results](docs/assistant-eval/README.md) show how
much each combination finds, how often it gets the severity right, and how much it invents. The inspector never
chooses the model; each consultation records which model and prompt answered.

In the interface, the assistant appears on an inspection in progress, after its notes: the assigned inspector adds a
note describing what they saw and asks for suggestions.

## Roles and authentication

The domain models who may do what and enforces it:

| Role | May |
|---|---|
| Assigned inspector | Start the inspection, record answers, evidence, notes and observed non-conformities, consult the assistant and decide its suggestions, close, and rectify. Only the inspector assigned to that inspection. |
| Asset responsible | Plan the corrective action of each finding: the work, who executes it, and the deadline. |
| Executor | Report that the corrective action was carried out. |
| Inspector (verification) | Verify the corrective action; not the same person who executed it. |

Identity, however, is not authenticated. The API reads the acting person from the `X-Actor-Id` header, and the
interface lets anyone choose who they act as in **«Actuando como»** in the sidebar. This stands in for a login so
every role can be shown in a demonstration; it is deliberate technical debt (see `DESIGN.md`, section 14), and the
administration endpoints are unprotected for the same reason. A real deployment needs authentication, with the
identity taken from the session, and system roles for administration.

## Demonstration data

The `demo` profile seeds an empty database with the scenario of the second delivery: one schema ("Inspección de
instalaciones") published twice, the second version effective in the future; three facilities, one per jurisdiction,
inspected against the version frozen at the time; findings of low, medium and high severity; and a certificate per
subsystem with its derived global certificate. The seeder drives the application's own use cases, so every
evaluation, finding and certificate is state the domain computed rather than inserted rows. It runs only on an empty
database and leaves an existing one untouched; delete `./data/certiflow*` to seed again.

```sh
java -jar app/target/certiflow-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=demo
```

The seeded inspections are closed; to try the assistant, assign a new inspection to Ana Pérez, start it acting as
her, and add a note.

## Containers

Running the application in a container requires neither a JDK nor Node on the host. The image builds the backend and
the frontend and runs the demo profile.

```sh
docker compose up
```

The interface is then available at <http://localhost:8080/>. The compose file declares no volume, so each run starts
from an empty database and reseeds it, which keeps a demonstration reproducible; uncomment the volume in
`compose.yaml` to keep the data between runs instead.

To run the assistant with a local model in a container as well:

```sh
CERTIFLOW_ASSISTANT_ENABLED=true docker compose --profile assistant up
```

This starts Ollama and downloads `llama3.2:3b` on the first run. Docker does not use the GPU on macOS, so answers are
slow there; a natively installed Ollama is faster.

## Continuous integration

The [CI workflow](.github/workflows/ci.yml) runs `mvn verify` with JDK 25 on pull requests and pushes to `main`, and
uploads test and coverage reports. A repository ruleset requires the **Build and test** check before merging into
`main`. The CI never calls a model: the assistant runs against a stub, and the evaluation against real models runs
separately (see the evaluation documentation).

## Documentation

- Requirements: [Delivery 1](docs/entrega_1.md), [Delivery 2](docs/entrega_2.md) and
  [Delivery 3](docs/entrega_3.md).
- [Design record](DESIGN.md): design decisions, classes added or modified per feature, refactorings, and deliberate
  technical debt.
- [REST API](docs/API.md): endpoints, actor identification, and error contracts.
- [Assistant evaluation](docs/assistant-eval/README.md): golden set, metrics, how to run the harness, and results.
- [Architecture](docs/arquitectura-hexagonal.md): hexagonal architecture overview.
- [Frontend README](app/frontend/README.md): frontend structure and development.
- [Project instructions](AGENTS.md): contributor and agent rules, validation, and review workflow.
