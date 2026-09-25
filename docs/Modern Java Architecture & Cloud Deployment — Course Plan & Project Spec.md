# Modern Java Architecture & Cloud Deployment — Course Plan & Project Spec

Sep 23, 2026 · @Mark Varadi

## How to use this document

This is the working spec for a 30-hour instructor-led course and for the reference project that carries it. It has two audiences:

- **Me, teaching.** The session plans, timings, and setup instructions.
- **A Claude Code session, building.** The project spec, architecture, branch strategy, and build order.

**What the Claude Code session should produce:** one Gradle multi-project repository containing the full reference system, built in the same order it is taught, with a git tag at every session boundary and a solution branch for every lab. The system itself is the MVP; the branches are a by-product of building it linearly.

**Start there:** *MVP build order*, near the end of this document, is the step-by-step plan for that session. Everything before it is context that plan depends on.

**Working rule while building:** keep a running note of everything that breaks or takes longer than expected. Those notes become theory slides, and anything that costs more than an hour of fighting becomes something students get pre-configured rather than a lab task.

## Course facts

|  |  |
| --- | --- |
| Title | Modern Java Architecture & Cloud Deployment |
| Subtitle | From Developer to Architect: Designing and Deploying Scalable Systems |
| Duration | 30 hours, 10 sessions of 3 hours |
| Schedule | Twice a week over 5 weeks |
| Audience | Developers with some Java and Spring Boot experience, not advanced |
| Delivery | Instructor-led, with guided labs |
| Scope | Backend only: Java, architecture, cloud. The AI/React half of the source curriculum is out of scope. |
| Cost constraint | Everything students use must be free. No paid tools, no paid tiers. |

### Session template

40 min theory · 40 min live coding · 15 min break · 70 min lab · 15 min wrap-up. Sessions 6 and 10 adjust this; see their entries.

### Curriculum mapping

| Source module | Sessions |
| --- | --- |
| 1 — Establishing a foundation (Java, Spring, Spring Data, AI assistants) | 1, 2, 3 |
| 2 — Deploying an application (GCP, Cloud Run, GKE) | 8, 9 |
| 3 — Architectural patterns (system, application, component level) | 4, 5, 6 |
| 4 — Building a distributed system (REST, RabbitMQ, config, discovery, observability, CI/CD, performance) | 7, 9, 10 |

### Session list

| # | Session | Runs on |
| --- | --- | --- |
| 1 | Java 8 → 25 | Laptop |
| 2 | Spring Boot core and REST | Laptop |
| 3 | Persistence: JPA and MongoDB | Compose |
| 4 | System patterns and the modulith | Compose |
| 5 | Hexagonal architecture | Compose |
| 6 | Domain-Driven Design | Compose |
| 7 | Distributed communication and observability | Compose |
| 8 | Containers, GCP, and Cloud Run | Cloud Run |
| 9 | Kubernetes and GKE | GKE |
| 10 | CI/CD and final deployment | GKE |

## Tooling and version baseline

### Core stack

|  | Choice | Notes |
| --- | --- | --- |
| Java | 25 (Temurin) | LTS. Set through a Gradle toolchain, so the local JDK matters less. |
| Spring Boot | 4.x | Minimum Java 17, supported up to Java 26. Brings Spring Framework 7, Jakarta EE 11, Jackson 3, JUnit 6, and a modularised codebase. |
| Build | Gradle 9.1+, Kotlin DSL | Java 25 requires Gradle 9.1 or later. Wrapper committed; students never install Gradle. |
| IDE | IntelliJ IDEA (free edition) or VS Code with the Java and Spring extension packs | The IDE must support Java 25 and Gradle 9.1. An old IDE is the most likely Session 1 blocker. |
| Containers | Docker Desktop, Podman Desktop, or Rancher Desktop | Docker Desktop is free for personal use but may need a licence on a large employer's laptop. |
| Local Kubernetes | kind or k3d | Only needed if a student cannot use GKE. |

**Boot 4 caveat for the build:** artifact names changed with the modularisation, and Jackson 3 / JUnit 6 break older snippets. Generate the starter from start.spring.io rather than adapting an existing project.

### Gradle specifics to teach

- **Toolchains** — introduced in Session 1 as the reason the build is reproducible.
- **Wrapper** — `./gradlew` from day one; it is also what makes CI trivial.
- **Version catalog** (`libs.versions.toml`) — five minutes in Session 2.
- **Multi-project builds** — Session 4, when the modulith gains modules, and Session 7, when services split out.
- **Custom `check` wiring** — Session 4/5, so Spring Modulith verification and ArchUnit rules fail the build.

### Free services

| Need | Service | Free-tier limits that matter |
| --- | --- | --- |
| Cloud platform | GCP free trial | $300 credit, 90 days, no charge unless manually upgraded. Card needed for identity verification; only for people who have never been a paying GCP/Maps/Firebase customer. |
| Relational DB | Cloud SQL (on trial credits) | Stop the instance between sessions. Local Postgres in Compose until Session 8. |
| Document DB | MongoDB Atlas free cluster | 512 MB, MongoDB 8.0, limited regions, no peering or private endpoints. |
| Messaging | CloudAMQP Little Lemur | 1M messages/month, 20 connections, 100 queues, 10,000 queued messages, 28-day idle queue limit. Local RabbitMQ in Compose until Session 10. |
| CI/CD | GitHub Actions | Free for public repos — keep course repos public. |
| Registry | Artifact Registry (trial) or GHCR |  |
| Observability (local) | Grafana, Tempo or Zipkin, Prometheus | Self-hosted in Compose. |

### AI assistants

Claude Code is **not** usable: it needs a paid plan. Gemini Code Assist's free individual tier stopped serving requests in June 2026. What students use:

- **GitHub Copilot Free** — 2,000 completions and 50 chat requests per month. The course runs one month, so chat is the scarce resource: roughly 5 requests per session. Each lab names one or two specific chat tasks; everything else uses completions.
- **Free browser chat** (Claude, ChatGPT) — no quota impact, used for refactoring, review, and test generation.

Tell students about the 50-request budget in Session 1 so they do not spend it in week one. Verified university students get Copilot free with unlimited completions through the Student Developer Pack.

## The learning project

**Domain: event ticketing.** One system, built across all 10 sessions, starting as a plain Spring Boot app and ending as three deployed services with a pipeline.

### Why this domain

- It has a **real invariant worth protecting**: no overbooking. That gives the DDD session (6) something to enforce, and ties back to optimistic locking in Session 3.
- It has **natural bounded contexts** (catalog, booking, payment, notification) that justify the modulith in Session 4 and the split in Session 7 without feeling arbitrary.
- It has an **obvious document-shaped feature** (reviews) so MongoDB is not bolted on.
- Everyone understands it, so no session time goes to explaining the business.

### Scope boundaries

Deliberately **not** in the project: authentication and authorisation, payments beyond a fake gateway, a frontend, search, seat maps, admin tooling, or anything with real money in it. Each of those would be a session of its own.

The UI is curl, the IntelliJ HTTP client, or Bruno. There is no frontend at any point.

### What the finished system must demonstrate

1. Modern Java (records, sealed types, pattern matching) in the domain model.
2. A clean REST API with validation and `ProblemDetail` error handling.
3. JPA used correctly: no N+1, transactions at the right boundary, optimistic locking.
4. MongoDB for reviews, including one aggregation.
5. Verified module boundaries (Spring Modulith `verify()` in the build).
6. Hexagonal structure in the booking module, with a domain core free of Spring and JPA.
7. An aggregate that makes overbooking impossible, with fast unit tests and no Spring context.
8. REST between services with timeout, retry, and circuit breaker; RabbitMQ for events, with an idempotent consumer and a dead-letter queue.
9. Distributed tracing across all three services.
10. Container images built with Buildpacks, deployed to Cloud Run and then GKE.
11. A GitHub Actions pipeline that builds, tests, enforces architecture rules, and deploys.

### Size targets

Small enough to read in an evening. Rough ceiling for the finished system: around 40–50 production classes across the three services. If a lab needs more than about 60 lines of student-written code, it is too big — move the rest into the starter.

## Target architecture

### Final shape: three deployables

| Service | Contains | Talks to |
| --- | --- | --- |
| `core-app` | catalog and booking modules | Postgres, MongoDB, payment (REST), RabbitMQ (publish) |
| `payment-service` | fake payment gateway | nothing |
| `notification-service` | consumes booking events | RabbitMQ (consume) |

Three, not four, on purpose: four services add operational overhead without teaching anything the third does not.

### Modules inside `core-app`

- **catalog** — events, venues, seats. Stays **layered**, deliberately. It is CRUD, and hexagonal would be overhead. Use this contrast in Session 5.
- **booking** — bookings and inventory. **Hexagonal**, with the DDD aggregate inside.
- **shared** — value objects (`Money`, `SeatNumber`, ids) used by both.

Boundaries enforced by Spring Modulith `verify()`, wired into `check`.

### Booking module, hexagonal layout

```
booking/
  domain/           no Spring, no JPA annotations
    EventInventory        aggregate root — owns seat availability
    Booking               entity
    BookingStatus         sealed interface: Pending | Confirmed | Cancelled
    Money, SeatNumber     value objects
  application/
    BookingUseCase        inbound port
    BookingService        implements the use case
    BookingRepository     outbound port
    PaymentGateway        outbound port
  adapter/
    web/     BookingController
    persistence/  JpaBookingRepository + entities + mapping
    payment/ FakePaymentGateway (to session 6) → RestPaymentGateway (session 7)
```

The swap from fake to REST gateway in Session 7 is the payoff of Session 5 — only the adapter changes.

### Aggregate rules

- `EventInventory` is the consistency boundary for seat availability. Overbooking is rejected inside it, not in a service or a database constraint.
- `Booking` references `EventInventory` by ID only.
- Optimistic locking with `@Version` on the inventory row, covering concurrent bookings.
- Domain events (`BookingConfirmed`) published through Spring Modulith, then out to RabbitMQ.

### Messaging topology

- Topic exchange `booking.events`, routing key `booking.confirmed`.
- Queue `notification.booking-confirmed` bound to it.
- Dead-letter exchange and queue for poison messages.
- Consumer is idempotent: it tracks processed booking IDs, because delivery is at-least-once.

### Persistence split

| Data | Store | Why |
| --- | --- | --- |
| Events, venues, seats, bookings, inventory | Postgres / JPA | Relational, transactional, invariant-bearing |
| Reviews | MongoDB | Flexible structure, read-heavy, no joins, natural aggregation |

No Flyway. Schema comes from a provided `schema.sql` with `ddl-auto: validate`. Mention Flyway and Liquibase in two minutes of theory; do not teach migrations.

### Observability

Micrometer Tracing with Grafana and Tempo (or Zipkin) in Compose from Session 7. One trace must visibly cross `core-app` → `payment-service` → RabbitMQ → `notification-service`.

## Sessions 1–5

### Session 1 — Java 8 → 25

**Goal:** a shared baseline in modern Java, the project introduced, expectations set for AI assistants.

Allow 15 min at the start for course intro and environment check; theory then runs 45 min.

**Theory — organised by problem solved, not by version:**

- *Data carriers:* `var` (10), records (16), sealed interfaces (17).
- *Pattern matching:* `instanceof` patterns (16), switch expressions (14), pattern matching for `switch` and record patterns (21).
- *Everyday APIs:* collection factories (9), `String.strip/isBlank/repeat` (11), text blocks (15), sequenced collections (21), stream gatherers (24).
- *Concurrency:* `CompletableFuture` (8) → virtual threads (21), covered properly: blocking I/O, why pooling them is pointless, pinning. Scoped values (finalised in 25) as the `ThreadLocal` replacement. Structured concurrency mentioned only — still preview in 25.
- *Java 25 conveniences:* compact source files with instance `main`, module import declarations, flexible constructor bodies.
- *Context:* module system (9), Java EE removal (11), six-month cadence with LTS at 8, 11, 17, 21, 25.

Hand out a one-page feature-to-version cheat sheet. Resist adding examples — depth goes to records + sealed types + pattern matching, and virtual threads.

**Live coding (35 min):** model `Event`, `Venue`, `BookingStatus` with records and a sealed interface; refunds via pattern-matching `switch`. Introduce Copilot: completions, inline chat, and one plausible-but-wrong suggestion. Close with a 5-minute build-file walkthrough (wrapper, toolchain, plugins, dependencies).

**Core lab (70 min):** refactor the provided `BookingReportService` (Java 8 style: mutable beans, null checks, nested loops) using records, sealed types, streams. Provided tests must keep passing.

*Copilot task (1 chat request):* "explain what this method does and suggest a modern Java equivalent", then evaluate the answer.

**Stretch:** edge-case tests; parallel price lookup from three slow fake providers using virtual threads.

---

### Session 2 — Spring Boot core and REST

**Goal:** understand what Boot does at startup, and build a clean API.

**Theory:** constructor injection; auto-configuration and conditions, read live from the `--debug` report; `@ConfigurationProperties` with records; profiles (`local`, `cloud`); REST design and status codes (201 + Location, 404, 409); DTOs vs entities; Bean Validation; `ProblemDetail` and `@RestControllerAdvice`.

**Live coding:** booking-rules configuration property, profile switching, global error handler, against the in-memory starter. Five minutes on the version catalog.

**Core lab:** Booking endpoints — create (`POST /events/{id}/bookings`), get, cancel — still in-memory, with validation and correct error responses.

*Copilot task:* let completions write the DTOs, then check every validation annotation by hand.

**Stretch:** `@WebMvcTest` tests; pagination on the events list.

**CI:** first pipeline — see the CI/CD thread section.

---

### Session 3 — Persistence: JPA and MongoDB

**Goal:** JPA used correctly, plus knowing when a document store fits.

This is the densest foundation session. JPA gets roughly two-thirds, MongoDB one-third.

**Theory — JPA (30 min):** entity lifecycle and the persistence context; dirty checking; relationships and fetch types (`@ManyToOne` is eager by default — make it lazy); the N+1 problem and three fixes (fetch join, `@EntityGraph`, DTO projections); `@Transactional` placement, `readOnly`, self-invocation, rollback rules; optimistic locking with `@Version`; connection pool sizing in passing.

**Theory — MongoDB (15 min):** embed vs reference, modelling around query patterns, consistency differences, Spring Data repositories and derived queries, aggregations.

**Live coding:** swap the in-memory repository for JPA + Postgres in Compose; enable SQL logging; trigger an N+1 and fix it with `@EntityGraph`; demonstrate a lost update, then fix it with `@Version`.

**Core lab:** persist bookings; find and fix a planted N+1; add `Review` documents with post and list endpoints.

*Copilot task:* "generate an aggregation pipeline for average rating per event", then verify it against real data — this is exactly where AI output looks right and isn't.

**Stretch:** Testcontainers integration tests; the aggregation query if not reached in the core lab.

**Homework:** create an Atlas account and free cluster; activate the GCP trial.

---

### Session 4 — System patterns and the modulith

**Goal:** think in trade-offs; make boundaries explicit before considering distribution.

**Theory:** monolith vs modulith vs microservices; the real costs of distribution (network failure, consistency, operations, debugging); Conway's law; good reasons to split (independent scaling, team autonomy, release cadence) and the default of starting modular; Spring Modulith — packages as modules, `internal` subpackages, `verify()`, application events, generated diagrams.

**Live coding:** the starter branch already has catalog / booking / payment / notification packages with planted boundary violations. Run `verify()`, read the report, fix one by exposing a proper API, replace booking's direct call to notification with a `BookingConfirmed` event, generate the module diagram.

**Core lab:** fix the remaining violations until `verify()` passes; convert one more cross-module call into an event.

**Stretch:** write an ADR for "modulith now, split later"; add an `@ApplicationModuleTest`.

**CI:** wire `verify()` into `check` so violations fail the build.

---

### Session 5 — Hexagonal architecture

**Goal:** keep business logic independent of frameworks, and know when it is worth it.

**Theory:** layered architecture and how it fails (logic tied to JPA and HTTP, anemic services); ports and adapters; the dependency rule; onion as the same rule drawn as rings; the cost — more interfaces, more mapping. Catalog stays layered on purpose; hexagonal is applied only where the logic is worth protecting.

**Live coding:** refactor booking — extract `BookingUseCase`, add `BookingRepository` and `PaymentGateway` ports with a JPA adapter and a fake payment adapter, strip annotations from domain classes, map between entities and domain objects.

**Core lab:** finish the refactor so the controller calls the use case through its port; test the use case with in-memory adapters and no Spring context. The speed of those tests is the payoff — make it visible.

**Stretch:** ArchUnit rule that the domain must not depend on Spring or JPA.

**CI:** add the ArchUnit rules to `check`.

## Sessions 6–10

### Session 6 — Domain-Driven Design

**Goal:** model the domain so the code enforces the business rules.

**Adjusted template:** 35 theory · 20 event storming · 30 live coding · 15 break · 65 lab · 15 wrap-up.

**Theory:** strategic DDD kept short — ubiquitous language, bounded contexts (point out the Session 4 modules already are ones), one slide on context maps. Tactical DDD — entities vs value objects; the aggregate as consistency boundary with invariants enforced inside; one repository per aggregate; referencing other aggregates by ID; domain events.

**Event storming (20 min):** map the booking flow in Excalidraw — events, commands, aggregates, policies.

**Live coding:** start from the design question — where does "no overbooking" live? Lead to `EventInventory` owning seat availability. Add `Money`, `SeatNumber`, `BookingId`.

**Core lab:** implement the aggregate with its invariants plus unit tests, no Spring: overbooking rejected; cancelling releases seats; a booking cannot be cancelled twice. Tie back to `@Version` from Session 3.

**Stretch:** publish domain events from the aggregate through Modulith.

**Homework:** create a CloudAMQP account and Little Lemur instance (5 min).

---

### Session 7 — Distributed communication and observability

**Goal:** services that survive each other's failures, and the ability to see inside them. All local, in Compose.

**Theory — communication (30 min):** synchronous vs asynchronous; how remote calls fail; always set timeouts; retry only idempotent operations, with backoff; circuit breaker states; `RestClient` and HTTP interfaces with Resilience4j. RabbitMQ — exchanges, queues, bindings, routing keys, acknowledgements, at-least-once delivery and why consumers must be idempotent, dead-letter queues. The outbox pattern mentioned only.

**Theory — observability (15 min):** logs, metrics, traces; correlation IDs; what to check first when something breaks.

**Live coding:** payment and notification service skeletons are provided, along with a Compose file carrying RabbitMQ, Grafana, and Tempo. Replace the fake payment adapter with a REST adapter — only the adapter changes. Add a timeout, kill payment to show the failure, add a circuit breaker with a fallback leaving the booking as pending payment. Publish `BookingConfirmed` to the topic exchange. Show one trace crossing all three services.

**Core lab:** write the `@RabbitListener` in notification-service and make it idempotent by tracking processed booking IDs; verify flow in the RabbitMQ management UI; stop payment-service and watch the breaker open.

**Stretch:** dead-letter queue for a poison message; find a slow call in the trace view.

---

### Session 8 — Containers, GCP, and Cloud Run

**Goal:** first deployment. This is the payoff session, with room to do containers properly.

**Theory — containers (20 min):** images and layers; registries; building Java images with `bootBuildImage` (Buildpacks) and what a Dockerfile would look like; the JVM inside a container — memory limits and CPU shares.

**Theory — GCP (25 min):** projects and billing (the trial); IAM — principals, roles, service accounts, least privilege; regions and basic VPC concepts; Artifact Registry. The Cloud Run model — request-based scaling, scale to zero, cold starts, concurrency. Connecting to managed services: Cloud SQL via the Java connector; Atlas over the public internet, and why the wide-open IP allowlist is a course-only compromise (free clusters support no peering or private endpoints).

**Live coding:** build, push to Artifact Registry, deploy with the `cloud` profile and env vars, read Cloud Logging.

**Core lab:** every student deploys `core-app` to Cloud Run, wired to Cloud SQL and Atlas, and calls their public URL.

**Stretch:** move the DB password to Secret Manager; measure cold starts with min instances 0 vs 1.

**Timing note:** Cloud SQL instance creation takes \~10 minutes — have students start it during the theory block. Remind everyone to stop it after class.

---

### Session 9 — Kubernetes and GKE

**Goal:** deploy, debug, and scale on Kubernetes.

**Theory:** why orchestration exists; Pods, Deployments, Services (Service DNS *is* service discovery), ConfigMaps and Secrets (this is centralised configuration — say so, and note Spring Cloud Config and Eureka as the alternatives you do not need here); liveness vs readiness probes with Actuator health groups; requests and limits; rolling updates and rollback; HPA and how autoscaling actually behaves; Cloud Logging and Cloud Monitoring; Autopilot vs Standard.

Include here the performance point that scaling moves the bottleneck downstream — CloudAMQP's 20-connection limit is the concrete example.

**Live coding:** deploy the three services to GKE Autopilot with provided manifests, wired to Cloud SQL, Atlas, and CloudAMQP via a Secret. Rolling update; a failing probe holding back traffic.

**Core lab:** get their own three services running on GKE, then break-and-fix — wrong image tag (`ImagePullBackOff`), missing secret (`CreateContainerConfigError`), readiness probe on the wrong path. Timebox break-and-fix to 20 minutes.

**Stretch:** add an HPA and watch it scale under load.

**Note:** students work from prepared manifests and do not write them from scratch. That is a deliberate cut — Kubernetes YAML is the most Googleable skill in the course.

---

### Session 10 — CI/CD and final deployment

**Goal:** push a commit, watch it reach a running cluster.

**Adjusted template:** 40 theory · 30 live coding · 15 break · 80 lab · 15 wrap-up. No in-session demos — see the homework section.

**Theory:** pipeline design and stages; quality gates (tests, Modulith verification, ArchUnit) as the build's opinion about the architecture; image tagging by commit SHA, never `latest`; Workload Identity Federation instead of long-lived service account keys, and why keys in CI are the classic leak; GitHub Environments, secrets, and manual approval gates; promotion from staging to production; rollback with `kubectl rollout undo`; overview only — blue/green, canary, GitOps with Argo CD or Flux.

**Live coding:** extend the pipeline to build the image, push to Artifact Registry, and deploy to GKE. Then break something and roll back live.

**Core lab:** each student gets their own pipeline deploying to GKE. Students whose cluster is broken work against the known-good branch and still learn the pipeline.

**Stretch:** add a manual approval gate; a capped k6 load test against the booking endpoint (cap it — a careless run eats the CloudAMQP message quota).

**Wrap-up:** final project requirements, deadline, and cleanup instructions (delete the cluster, stop Cloud SQL).

## CI/CD thread

CI/CD is taught as a habit that grows with the system, not as one lecture. Small blocks early, one full session at the end. The early blocks are what make Session 10 workable — by then students have had a pipeline running for four weeks.

| Session | Block | What gets added |
| --- | --- | --- |
| 2 | 15 min, wrap-up | Push to a **public** repo. Workflow runs `./gradlew build` on push and PR. Anatomy: triggers, jobs, steps, runners. Branch protection so a red build blocks merging. *Homework: green badge in the README.* |
| 3 | 20 min, in theory | `gradle/actions/setup-gradle` for caching; build cache and configuration cache; publishing test reports; running Testcontainers on the runner (Docker is available on the Ubuntu runner). Why fast pipelines matter. |
| 4 | 10 min, in lab | `ApplicationModules.verify()` wired into `check` — a boundary violation now fails the build. |
| 5 | 5 min, in lab | ArchUnit dependency rules added to `check`. The architecture stops being a diagram nobody enforces. |
| 8 | mentioned | `bootBuildImage` is introduced as a local command; pipeline integration waits for Session 10. |
| 10 | full session | Image build and push to Artifact Registry, SHA tagging, Workload Identity Federation, GitHub Environments and secrets, deploy to GKE, approval gate, rollback. |

**Protect the small blocks.** They are the first thing that gets cut when a session runs long, and cutting them is what makes Session 10 a cold start.

## Repo, branches, and planted bugs

### Repo layout

One public GitHub repo, Gradle multi-project. It starts single-module and grows.

```
ticketing/
  settings.gradle.kts
  gradle/libs.versions.toml
  build-logic/              convention plugins (from session 4)
  core-app/
  payment-service/          from session 7
  notification-service/     from session 7
  compose/                  docker-compose files per session
  k8s/                      manifests (session 9)
  .github/workflows/
  docs/                     setup guide, cheat sheets, ADRs
```

### Branch and tag strategy

Build the system linearly, tagging as you pass each boundary. Each session then has:

| Ref | Purpose |
| --- | --- |
| `session-NN-start` | What students check out at the beginning. Includes the starter code, the planted bugs, and any boilerplate they should not type. |
| `session-NN-solution` | The finished state, including lab and stretch tasks. The fallback. |

Naming matters: `solution` reads as a fallback, `start` reads as the default. That small friction discourages students from skipping ahead instead of finishing the lab.

`session-NN-solution` and `session-(NN+1)-start` are usually the same commit, but keep both refs — the next session's start may add scaffolding the previous solution did not need.

### Planted bugs

Build the system **correctly first**, then introduce these as separate, clearly-labelled commits on the relevant `-start` branch. Bugs written after the fact are more realistic than bugs remembered.

| Session | Planted | Symptom students see |
| --- | --- | --- |
| 1 | `BookingReportService` in Java 8 style | Works, but painful to read; tests pass and must keep passing |
| 3 | N+1 on a list endpoint | Dozens of SELECTs in the SQL log |
| 3 | Missing `@Version` | Lost update under concurrent booking |
| 4 | 3–4 cross-module boundary violations | `verify()` fails with a readable report |
| 5 | Domain classes annotated with JPA | ArchUnit rule fails |
| 7 | No timeout on the payment call | Request hangs when payment is stopped |
| 9 | Wrong image tag | `ImagePullBackOff` |
| 9 | Missing secret reference | `CreateContainerConfigError` |
| 9 | Readiness probe on the wrong path | Pod never becomes ready, traffic never arrives |

Keep the fixes on the solution branches so you can apply one fast when a student is stuck.

### What lives in `docs/`

- Setup guide, grouped by week (see the cloud section).
- Java feature-to-version cheat sheet (Session 1).
- `kubectl` and `gcloud` command cards (Sessions 8–9).
- ADR template plus the one worked example.
- The final project definition of done.

## MVP build order

This is the plan for the Claude Code session. Build linearly, in teaching order, tagging as you go. Do not build ahead — the point is that each step's starting state is a real commit.

**Before anything else:** generate the project from start.spring.io (Gradle Kotlin DSL, Java 25, Spring Boot 4.x) rather than hand-writing the build. Confirm `./gradlew build` works on Java 25 with Gradle 9.1+ before writing a line of application code.

### Step 1 — Skeleton and Session 1 material

- Gradle wrapper 9.1+, toolchain pinned to 25, version catalog.
- Domain sketch: `Event`, `Venue`, `Seat` as records; `BookingStatus` as a sealed interface.
- Write `BookingReportService` **correctly**, then write the Java 8-style version and its tests for the lab.
- **Checkpoint:** `./gradlew build` green. Tag `session-01-start` (legacy version) and `session-01-solution`.

### Step 2 — REST API, in memory

- Event CRUD (provided to students), Booking endpoints (their lab), DTOs, validation, `ProblemDetail` handler, `@ConfigurationProperties` for booking rules, `local`/`cloud` profiles.
- First GitHub Actions workflow.
- **Checkpoint:** create, fetch, cancel a booking via HTTP; CI green. Tag both refs.

### Step 3 — Persistence

- Postgres and MongoDB in Compose; `schema.sql`; `ddl-auto: validate`.
- JPA entities and repositories; mapping to and from DTOs.
- `Review` documents plus the aggregation.
- Plant the N+1 and the missing `@Version`; write the tests that expose both.
- **Checkpoint:** SQL log visibly shows the N+1 on the start branch and does not on the solution. Tag both.

### Step 4 — Modulith

- Split into `catalog`, `booking`, `shared`; add `build-logic` convention plugins.
- Spring Modulith; `verify()` wired into `check`; generate diagrams.
- Plant 3–4 boundary violations on the start branch.
- **Checkpoint:** `check` fails on start, passes on solution. Tag both.

### Step 5 — Hexagonal

- Refactor booking into `domain` / `application` / `adapter`; `BookingUseCase`, `BookingRepository`, `PaymentGateway` ports; JPA and fake-payment adapters.
- ArchUnit rules in `check`; leave annotated domain classes on the start branch.
- **Checkpoint:** use-case tests run with in-memory adapters, no Spring context, in under a second. Tag both.

### Step 6 — DDD

- `EventInventory` aggregate owning availability; `Money`, `SeatNumber`, `BookingId`; domain events.
- Tests: overbooking rejected, cancel releases seats, no double cancel.
- **Checkpoint:** a concurrent-booking test proves overbooking is impossible. Tag both.

### Step 7 — Distribution and observability

- Extract `payment-service` and `notification-service` as Gradle subprojects.
- `RestPaymentGateway` with timeout, retry, circuit breaker (Resilience4j).
- RabbitMQ topology: topic exchange, queue, DLQ. Idempotent consumer.
- Compose adds RabbitMQ, Grafana, Tempo; Micrometer Tracing in all three services.
- **Checkpoint:** one trace visibly spans all three services; stopping payment opens the breaker; a poison message lands in the DLQ. Tag both.

### Step 8 — Cloud Run

- `bootBuildImage`; deploy `core-app` to Cloud Run; Cloud SQL via the Java connector; Atlas over the internet; Secret Manager for the password.
- **Checkpoint:** public URL serves the booking flow. Record every manual step — these become the lab script. Tag both.

### Step 9 — GKE

- Manifests for all three services: Deployments, Services, ConfigMaps, Secrets, probes, requests and limits, HPA.
- CloudAMQP wired in via Secret.
- Produce the three broken manifest variants for break-and-fix.
- **Checkpoint:** full flow works on GKE Autopilot; rolling update and rollback both demonstrated. Tag both.

### Step 10 — Pipeline

- Workload Identity Federation setup, documented step by step (this is the most likely thing to eat an hour — if it does, pre-configure it for students).
- Pipeline: build, test, `check`, image build, push, deploy, approval gate.
- **Checkpoint:** a commit to main reaches GKE unattended; `kubectl rollout undo` recovers. Tag `session-10-solution` as the reference implementation.

### Build-time rules

1. **Keep a friction log.** Anything that surprises you becomes a slide; anything costing over an hour becomes pre-configured rather than a lab task.
2. **Time each lab task** as you write it. If your own clean implementation takes more than 25 minutes, the 70-minute lab version is too big — move code into the starter.
3. **Count student-written lines** per lab. Over \~60 means cut.
4. **Everything runs locally through Step 7.** No cloud dependency before Step 8, so a student without a GCP account can follow seven sessions fully.
5. **Write the README as you go**, not at the end. It doubles as the students' reference.

## Cloud and free-tier setup

### Student setup timeline

Give one setup document at the start, grouped by week so nobody does everything up front.

| When | Task |
| --- | --- |
| Before session 1 | JDK 25 (Temurin), IDE supporting Java 25 and Gradle 9.1, Docker/Podman/Rancher, Git, GitHub account with Copilot Free enabled |
| Before session 3 | Docker working — Compose starts Postgres and Mongo |
| After session 3 | Atlas account and free cluster |
| Before session 8 | GCP trial activated, `gcloud` CLI installed |
| After session 6 | CloudAMQP account and Little Lemur instance |
| Fallback only | kind or k3d, for anyone without a GCP account |

### GCP trial notes

- 90 days and $300 credit, activated in week 2 covers the course plus roughly two months for the final project.
- No charge unless a student manually upgrades to a paid account. If credits run out, workloads stop — they do not bill.
- A card is needed for identity verification. Eligibility requires never having been a paying GCP, Google Maps Platform, or Firebase customer.
- **Ask the organiser** whether they can provide a shared project or education credits. That removes the card requirement entirely and lets you control cleanup centrally.

### Cost hygiene to teach

- Set a budget alert on day one.
- Stop the Cloud SQL instance after every session.
- Delete the GKE cluster after every session.
- Cap load tests — an uncapped k6 run can consume the CloudAMQP monthly message quota in minutes.

### Security compromises made for the course

Name these out loud when you make them; they are teaching moments, not oversights.

| Compromise | Why it is acceptable here | What production would do |
| --- | --- | --- |
| Atlas IP allowlist open to `0.0.0.0/0` | Free clusters support no peering or private endpoints, and Cloud Run has no static egress IP without Cloud NAT | Private endpoint or peering, or Cloud NAT with a fixed IP |
| Postgres in a pod with no persistent volume (fallback track) | Ephemeral data is fine for a lab | Managed database or a StatefulSet with a PV |
| Shared RabbitMQ broker | Free tier | Dedicated instance |
| Public course repos | Free Actions minutes | Private repo with paid minutes |

### Fallback track

For students who cannot or will not create a GCP account: everything through Session 7 runs locally already. For Sessions 9 and 10, kind or k3d runs the same manifests, and the pipeline deploys to GHCR plus a local cluster. They lose the managed-service experience, not the concepts.

## Homework and the final project

### Session homework

With two sessions a week, there are only 2–3 days between them. Cap homework at **one hour**, and keep it mostly optional. Anything heavier moves into lab time.

The homework that is not optional is account setup: Atlas after Session 3, GCP before Session 8, CloudAMQP after Session 6. Those gate the next session.

### Final project

Students finish their own system in their own repo after the course. No in-session demos — that frees 40 minutes of lab time in Session 10 and means they submit finished work rather than whatever state it was in at 21:30.

**Definition of done — state this in writing in Session 1:**

1. The three services run.
2. The booking flow works end to end.
3. Overbooking is rejected, proven by a test.
4. `./gradlew check` passes, including Modulith verification and ArchUnit rules.
5. The CI pipeline is green.
6. The README explains how to run it locally, and includes the generated module diagram.

**Stretch, explicitly not required:** deployed to GKE with the pipeline deploying automatically.

**Deadline:** two weeks after Session 10. Long enough for people with jobs, short enough to keep momentum, and inside the GCP trial window.

### Two-track expectations

Say this in Session 1, not Session 9. Everyone will deploy to Cloud Run in Session 8, because it is essentially one command. Not everyone will have three services on GKE with a green pipeline by the end of Session 10, and that is fine — it is a stretch goal, not a failure. The known-good branch is what makes this honest: a student whose own cluster is broken still does the Session 10 lab against a working system and still learns the pipeline.

### Feedback

One written review per repo after the deadline. Roughly 15 minutes each, so about 4 hours for a group of 15. This is where the real teaching happens for the students who struggled.

Optionally, offer PR review during the two weeks. Some will use it, most will not, and it costs nothing when unused.

### What students walk away with

Be honest about this with the organiser and with the group.

- **Solid and hands-on:** modern Java, Spring Boot internals, JPA done correctly, module boundaries, hexagonal architecture, DDD aggregates, resilient communication with RabbitMQ.
- **Working knowledge, done once with guidance:** containerising and deploying to Cloud Run, deploying to GKE, a CI/CD pipeline, distributed tracing.
- **Seen and understood, not built:** GCP networking, deployment strategies beyond rolling updates, performance tuning, Kubernetes manifests from scratch.

The artifact they keep is a public repo with a clear README, a green Actions badge, and an architecture diagram — which is exactly the portfolio piece the source curriculum promises.

## Decisions, cuts, and risks

### Decisions already made, and why

| Decision | Reason |
| --- | --- |
| Cloud work at the end (Sessions 8–10), not Session 4 | The deployment is the culmination of building something, not a warm-up |
| One project across all 10 sessions | Continuity; each session's work visibly matters to the next |
| Three services, not four | The fourth adds operations without adding a concept |
| Persistence merged into one session | Freed a session for the cloud half, which the curriculum weights heavily |
| Catalog stays layered while booking goes hexagonal | The contrast teaches when *not* to use the pattern |
| No Flyway | Migrations are a topic of their own; `schema.sql` is enough for a course project |
| CI as a thread, CD as a session | CI/CD learns better in repeated doses than in one block |
| Demos moved out of session time | Recovers lab time; students submit finished work |
| Prepared Kubernetes manifests | Writing YAML from scratch is the most Googleable skill in the course |

### Deliberately cut

Structured concurrency beyond a mention, OpenAPI, onion as a separate topic, the outbox pattern beyond a mention, Eureka and Spring Cloud Config (Kubernetes-native equivalents are taught instead), Flyway, performance tuning as its own segment (folded into Sessions 3 and 9), blue/green and canary beyond an overview.

### Coverage honesty

Every item in the source curriculum is touched. Some are at "understand it and have seen it" depth rather than "built it". The under-covered items relative to the curriculum's wording: GCP networking, and Kubernetes at the depth implied by "managing microservices with Kubernetes".

### Risks

| Risk | Mitigation |
| --- | --- |
| Session 10 depends on Session 9 output, with 2–3 days between them | Known-good branch; two-track expectations set in Session 1 |
| A student's IDE is too old for Java 25 / Gradle 9.1 | Setup document checked before Session 1, not during it |
| Spring Boot 4 + Java 25 + Gradle 9.1 is a new combination | Building the reference project in advance is exactly what surfaces this |
| Copilot's 50 monthly chat requests run out in week one | Tell students the budget in Session 1; name specific chat tasks per lab |
| Students lean on solution branches instead of finishing labs | Naming (`solution` vs `start`); homework builds on their own repo |
| Kubernetes debugging expands to fill all available time | Timebox break-and-fix to 20 minutes; have fixes ready |
| Cloud SQL or GKE left running drains credits | Cost hygiene taught in Session 8; cleanup reminder at every session end |

### Open question for the organiser

What were the 30 hours sold as? If participants were promised they would deploy their own microservices to GKE, this plan needs more cloud and less architecture. If they were promised they would think like architects — which the subtitle *From Developer to Architect* suggests — the current balance is right, and the cloud work is the demonstration rather than the point.
