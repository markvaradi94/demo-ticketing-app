# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-08-end`**.

## Prerequisites

- JDK 25 (Temurin recommended), on your `PATH`. The build does not auto-provision a
  toolchain — install it yourself before running anything below.
- Docker (Desktop, Podman, or Rancher), running. `./gradlew build` starts ephemeral
  Postgres and MongoDB via Testcontainers for `core-app`'s tests; `./gradlew
  :core-app:bootRun` starts Postgres, MongoDB, and (new this session) RabbitMQ via
  `compose/docker-compose.yml` automatically (Spring Boot's Docker Compose support —
  no manual `docker compose up` needed).

## Build & test

This is a **multi-module build**, new this session — `./gradlew build` from the root
builds and tests every subproject (`core-app`, `payment-service`,
`notification-service`, `shared`). To run just one service:

```
./gradlew :core-app:bootRun
./gradlew :payment-service:bootRun
./gradlew :notification-service:bootRun
```

`core-app` still owns the ticketing domain and is the only one with a database or a
Testcontainers-backed test suite; `payment-service` and `notification-service` are
plain, fast-booting Spring Boot apps.

## Where things stand — Session 8: Cloud Run (end)

A different kind of session, continued from `session-08-start`. Sessions 1–7 could
all be fully built, tested, and verified by running `./gradlew build` — every piece
of infrastructure was Docker-on-a-laptop, reproducible for anyone, verifiable by CI.
Session 8 leaves that world: Cloud Run, Cloud SQL, Atlas, CloudAMQP, and Secret
Manager are real external services with real accounts behind them. Everything below
that's genuinely local — `bootBuildImage`, the Secret Manager integration's actual
logic — was built and verified with the same rigor as every prior session. The
actual `gcloud`/Secret Manager/Atlas/CloudAMQP commands are documented precisely but
were not run by whoever last touched this file — they need real credentials this
repo's automation doesn't have.

**Background, unchanged from `session-08-start`:** `spring-cloud-gcp-starter-secretmanager`
doesn't support Spring Boot 4 (a confirmed, open incompatibility, not a missing
feature) — see that branch's README for the full reasoning. `shared`'s scope
expanded to hold cross-service infrastructure, not just message contracts.
`com.google.cloud:libraries-bom`, `google-cloud-secretmanager`, and a real
provisioning checklist (GCP project, one shared instructor-managed Cloud SQL
instance, per-student Atlas M0 and CloudAMQP Little Lemur) were already in place.

### Live coding — bootBuildImage, then Secret Manager for real

**`bootBuildImage`, configured once in `build-logic`, verified against all three
services:** `imageName.set("ticketing/${project.name}:latest")` on the
`BootBuildImage` task — Cloud Native Buildpacks do the rest, no Dockerfile anywhere
in this repo. Hit one genuine, confirmed Spring Boot 4.0+ regression along the way:
an empty Docker Hub auth entry in `~/.docker/config.json` (a normal side effect of
using `credsStore`) makes `bootBuildImage` throw `'username' must not be null`
instead of falling back to the credential helper the way Boot 3.5 correctly did —
already fixed upstream, not yet in 4.1.1. `docker login` resolved it locally
(populates a real credential the helper can use, sidestepping the broken fallback
path entirely). Every image was built and actually run locally —
`payment-service`'s answered real HTTP traffic standalone;
`core-app`'s connected to real Postgres/Mongo/RabbitMQ containers over a shared
Docker network and genuinely persisted an `Event` via `INSERT` statements visible in
its own logs; `notification-service`'s was detected by the buildpack itself as a
"Non-web application" — independent confirmation, from the platform, that its shape
genuinely fits a Cloud Run Worker Pool rather than a normal HTTP service.

**`GcpSecretsEnvironmentPostProcessor`, in `shared`, replacing the broken starter:**
runs only on the `cloud` profile, only if `GOOGLE_CLOUD_PROJECT` is set (the
standard env var Cloud Run itself provides automatically at runtime — nothing to
configure for that part), only if `gcp.secrets.mappings.*` properties exist. Each
mapping's key is a Secret Manager secret name, its value the Spring property key the
fetched secret becomes — e.g.
`gcp.secrets.mappings.cloud-sql-jdbc-url=spring.datasource.url`. Every secret this
app uses is a single, complete, ready-to-use connection string with credentials
already embedded (a full JDBC URL, a full `mongodb+srv://` URI, a full `amqps://`
URI) — the post-processor never needs to know the shape of any one dependency's
credentials, only which property key each secret becomes.

**Registered via `META-INF/spring.factories`, targeting the interface Boot 4.1
actually uses — confirmed against the real jar, not assumed from search results:**
`org.springframework.boot.EnvironmentPostProcessor`, not the older
`org.springframework.boot.env.EnvironmentPostProcessor` (present in the same jar,
still working, but the deprecated one — the two are easy to confuse, since a search
turned up a self-contradicting summary on exactly this point; checking the actual
4.1.1 jar's bytecode settled it). A dedicated test —
`GcpSecretsEnvironmentPostProcessorRegistrationTest` — boots a bare `SpringApplication`
on the `cloud` profile with no `GOOGLE_CLOUD_PROJECT` set, proving the registration
genuinely works through Spring's real bootstrap machinery (not just a direct method
call) and that the class cleanly no-ops when there's nothing to fetch yet.
`GcpSecretsEnvironmentPostProcessorTest` covers the branching logic itself — which
profile, which project id, which mappings — against a recording fake in place of the
real GCP call, no network needed.

**Cloud SQL, Atlas, and CloudAMQP wiring, all three the same shape:** a
`gcp.secrets.mappings.*` entry mapping straight to the Spring property each
dependency already uses — `spring.datasource.url` (with
`com.google.cloud.sql:postgres-socket-factory` added as a `runtimeOnly` dependency,
loaded reflectively by the JDBC driver via the URL's own `socketFactory=` parameter,
no direct import anywhere in this codebase), `spring.data.mongodb.uri`,
`spring.rabbitmq.addresses` (confirmed directly against `RabbitProperties`' own
parsing code that it genuinely accepts a full `amqps://user:pass@host/vhost` URI as
one address, not just bare `host:port` pairs). `spring.docker.compose.enabled=false`
on the `cloud` profile too — nothing to auto-start once real connection strings are
in play, and no Docker daemon exists inside a Cloud Run container to run Compose
against anyway.

### Deploying for real — commands, not code

Everything below is real `gcloud`/`psql`/dashboard work against real accounts —
verify current flag names against `--help` at teaching time, especially for Worker
Pools (a feature roughly five months old as of this session, still under `gcloud
alpha`/`beta` in some documentation).

**One-time, instructor, before class:**

```
gcloud sql instances create ticketing-shared --database-version=POSTGRES_17 \
  --tier=db-f1-micro --region=europe-central2
gcloud sql databases create ticketing_<student> --instance=ticketing-shared
gcloud sql users create <student> --instance=ticketing-shared --password=<...>
```

Delete the same day the session ends — `gcloud sql instances delete ticketing-shared`.

**Per student, in class:**

```
# Atlas — via the web console: Project > Create Cluster > M0 (Free), then
# Database Access > Add New Database User, Network Access > Allow Access from Anywhere
# (fine for a single-session lab; not a production posture).

# CloudAMQP — cloudamqp.com > Create New Instance > Little Lemur (Free), copy the AMQP URL.

gcloud secrets create cloud-sql-jdbc-url --data-file=- <<< \
  "jdbc:postgresql:///ticketing_<student>?cloudSqlInstance=<PROJECT>:europe-central2:ticketing-shared&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=<student>&password=<...>"
gcloud secrets create atlas-uri --data-file=- <<< "mongodb+srv://<user>:<pass>@<cluster>.mongodb.net/ticketing"
gcloud secrets create cloudamqp-uri --data-file=- <<< "<the amqps:// URL CloudAMQP gave you>"

# The Cloud Run service's own service account needs read access to each secret:
gcloud secrets add-iam-policy-binding cloud-sql-jdbc-url \
  --member="serviceAccount:<PROJECT_NUMBER>-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
# (repeat for atlas-uri, cloudamqp-uri)

./gradlew :core-app:bootBuildImage :payment-service:bootBuildImage
docker tag ticketing/payment-service europe-central2-docker.pkg.dev/<PROJECT>/ticketing/payment-service
docker push europe-central2-docker.pkg.dev/<PROJECT>/ticketing/payment-service
gcloud run deploy payment-service --image europe-central2-docker.pkg.dev/<PROJECT>/ticketing/payment-service --region europe-central2

docker tag ticketing/core-app europe-central2-docker.pkg.dev/<PROJECT>/ticketing/core-app
docker push europe-central2-docker.pkg.dev/<PROJECT>/ticketing/core-app
gcloud run deploy core-app --image europe-central2-docker.pkg.dev/<PROJECT>/ticketing/core-app \
  --region europe-central2 --add-cloudsql-instances=<PROJECT>:europe-central2:ticketing-shared \
  --set-env-vars=SPRING_PROFILES_ACTIVE=cloud,PAYMENT_SERVICE_URL=<payment-service's own Cloud Run URL>

# notification-service — a Worker Pool, not a normal service: no HTTP ingress,
# matches what bootBuildImage's own buildpack detection already confirmed locally.
./gradlew :notification-service:bootBuildImage
docker tag ticketing/notification-service europe-central2-docker.pkg.dev/<PROJECT>/ticketing/notification-service
docker push europe-central2-docker.pkg.dev/<PROJECT>/ticketing/notification-service
gcloud run worker-pools deploy notification-service \
  --image europe-central2-docker.pkg.dev/<PROJECT>/ticketing/notification-service \
  --region europe-central2 --set-env-vars=SPRING_PROFILES_ACTIVE=cloud
```

**Verify it, end to end:** `curl` a real booking against `core-app`'s Cloud Run URL,
watch `payment-service`'s logs show the charge, watch `notification-service`'s Worker
Pool logs show the RabbitMQ message actually arriving from a broker none of this ever
touched locally.

**Teardown — an explicit lab step, not an afterthought:**

```
gcloud run services delete core-app payment-service --region europe-central2
gcloud run worker-pools delete notification-service --region europe-central2
gcloud secrets delete cloud-sql-jdbc-url atlas-uri cloudamqp-uri
# Atlas: Project > Clusters > ... > Terminate. CloudAMQP: Instance > Delete.
```

## Testing strategy — all four layers, explicitly

This is as much a course topic as the code itself: tests are split by what they
actually verify, using the lightest tool that can genuinely test it. Table below
covers `core-app` — still the only subproject with a database or a
Testcontainers-backed unit/persistence suite (`notification-service`'s one
integration test manages its own RabbitMQ container the same manual way, described
above, but doesn't fit neatly into this `core-app`-shaped table).
`payment-service` has its own small pair, `PaymentOutcomeTest` (plain JUnit, the
classification logic) and `PaymentControllerTest` (`@WebMvcTest`, the instant
approve/decline paths only — the 5-second slow path is real production behavior,
demoed live rather than paid for in every test run). `notification-service` adds
`BookingEventsListenerTest` (plain JUnit, the idempotency dedup logic — no broker
needed) and `BookingEventsListenerIntegrationTest` (the real-broker proof, described
above). `shared`, previously untested, gains its first two:
`GcpSecretsEnvironmentPostProcessorTest` (plain JUnit, the branching logic, a
recording fake standing in for the real GCP call) and
`GcpSecretsEnvironmentPostProcessorRegistrationTest` (a bare `SpringApplication`,
proving `META-INF/spring.factories` registration genuinely works through Spring's
real bootstrap machinery, not just a direct method call) — `shared` needed its own
JUnit/AssertJ/`spring-test` dependencies for the first time this session,
version-managed by importing the Spring Boot BOM directly (`io.spring.dependency-management`
alone, not the full Spring Boot Gradle plugin — inappropriate for a plain library),
moved into the base `ticketing.java-conventions` plugin so every module gets
consistent versions, not just the three bootable ones.

| Layer | Example | Tool | Database? |
|---|---|---|---|
| **Unit** | `RefundPolicyTest`, `BookingReportServiceTest` (session 1), `SeatCountTest`, `MoneyTest`, `BookingIdTest`, `EventInventoryTest`, `BookingCancelledEventListenerTest`, `EventClientTest`, `EventServiceTest`, `BookingServiceTest`, `EventAvailabilityAdapterTest`, `PaymentAdapterTest`, `ReviewServiceTest` | plain JUnit, repository/port mocked by hand | no |
| **Controller** | `EventControllerTest`, `BookingControllerTest`, `ReviewControllerTest` | `@WebMvcTest` + `@MockitoBean` on the service | no |
| **Persistence** | `EventRepositoryTest`, `BookingRepositoryTest` | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` | yes — real Postgres |
| **Persistence (Mongo)** | `ReviewRepositoryTest` | `@DataMongoTest` | yes — real MongoDB |
| **Integration** | `EventJourneyIntegrationTest`, `BookingJourneyIntegrationTest` | `@SpringBootTest` + real HTTP (`TestRestTemplate`) | yes — real Postgres |

`booking`'s test classes above live under the same subpackage as the class they
cover — `RefundPolicyTest`/`BookingReportServiceTest` in `booking.domain[.report]`,
`BookingServiceTest`/`BookingPropertiesProfileTest` in `booking.application`,
`BookingControllerTest` in `booking.adapter.in.web`, `BookingRepositoryTest` and
`EventAvailabilityAdapterTest` in `booking.adapter.out.{persistence,catalog}` —
mirroring main, same as `catalog`/`review` already did before session 5.

Three narrower shapes beyond that table: `BookingPropertiesProfileTest` tests
`SpringApplication`'s own profile-file-loading behavior, so it needs real bootstrap
machinery — but not the whole app. `@SpringBootTest(classes = MinimalConfig.class)`
gets real profile loading without pulling in JPA/web/Mongo (`ApplicationContextRunner`
would skip profile-file loading entirely, which is the one thing this test needs to
verify). `ModularityTests` and `BookingArchitectureTests` — architecture, not
behavior: no Spring context at all, just static analysis over compiled classes
(`ApplicationModules.verify()` for cross-module boundaries, ArchUnit's
`layeredArchitecture()` for `booking`'s internal layering). Neither fits
unit/controller/persistence/integration because neither tests what the code *does*,
they test how the code is *shaped* — a genuinely different category, not a smaller
version of the others.

`AbstractIntegrationTest` provides both databases: **one Postgres container and one
Mongo container, each started once, shared across every test class that needs them**
— not one per class. Each test class owns its own data (helper methods building fresh
rows), not the shared base. Splitting it any other way caused genuine cross-test-class
failures during development (Spring's test-context cache colliding with per-class
container lifecycles) before landing on this pattern.

Both integration tests stay from session 2, now backed by real Postgres instead of
in-memory — `EventJourneyIntegrationTest` covers the full Event CRUD lifecycle
(list/update/delete), `BookingJourneyIntegrationTest` covers event+booking
interaction, plus session 6's two additions
(`rejectsABookingThatWouldExceedCapacity`, `cancellingABookingReleasesItsSeatsForReuse`).
No new integration test for reviews: the persistence and controller layers already
cover that feature's real behavior (the Mongo aggregation, the HTTP contract)
without needing a third, more expensive test making the same claims.

**Session 6's two new integration tests are reference-level, not lab output** —
writing a multi-step HTTP proof like `cancellingABookingReleasesItsSeatsForReuse()`
from scratch is realistically 10–15 minutes on its own, which is what was pushing
the lab's second task over its ~30-minute budget. In the lab, students build and
verify `BookingCancelledEventListener` with the fast unit-level tests
(`BookingCancelledEventListenerTest`, the updated `BookingServiceTest`) — genuinely
enough to prove the wiring is correct. The full HTTP-level proof is this branch's
target, confirmed working, and the *skill* of writing one becomes this session's
homework instead, practiced on an unrelated project rather than spending lab minutes
writing it against `ticketing` specifically.

**A test that deliberately breaks its own layer's isolation, and why:**
`EventRepositoryTest.rejectsASecondSaveAgainstAStaleVersion()` opts out of
`@DataJpaTest`'s automatic per-test rollback (`@Transactional(propagation =
NOT_SUPPORTED)`) so its two `save()` calls land in genuinely separate transactions.
That means its writes are real, uncleaned-up commits against the shared container —
unlike every other test in this file — so it deletes its own row at the end by hand
instead of relying on rollback.

**A real cross-test pollution bug, caught the same way the planted bugs were —
reproduced, diagnosed, then fixed, not just reasoned about:** once
`fetchesAllEventsWithTheirVenuesInOneQuery()` started asserting an exact row count,
it intermittently failed with an extra row. That traced back to
`BookingJourneyIntegrationTest`, which creates a real event over HTTP and books it,
but — unlike its sibling `EventJourneyIntegrationTest` — never deleted it, leaking a
permanent row into the Postgres container every test class in the suite shares. Fixed
on both sides: the integration test now deletes what it created, and the repository
test no longer trusts the shared table to contain only its own rows — it filters
`findAll()`'s result down to the ids it just saved before asserting on it.

## Homework

A standalone exercise, not a change to this repo. Take any small Spring Boot project
(a fresh one is fine — a single controller and a database is enough) and:

1. Add `bootBuildImage` support to it — no Dockerfile, just the Spring Boot Gradle
   plugin's built-in task — and confirm the resulting image actually boots locally
   via `docker run`, the same way every image in this session was verified before
   anything real was deployed.
2. Pick one piece of config your project currently keeps in plain text (a database
   password, an API key) and move it to a real secret store — Secret Manager if
   you're on GCP, otherwise whatever your cloud provider's equivalent is. Write the
   fetch logic yourself against the plain client library rather than reaching for
   the first framework integration you find, and confirm it actually works before
   trusting it — the same discipline this session applied when the obvious Spring
   starter turned out to be broken on this project's Boot version.

Same mechanic as this session's own real finding — `spring-cloud-gcp-starter-secretmanager`
looked like the obvious choice and wasn't, confirmed by trying it, not by assuming a
popular library must be fine. Practicing "verify the dependency actually works
before building on it" on unfamiliar infrastructure, not just unfamiliar code.

Next up, Session 9: GKE — provided Kubernetes manifests (not hand-written; reading
and adapting them is the skill, not authoring YAML from scratch), a Horizontal Pod
Autoscaler, and break-and-fix labs against a real cluster. The first session where
"more than one instance of a service running at once" is something students
genuinely observe, not just reason about.
