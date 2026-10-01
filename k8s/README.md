# Session 9 — GKE manifests

**Verified against a real Autopilot cluster** — every command below was
actually run, including teardown. Three real issues surfaced along the way
(a sidecar startup race, a loopback-vs-pod-IP probe mistake, a too-tight
default probe timeout) — full detail and fixes in the main `README.md`'s
"Real findings" section; the manifests in this directory already have all
three fixes applied.

Provided manifests, per the course's own convention for this session — reading
and adapting them is the skill being taught, not hand-writing Kubernetes YAML
from scratch. Targets the same instructor project/images Session 8 already
built and pushed: `ticketing-app-510211`, `europe-central2`, images at
`europe-central2-docker.pkg.dev/ticketing-app-510211/ticketing/*:latest`.

## What's different from Session 8, and why

Session 8 used `GcpSecretsEnvironmentPostProcessor` + GCP Secret Manager —
a cloud-provider-specific mechanism. This session uses native Kubernetes
ConfigMaps and Secrets instead, which is itself the theory point (centralized
configuration, no new framework needed). No app code changes were required to
support this: the `cloud` profile is reused as-is for
`spring.docker.compose.enabled=false`, and since nothing here sets
`GOOGLE_CLOUD_PROJECT`, the Secret Manager post-processor simply no-ops —
every connection detail below comes from `envFrom`/`secretKeyRef` instead.

Cloud SQL connectivity also changes shape: rather than the JDBC
`socketFactory` URL from Session 8 (and its `sslmode=disable` fight between
pgjdbc and the connector), `core-app`'s pod runs the **Cloud SQL Auth Proxy as
a sidecar container** — the standard GKE pattern. `core-app` just talks plain
Postgres to `localhost:5432`; the sidecar is the only thing that ever reaches
the real Cloud SQL instance, authenticated via Workload Identity rather than
an embedded JDBC credential.

## One-time instructor setup

```bash
gcloud services enable container.googleapis.com --project=ticketing-app-510211

gcloud container clusters create-auto ticketing \
  --region=europe-central2 --project=ticketing-app-510211

gcloud container clusters get-credentials ticketing \
  --region=europe-central2 --project=ticketing-app-510211

# Workload Identity — required on Autopilot, no node-level service account
# key fallback exists the way Standard mode allows.
gcloud iam service-accounts create ticketing-gke-sa --project=ticketing-app-510211

gcloud projects add-iam-policy-binding ticketing-app-510211 \
  --member="serviceAccount:ticketing-gke-sa@ticketing-app-510211.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

kubectl create serviceaccount ticketing-ksa

gcloud iam service-accounts add-iam-policy-binding \
  ticketing-gke-sa@ticketing-app-510211.iam.gserviceaccount.com \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:ticketing-app-510211.svc.id.goog[default/ticketing-ksa]"

kubectl annotate serviceaccount ticketing-ksa \
  iam.gke.io/gcp-service-account=ticketing-gke-sa@ticketing-app-510211.iam.gserviceaccount.com
```

## Per student, in class

Reuses each student's own Atlas/CloudAMQP from Session 8, plus their own
Cloud SQL database/user on the shared instructor instance (`ticketing-shared`
— same instance Session 8 used, create a fresh `ticketing_<student>` database
and user on it if Session 8's isn't still around).

```bash
kubectl create secret generic ticketing-secrets \
  --from-literal=db-username=<student> \
  --from-literal=db-password=<...> \
  --from-literal=mongo-uri="mongodb+srv://<user>:<pass>@<cluster>.mongodb.net/ticketing" \
  --from-literal=rabbitmq-host=<cloudamqp-host> \
  --from-literal=rabbitmq-port=5671 \
  --from-literal=rabbitmq-username=<user> \
  --from-literal=rabbitmq-password=<pass> \
  --from-literal=rabbitmq-vhost=<vhost>

kubectl apply -f k8s/configmap.yaml \
  -f k8s/core-app-deployment.yaml -f k8s/core-app-service.yaml \
  -f k8s/payment-service-deployment.yaml -f k8s/payment-service-service.yaml \
  -f k8s/notification-service-deployment.yaml

kubectl get pods --watch
```

## Verify it

```bash
kubectl port-forward svc/core-app 8080:8080
# in another terminal:
curl -X POST http://localhost:8080/events -H "Content-Type: application/json" \
  -d '{"name":"Test Show","venueName":"Test Venue","venueCapacity":100,"pricePerSeat":25.00,"startTime":"2026-12-01T20:00:00Z"}'
```

Check `notification-service`'s logs for the booking confirmation, same as
Session 8's verification, just over `kubectl logs` instead of Cloud Logging:

```bash
kubectl logs deployment/notification-service
```

## Rolling update and rollback, live

```bash
kubectl set image deployment/core-app core-app=europe-central2-docker.pkg.dev/ticketing-app-510211/ticketing/core-app:latest
kubectl rollout status deployment/core-app
kubectl rollout undo deployment/core-app
```

## Break-and-fix (`k8s/broken/`)

Three manifests, each a complete copy of `core-app-deployment.yaml` with
exactly one planted bug, applied one at a time:

```bash
kubectl apply -f k8s/broken/core-app-wrong-image-tag.yaml      # ImagePullBackOff
kubectl apply -f k8s/broken/core-app-missing-secret.yaml       # CreateContainerConfigError
kubectl apply -f k8s/broken/core-app-bad-readiness-probe.yaml  # Running but never Ready
```

Diagnose each with `kubectl describe pod <name>` (and `kubectl logs <name>`
for the probe case — the app itself is running fine, only the probe is
wrong). Fix by reapplying the real `k8s/core-app-deployment.yaml`. Timebox
this to 20 minutes total, per the course plan.

## Stretch — HPA

```bash
kubectl apply -f k8s/hpa.yaml
kubectl get hpa core-app --watch
```
Drive load against the booking endpoint (a simple loop of `curl` calls is
enough) and watch `REPLICAS` climb from 2 toward 6 as CPU utilization crosses
60%. GKE Autopilot ships `metrics-server` built in — nothing extra to
install first.

## Teardown

```bash
kubectl delete secret ticketing-secrets
gcloud container clusters delete ticketing --region=europe-central2 --project=ticketing-app-510211
```
An Autopilot cluster bills per-pod resource request the whole time it's
running — delete it the same day the session ends, same cost-hygiene habit
as stopping Cloud SQL in Session 8.
