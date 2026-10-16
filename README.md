# Payments API

A Spring Boot payments service with JWT (RS256) auth, refund workflows, webhook processing, and analytics — a portfolio project spanning app code, CI/CD, infrastructure as code (Terraform), and observability.

**Live demo:** `http://18.153.65.216:8080/swagger-ui.html`
> Runs on a free-tier AWS EC2 instance without an Elastic IP, so the public IP can change if the instance is restarted, and the environment may be torn down for cost control — see [Deployment](#deployment) to spin it back up via Terraform.

---

## Features

- **Payments** — initiate (idempotent), look up, filter/paginate, export to CSV
- **Refunds** — customer request → admin approve/reject (two-step, not a single unmoderated action)
- **Analytics** — admin-only volume/status reporting over a date range
- **Auth** — registration, login, refresh tokens, JWT (RSA key pair)
- **User management** — profile updates, password change, account deactivation/reactivation/deletion
- **Webhooks** — signed payment callbacks (`X-Signature`, HMAC-SHA256), simulating an external provider
- **Rate limiting & idempotency** — Redis-backed (Bucket4j), correct across multiple app instances
- **Caching** — Redis-backed single-payment lookups with invalidation on state changes
- **Observability** — Actuator health/metrics + Prometheus/Grafana (local dev only, via docker-compose)
- **Audit trail** — `createdBy` / `lastModifiedBy` / timestamps via Spring Data JPA auditing

## Tech Stack

Java 21 · Spring Boot 4 (Web, Security, Data JPA) · PostgreSQL + Liquibase · Redis (Lettuce) · JJWT (RS256) · springdoc-openapi · Testcontainers / JUnit 5 / Mockito · Docker (multi-stage, `jlink`-trimmed JRE) · GitHub Actions · Terraform (AWS) · Prometheus + Grafana

---

## Getting Started

**Prerequisites:** JDK 21, Docker (for Postgres/Redis/Prometheus/Grafana)

```bash
docker-compose up -d      # Postgres, Redis, Prometheus, Grafana
./mvnw spring-boot:run    # applies Liquibase migrations on startup
```

App runs at `http://localhost:8080`. Two users are seeded by Liquibase for local dev (see `db/changelog`) — replace before any non-local use.

- **API docs:** Swagger UI at `/swagger-ui.html`, OpenAPI spec at `/v3/api-docs`
- **Monitoring (local only):** Prometheus `:9090`, Grafana `:3000` (admin/admin), Actuator `/actuator/health` + `/actuator/prometheus`

### Configuration

Key env vars (see `src/main/resources/application.yaml`):

| Variable | Purpose |
|---|---|
| `PUBLIC_URL` | Public base URL for the OpenAPI spec |
| `DB_URL` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL connection |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | JWT signing keys (falls back to `src/main/resources/keys/` if unset) |
| `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | Redis connection |

Webhook signatures use the `webhook.secret` property. All secrets are injected via environment variables at container runtime — never baked into the image or committed.

---

## API Overview

All endpoints are under `/api/v1`, JWT bearer auth required except `/auth/**` and `/webhooks/**`. Full endpoint reference lives in Swagger UI / the OpenAPI spec (above) — summary:

- **`/auth`** — login, register, refresh
- **`/payments`** (CUSTOMER or ADMIN) — initiate, get by ID, filtered search, CSV export, request refund, admin refund decision, admin analytics
- **`/users`** (authenticated) — update profile, change password, deactivate/reactivate/delete account
- **`/webhooks`** (public, signature-verified) — payment status callbacks

---

## Testing

```bash
./mvnw test
```

Unit tests mock dependencies; integration tests (`src/test/java/.../integration`) use Testcontainers to exercise real Postgres/Redis end-to-end.

---

## Deployment

`Dockerfile` builds a slim image via multi-stage `jlink` (no Maven/JDK/build toolchain in the final image).

`/infra` holds Terraform for AWS — fully as code:
- VPC, public subnets across two AZs
- Security groups: internet → EC2 (app + SSH, IP-restricted) → RDS (EC2-only)
- RDS PostgreSQL (`db.t3.micro`, SSL-required) · EC2 (`t3.micro`) running app + Redis as containers

```bash
cd infra
terraform init
terraform apply     # needs terraform.tfvars with db_password — see terraform.tfvars.example
terraform destroy   # tear down when idle, to stay in free tier
```

**CI/CD:** GitHub Actions runs the full test suite on every push/PR to `master`, then builds and pushes the Docker image.

---

## Design Decisions & Trade-offs

Deliberate simplifications, since "right for production" and "right for a cost-conscious learning project" aren't always the same:

- **No NAT Gateway / no ALB** — EC2 & RDS in public subnets (security groups enforce isolation, not network topology); a single instance needs no load balancer.
- **`sslmode=require`**, not full cert-chain validation — encrypts in transit without importing AWS's RDS CA bundle.
- **Prometheus/Grafana not deployed to AWS** — the free-tier `t3.micro` (911MB RAM) became unresponsive running both alongside the app and Redis; they work fully via docker-compose locally.
- **Refunds are full-amount only** — partial refunds would need a separate `Refund` entity; out of scope.
- **Refund approval is two-step** (request → admin decision), not automatic self-service.
