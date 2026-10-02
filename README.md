# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-10-start`**.

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

### Module diagram

`ModularityTests.generatesModuleDiagram()` regenerates it on every test run —
`core-app/build/spring-modulith-docs/components.puml`, a PlantUML file showing
the five modules (`Catalog`, `Messaging`, `Booking`, `Review`, `Shared`) and
their real dependencies, straight from `ApplicationModules.of(...)`, not
hand-drawn. Render it with any PlantUML viewer/extension, or paste it into
[PlantUML's own online editor](https://www.plantuml.com/plantuml). A separate
Gradle dependency from `verify()`'s own boundary-checking
(`spring-modulith-starter-core`) — confirmed directly against the jars that
the `Documenter`/diagram-generation API lives in a different artifact
entirely, `spring-modulith-docs`, not bundled with it.

## Where things stand — Session 8: Cloud Run (end)

A different kind of session, continued from `session-08-start`. Sessions 1–7 could
all be fully built, tested, and verified by running `./gradlew build` — every piece
of infrastructure was Docker-on-a-laptop, reproducible for anyone, verifiable by CI.
Session 8 leaves that world: Cloud Run, Cloud SQL, Atlas, CloudAMQP, and Secret
Manager are real external services with real accounts behind them. Unlike the
earlier version of this section, every command below — provisioning Atlas/CloudAMQP/
Cloud SQL, creating the Secret Manager entries, and the actual
`bootBuildImage` → Artifact Registry → `gcloud run deploy` → `gcloud run
worker-pools deploy` path — was genuinely run against a real GCP project this
session, not just documented. It took ten real, distinct failures to get a clean
end-to-end booking confirmed over a real RabbitMQ broker; every one of them is
recorded below as a finding, not smoothed over. That gap between "the plan reads
correctly" and "the plan survives contact with a real cloud account" is itself the
Session 8 lesson — see **Real findings from actually running this**, below.

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
runs only on the `cloud` profile, only if `GOOGLE_CLOUD_PROJECT` is set, only if
`gcp.secrets.mappings.*` properties exist. **Correction from the previous version of
this section:** `GOOGLE_CLOUD_PROJECT` is *not* auto-injected by Cloud Run for a
regular service or worker pool — that was a wrong assumption, confirmed both by
actually deploying without it (the post-processor silently no-opped, `datasource.url`
came back unset) and by the runtime contract docs (only Cloud Run *functions* get a
handful of auto-set vars; services and worker pools do not). It has to be passed
explicitly on every `gcloud run deploy`/`gcloud run worker-pools deploy` via
`--set-env-vars`, every time — see **Real findings**, below. Each
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
no direct import anywhere in this codebase), `spring.mongodb.uri`,
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
  --tier=db-f1-micro --region=europe-central2 --edition=ENTERPRISE
gcloud sql databases create ticketing_<student> --instance=ticketing-shared
gcloud sql users create <student> --instance=ticketing-shared --password=<...>
```

`--edition=ENTERPRISE` is not optional, confirmed by actually running this without
it: `gcloud` now defaults new Cloud SQL instances to the Enterprise Plus edition,
and `db-f1-micro` (the cheap shared-core tier this whole setup depends on) only
exists under plain Enterprise. Omitting the flag fails immediately with `Invalid
Tier (db-f1-micro) for (ENTERPRISE_PLUS) Edition` — first error of the session, and
a one-flag fix.

Delete the same day the session ends — `gcloud sql instances delete ticketing-shared`.

**Per student, in class:**

```
# Atlas — via the web console: Project > Create Cluster > M0 (Free), then
# Database Access > Add New Database User, Network Access > Allow Access from Anywhere
# (fine for a single-session lab; not a production posture).

# CloudAMQP — cloudamqp.com > Create New Instance > Little Lemur (Free), copy the AMQP URL.

gcloud secrets create cloud-sql-jdbc-url --data-file=- <<< \
  "jdbc:postgresql:///ticketing_<student>?cloudSqlInstance=<PROJECT>:europe-central2:ticketing-shared&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=<student>&password=<...>&sslmode=disable"
gcloud secrets create atlas-uri --data-file=- <<< "mongodb+srv://<user>:<pass>@<cluster>.mongodb.net/ticketing"
gcloud secrets create cloudamqp-uri --data-file=- <<< "<the amqps:// URL CloudAMQP gave you>"

# The Cloud Run service's own service account needs read access to each secret —
# roles/editor (what the default compute SA has by default) does NOT cover this,
# confirmed by hitting PERMISSION_DENIED on secretmanager.versions.access with a
# real deploy. Grant it explicitly, every time:
gcloud secrets add-iam-policy-binding cloud-sql-jdbc-url \
  --member="serviceAccount:<PROJECT_NUMBER>-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
# (repeat for atlas-uri, cloudamqp-uri)
gcloud projects add-iam-policy-binding <PROJECT> \
  --member="serviceAccount:<PROJECT_NUMBER>-compute@developer.gserviceaccount.com" \
  --role="roles/cloudsql.client"

./gradlew :core-app:bootBuildImage :payment-service:bootBuildImage :notification-service:bootBuildImage
docker tag ticketing/payment-service europe-central2-docker.pkg.dev/<PROJECT>/ticketing/payment-service
docker push europe-central2-docker.pkg.dev/<PROJECT>/ticketing/payment-service
gcloud run deploy payment-service --image europe-central2-docker.pkg.dev/<PROJECT>/ticketing/payment-service \
  --region europe-central2 --allow-unauthenticated --memory=1Gi

docker tag ticketing/core-app europe-central2-docker.pkg.dev/<PROJECT>/ticketing/core-app
docker push europe-central2-docker.pkg.dev/<PROJECT>/ticketing/core-app
gcloud run deploy core-app --image europe-central2-docker.pkg.dev/<PROJECT>/ticketing/core-app \
  --region europe-central2 --allow-unauthenticated --memory=1Gi \
  --add-cloudsql-instances=<PROJECT>:europe-central2:ticketing-shared \
  --set-env-vars=SPRING_PROFILES_ACTIVE=cloud,GOOGLE_CLOUD_PROJECT=<PROJECT>,PAYMENT_SERVICE_URL=<payment-service's own Cloud Run URL>

# notification-service — a Worker Pool, not a normal service: no HTTP ingress,
# matches what bootBuildImage's own buildpack detection already confirmed locally.
# `gcloud run worker-pools` needs the grpc Python module locally — see Real
# findings below if this fails with "No module named 'grpc'".
docker tag ticketing/notification-service europe-central2-docker.pkg.dev/<PROJECT>/ticketing/notification-service
docker push europe-central2-docker.pkg.dev/<PROJECT>/ticketing/notification-service
gcloud run worker-pools deploy notification-service \
  --image europe-central2-docker.pkg.dev/<PROJECT>/ticketing/notification-service \
  --region europe-central2 --memory=1Gi \
  --set-env-vars=SPRING_PROFILES_ACTIVE=cloud,GOOGLE_CLOUD_PROJECT=<PROJECT>
```

### Real findings from actually running this

Ten distinct, real failures between a clean `gcloud sql instances create` and a
confirmed booking notification arriving over a real RabbitMQ broker. Each one
genuinely reproduced, diagnosed from the actual error, then fixed — not
anticipated in advance. Session 8's real lesson isn't any one of these, it's that
there were this many: cloud deployment fails in layers, and each layer's error
message points at the next one, not all of them at once.

1. **`gcloud sql instances create` defaults to the wrong edition.** Covered above —
   `--edition=ENTERPRISE` required for `db-f1-micro` to be a valid tier at all.
2. **`spring.data.mongodb.uri` is dead, not just old.** Spring Boot 4.0 split Mongo
   config into a plain-client module (`spring.mongodb.*`) and a Spring-Data-only
   module (`spring.data.mongodb.*`, now just repository/GridFS settings). The old
   connection properties (`uri`, `host`, `username`, `password`, `ssl.*`) are
   deprecated at **error** level, confirmed directly against the jar's own
   `spring-configuration-metadata.json` — meaning they don't bind at all, not just
   warn. `gcp.secrets.mappings.atlas-uri` must map to `spring.mongodb.uri`.
3. **Hibernate logs the raw JDBC URL, credentials included, at INFO.** The
   `HHH10001005` connection-info diagnostic (`org.hibernate.orm.connections.pooling`
   logger) prints whatever's in the URL — including an embedded password, when the
   URL has one, which Cloud SQL's socket-factory URL does. Suppressed with
   `logging.level.org.hibernate.orm.connections.pooling=WARN` in
   `core-app/src/main/resources/application.properties`, before this ever reached
   real Cloud Logging.
4. **`GOOGLE_CLOUD_PROJECT` is not auto-injected by Cloud Run.** Corrected above —
   must be passed explicitly via `--set-env-vars` on every deploy.
5. **A hardcoded `server.port` fails Cloud Run's startup probe.**
   `payment-service` hardcoded `server.port=8081`; Cloud Run injects the real
   listen port via the `PORT` env var (8080 by default) and health-checks exactly
   that port — the app never opened it, so the deploy failed with *"container
   failed to start and listen on the port defined by PORT=8080"*. Fixed the same
   way in all three services for consistency, not just the one that broke:
   `server.port=${PORT:8081}` (and `:8080`/`:8082` for `core-app`/
   `notification-service`) — falls back to the old local default when `PORT` isn't
   set, so local dev is unaffected.
6. **Cloud Run's default memory (512Mi) is too small for the JVM's own fixed
   overhead**, independent of anything the app does. The buildpack's memory
   calculator reported needing 592292K just for
   `-XX:MaxDirectMemorySize=10M -XX:MaxMetaspaceSize=80292K
   -XX:ReservedCodeCacheSize=240M -Xss1M * 250 threads` — before any heap is
   allocated — and refused to start. Fixed with `--memory=1Gi` on every deploy;
   this is exactly the "JVM inside a container" theory point from this session's
   own outline, now a real failure instead of a slide.
7. **`roles/editor` does not include Secret Manager access.** The default Compute
   Engine service account (what Cloud Run runs as unless told otherwise) has
   `roles/editor`, which looks broad enough to cover this and doesn't —
   `secretmanager.versions.access` is deliberately carved out as its own grant.
   Confirmed by a real `PERMISSION_DENIED` on first deploy; fixed with an explicit
   `secretmanager.secretAccessor` binding per secret (command above). The same
   account was also missing `roles/cloudsql.client` outright — grant both up
   front rather than discovering the second one on a second failed deploy.
8. **The Cloud SQL Java Connector and pgjdbc fight each other over SSL.** The
   socket factory already wraps the connection in its own encrypted,
   mutually-authenticated tunnel; when pgjdbc *also* tries to negotiate its own
   SSL handshake on top, the extra negotiation gets an unexpected `EOFException`
   mid-handshake. Fix is `sslmode=disable` in the JDBC URL — the name is
   misleading, it only disables the driver's *own* redundant SSL layer, the
   connection is still fully encrypted by the connector itself. See
   [the connector's own docs](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/blob/main/docs/jdbc.md)
   and [confirming GitHub issue](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/175).
9. **`gcloud run worker-pools` needs a Python `grpc` module gcloud doesn't bundle
   by default on Windows**, failing with `No module named 'grpc'` before the
   command even runs. `gcloud`'s own error message suggests `pip3 install grpc` —
   that's the wrong package; the real one is `grpcio`. Beyond that,
   `CLOUDSDK_PYTHON_SITEPACKAGES=1` also has to be set, or gcloud's Python runs
   with `-S` (skip site-packages) and ignores the installed module anyway. Two-part
   local fix, per machine, once: `python -m pip install grpcio grpcio-status` then
   `export CLOUDSDK_PYTHON_SITEPACKAGES=1` (and `export CLOUDSDK_PYTHON=<path>` if
   multiple Pythons are on `PATH` and gcloud resolves the wrong one — it uses
   Windows' native `where python`, which can differ from what a POSIX shell's
   `which` reports).
10. **Locally-tuned Resilience4j timeouts are too aggressive for a real
    cross-service cold start.** `payment-service.connect-timeout=1s`/
    `read-timeout=2s` (`core-app/src/main/resources/application.properties:20-27`)
    are deliberately tight for an instant local demo. Against real Cloud Run,
    the first booking after `payment-service` scaled to zero returned a 503
    (`Payment service is currently unavailable`) — the timeout gave up before
    the ~4.4s cold start finished. A second, identical request succeeded
    immediately once the instance was warm. Not fixed in code this session —
    it's a live demonstration of exactly the Session 8 stretch goal ("measure
    cold starts with min instances 0 vs 1"), left as a deliberate teaching
    moment rather than papered over with a longer timeout.

**Confirmed working end to end**, for real, after all of the above: `core-app`
deployed to Cloud Run, connected to real Cloud SQL and real Atlas via Secret
Manager; a booking created over its public URL published a real message to
CloudAMQP; `notification-service`'s Worker Pool picked it up and logged
`Notification: booking 1 confirmed for Ada Lovelace — 2 seat(s) on event 2` —
visible in real Cloud Logging, not a local console.

**Everything was torn down after verifying** — `core-app`/`payment-service`
services deleted, the `notification-service` Worker Pool deleted, the Cloud SQL
instance stopped (`--activation-policy=NEVER`, not deleted — data and setup
preserved for next time). See **Teardown**, below, for the commands.

**Teardown — an explicit lab step, not an afterthought:**

```
# services delete only takes one name at a time — confirmed by
# `gcloud run services delete core-app payment-service` failing with
# "unrecognized arguments", run separately:
gcloud run services delete core-app --region europe-central2
gcloud run services delete payment-service --region europe-central2
gcloud run worker-pools delete notification-service --region europe-central2

# Cloud SQL: stop between sessions rather than delete, to keep the database and
# not have to recreate it — deleting drops the data, stopping doesn't:
gcloud sql instances patch ticketing-shared --activation-policy=NEVER
# (only delete it outright at the very end of the course: gcloud sql instances delete ticketing-shared)

gcloud secrets delete cloud-sql-jdbc-url atlas-uri cloudamqp-uri
# Atlas: Project > Clusters > ... > Terminate. CloudAMQP: Instance > Delete.
```

## Where things stand — Session 9: GKE (end)

A real Autopilot cluster was created, every manifest in `k8s/` was applied to
it, and the full booking flow was proven end to end — a real `curl` through
`core-app`'s Service, a real charge through `payment-service` over Service
DNS, a real message landing in `notification-service` over the same CloudAMQP
broker Session 8 used. Rolling update, rollback, and all three break-and-fix
scenarios were each triggered for real and produced exactly their documented
symptom. The cluster was torn down immediately after — see `k8s/README.md`'s
teardown section — so none of this is still running or billing.

The one real design decision worth calling out: Session 8 authenticated to
Cloud SQL via a JDBC `socketFactory` URL embedded with credentials. This
session uses the standard GKE pattern instead — the Cloud SQL Auth Proxy as
a **sidecar container** in `core-app`'s pod, authenticated via Workload
Identity, with `core-app` itself just talking plain Postgres to
`localhost:5432`. That sidesteps Session 8's `sslmode=disable` finding
entirely and is a more honest contrast for the ConfigMaps/Secrets theory
point — ordinary Kubernetes primitives doing the configuration job, not a
cloud-provider SDK.

### Real findings from actually running this

Three genuine, reproduced-live issues, none of them anticipated when the
manifests were first written and schema-validated — proof that
`--dry-run=client` checks shape, not behavior.

1. **A real sidecar startup race.** With `cloud-sql-proxy` as a plain second
   container (no `restartPolicy`), both containers in the pod start
   simultaneously with no ordering guarantee. `core-app`'s first boot reached
   Hikari before the proxy had finished its own Cloud SQL Admin API
   handshake and opened `127.0.0.1:5432` — crashed with exit code 1 after 22
   seconds, succeeded only on Kubernetes' automatic restart. Fixed with
   Kubernetes' **native sidecar** feature (`restartPolicy: Always` on the
   `cloud-sql-proxy` container, GA since 1.28 — our cluster ran 1.35) plus a
   `startupProbe`, which gates `core-app`'s own start on the proxy's probe
   actually passing — closing the race instead of surviving it by luck.
2. **The obvious probe for that fix was itself wrong, and it crash-looped
   the sidecar forever.** A first attempt used `startupProbe: {tcpSocket:
   {port: 5432}}`. The proxy deliberately binds `5432` to `127.0.0.1`
   only — nothing outside the pod should reach the DB tunnel — but kubelet's
   probe connects to the **pod's IP**, not loopback, so the check failed
   continuously even while the proxy was genuinely healthy and actively
   serving `core-app`'s connections (confirmed in its own logs). kubelet
   killed and restarted a working container repeatedly because the probe
   itself could never succeed. Fix: the proxy's own `--health-check` flag,
   which starts an HTTP health server — but it has the *identical*
   loopback-only default, needing `--http-address=0.0.0.0` explicitly before
   kubelet can reach it at all. `startupProbe` then checks that server's
   `/startup` path instead of the raw Postgres port.
3. **The default 1-second probe timeout is too tight for a cold JVM.**
   `payment-service` logged transient `context deadline exceeded` probe
   failures right after startup — the JVM's first few Actuator responses
   occasionally missed kubelet's default `timeoutSeconds: 1`. Didn't cause a
   restart this time (it self-resolved within the failure threshold), but
   it's the same category of issue as Session 8's cold-start timeout
   finding, one layer down. Fixed with `timeoutSeconds: 3` on both services'
   probes.

Everything else — Workload Identity, the ConfigMap/Secret wiring, Service
DNS resolution, the three break-and-fix symptoms — worked exactly as
designed on the first real attempt once the sidecar race above was fixed.

## Where things stand — Session 10: CI/CD (end)

Branched from `session-09-end`, not `session-09-start` — the earlier mix-up
where this session's start branch briefly carried Session 9's finished work
has been corrected; see the git history if curious, not repeated here.

The pipeline is real now, not just planned — `.github/workflows/build.yml`
gained a `deploy` job that genuinely builds, SHA-tags, pushes, and deploys
all three services to a live GKE cluster, gated behind the `production`
Environment's approval, authenticated via Workload Identity Federation with
no key material anywhere. A real push to `main`, a real manual approval, a
real rollout — confirmed by a real booking traveling through Cloud SQL,
`payment-service`, and a CloudAMQP notification arriving in
`notification-service`'s logs, same proof standard as every other session.

**This repo also gained its first conventional trunk branch, `main`,
during this session** — it didn't have one. Every prior branch was a
session snapshot; nothing acted as the evolving "current state" a CI
pipeline normally deploys from. `main` was cut from `session-10-end`'s tip
and is now the GitHub default branch too (previously, by accident of
history, the default branch was `session-01-start`).

What was provisioned ahead of the live-coding, per the spec's own call-out
that Workload Identity Federation is "the most likely thing to eat an hour"
and should be pre-configured if so:

**Workload Identity Federation — provisioned and verified, not just
documented:**
```
gcloud iam workload-identity-pools create github-actions-pool \
  --project=ticketing-app-510211 --location=global \
  --display-name="GitHub Actions Pool"

gcloud iam workload-identity-pools providers create-oidc github-actions-provider \
  --project=ticketing-app-510211 --location=global \
  --workload-identity-pool=github-actions-pool \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='markvaradi94/demo-ticketing-app'"

gcloud iam service-accounts create github-actions-ci --project=ticketing-app-510211

# Least privilege — push images, deploy to GKE, nothing else:
gcloud projects add-iam-policy-binding ticketing-app-510211 \
  --member="serviceAccount:github-actions-ci@ticketing-app-510211.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"
gcloud projects add-iam-policy-binding ticketing-app-510211 \
  --member="serviceAccount:github-actions-ci@ticketing-app-510211.iam.gserviceaccount.com" \
  --role="roles/container.developer"

# Only this specific repo's GitHub Actions tokens may impersonate the SA —
# scoped by the --attribute-condition above, not just by who has the name:
gcloud iam service-accounts add-iam-policy-binding \
  github-actions-ci@ticketing-app-510211.iam.gserviceaccount.com \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/793547076785/locations/global/workloadIdentityPools/github-actions-pool/attribute.repository/markvaradi94/demo-ticketing-app"
```
No key material exists anywhere — that's the entire point of WIF over the
long-lived service-account-key approach the theory block argues against.

**GitHub side, also provisioned, not just planned** — a `production`
Environment with a required reviewer (the manual approval gate from the
spec), and the WIF/GCP/GKE identifiers as Environment variables, so the
live-coded workflow YAML references `${{ vars.* }}` instead of anything
hardcoded:
```
gh api repos/markvaradi94/demo-ticketing-app/environments/production -X PUT \
  -f "reviewers[][type]=User" -F "reviewers[][id]=<your-github-user-id>"

gh variable set GCP_PROJECT_ID --env production --body "ticketing-app-510211"
gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER --env production --body "projects/793547076785/locations/global/workloadIdentityPools/github-actions-pool/providers/github-actions-provider"
gh variable set GCP_CI_SERVICE_ACCOUNT --env production --body "github-actions-ci@ticketing-app-510211.iam.gserviceaccount.com"
gh variable set GKE_CLUSTER_NAME --env production --body "ticketing"
gh variable set GKE_CLUSTER_REGION --env production --body "europe-central2"
gh variable set ARTIFACT_REGISTRY --env production --body "europe-central2-docker.pkg.dev/ticketing-app-510211/ticketing"
```

### Real findings from actually running this

Three genuine findings, on top of the ten from Session 8 and three from
Session 9 — the running total says more about what "verify, don't assume"
actually costs than any one of them individually does.

1. **This repo had no `main` branch at all.** The workflow's first draft
   triggered `deploy` on `refs/heads/main`, copied from the spec's generic
   phrasing without checking — and this repo's entire structure is session
   branches, no evolving trunk. Worse, the actual GitHub default branch
   was `session-01-start`, an accident of history nobody had looked at
   since the repo was created. Fixed by creating `main` for real (from
   `session-10-end`'s tip) and setting it as the GitHub default — not a
   workaround, a genuine missing piece of repo hygiene this session
   surfaced.
2. **The Session 9 sidecar fix reduces the startup race, doesn't eliminate
   it.** With the native-sidecar `restartPolicy`/`startupProbe` fix in
   place, `cloud-sql-proxy` itself never crashed this session (confirmed:
   `restarts=0` throughout) — but `core-app` still hit one `Connection
   refused` to `localhost:5432` and restarted once before stabilizing. The
   proxy's `/startup` endpoint most likely reports ready a moment before
   its actual Postgres listener is accepting connections — the HTTP health
   server and the TCP proxy socket don't necessarily finish initializing in
   the same instant. Not fixed further this session (it still self-heals
   via one automatic restart, same as before the native-sidecar fix even
   existed) — recorded honestly as a real limit of the existing fix rather
   than claimed as fully solved.
3. **`actions/setup-java@v4` and the Node.js 20 runtime it depends on are
   both deprecated**, flagged directly in the workflow run's own
   annotations. Not blocking — GitHub is still forcing these actions onto
   Node 24 under the hood — but a real, dated thing to bump
   (`actions/setup-java@v5`) before it becomes a real break instead of a
   warning.

**Confirmed working end to end, genuinely, not assumed:** a push to `main`
→ `build` job green → `deploy` paused on the `production` Environment's
approval gate → approved → WIF authentication succeeded with zero key
material → all three images built, SHA-tagged, and pushed → GKE rollout
completed with **zero container restarts** (cleaner than the manual deploy
earlier this same session) → a real booking confirmed through Cloud SQL and
`payment-service` → a real CloudAMQP message picked up by
`notification-service`. Then, separately: a deliberately broken image
deployed on purpose → genuine `ImagePullBackOff`, rollout genuinely stuck,
the two prior healthy replicas continuing to serve traffic the entire
time → `kubectl rollout undo` → clean recovery, confirmed by another real
booking succeeding immediately after.

**Everything was torn down after verifying** — same cost discipline as
Sessions 8 and 9: the GKE cluster deleted, Cloud SQL stopped. Nothing from
this session's testing is still running or billing.

### Final project — definition of done

Stated here, as the spec calls for, now that the pipeline itself is real:

1. All three services run.
2. The booking flow works end to end.
3. Overbooking is rejected, proven by a test.
4. `./gradlew check` passes, including Modulith verification and ArchUnit
   rules.
5. The CI pipeline is green.
6. The README explains how to run the project locally, and includes the
   generated module diagram.

**Explicitly not required:** GKE with the pipeline deploying to it
automatically — that's the stretch goal this session itself just proved
out, not the bar every student needs to clear. A student whose own cluster
is broken still has a complete, working system and a green pipeline against
it; GKE is where the ambitious difference shows up, not where "done" is
decided.

**Deadline:** two weeks after this session — long enough for people with
day jobs, short enough to keep momentum, and inside the GCP trial window
this whole course has been built against.

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

## Homework (Session 8)

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

## Homework (Session 9)

Also standalone, not a change to this repo. Take any small multi-container setup —
two containers you control is enough, it doesn't need to be Kubernetes-specific to
start:

1. Write a Kubernetes Deployment with a sidecar container in the same pod (any
   sidecar pattern works — a proxy, a log shipper, anything with its own startup
   time). Give the sidecar a `startupProbe` and get it wrong the way this session
   first did: if the sidecar binds its port to `127.0.0.1` only (the common,
   deliberate default for anything not meant to be reached from outside the pod),
   confirm for yourself that a plain `tcpSocket`/`httpGet` probe against it fails
   every time — kubelet probes the pod's IP, not loopback — before working out the
   actual fix.
2. Remove any startup ordering between your main container and the sidecar and
   watch the main container crash on its first attempt, the same race `core-app`
   hit tonight. Then fix it properly with Kubernetes' native sidecar pattern
   (`restartPolicy: Always` + a passing `startupProbe` gating the main container's
   start) — not by letting Kubernetes' automatic restart quietly paper over a race
   that's still there.

Same mechanic as both this course's running theme: a plausible-looking first
attempt (a second container, a direct port check) was wrong in a way that only
showed up by actually running it, not by reading the manifest. Confirmed on a real
cluster, not assumed from the YAML looking reasonable.

Next up, Session 10: CI/CD — Workload Identity Federation instead of long-lived
service account keys, a pipeline that builds, tests, and deploys on every commit,
GitHub Environments with an approval gate, and `kubectl rollout undo` as the
rollback story. The final project's definition of done gets stated here too.
