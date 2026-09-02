# Payments API

A Spring Boot payments service with JWT (RS256) authentication, role-based authorization, refund workflows, webhook processing, and analytics — built as a portfolio project to demonstrate both backend engineering and DevOps practice, from application code through containerization, CI/CD, cloud infrastructure (as code, via Terraform), and observability.

**Live demo:** `http://18.153.65.216:8080/swagger-ui.html`
> Runs on a free-tier AWS EC2 instance without an Elastic IP, so the public IP can change if the instance is stopped/restarted. If the link doesn't resolve, the environment may be temporarily torn down for cost control — see [Deployment](#deployment) to spin it back up in a few minutes via Terraform.

---

## Overview

This service simulates a real-world payment processing backend: initiating payments, tracking their lifecycle, handling refunds through a request/approval workflow, receiving webhook callbacks from a (simulated) external payment provider, and exposing operational features like rate limiting, caching, analytics, and data export. The goal was to take a backend service beyond "runs on my machine" — all the way to a real, monitored, CI/CD-driven cloud deployment, provisioned entirely as code.

## Features

- **Payments** — initiate payments (with idempotency support), look up by ID, filter/paginate, export to CSV
- **Refunds** — customer-initiated refund requests, admin approval/rejection (two-step, not a single unmoderated action)
- **Analytics** — admin-only volume/status reporting over a date range
- **Auth** — registration, login, refresh tokens, JWT signed with an RSA key pair
- **User management** — profile updates, password change, account deactivation/reactivation/deletion
- **Webhooks** — signed payment webhook ingestion (`X-Signature` header, HMAC-SHA256), simulating an external payment provider
- **Rate limiting** — Bucket4j backed by Redis, correct even across multiple app instances
- **Idempotency** — Redis-backed idempotency (`Idempotency-Key` header), with a content-based fallback for requests that omit it
- **Caching** — Redis-backed caching for single-payment lookups, with invalidation on state changes
- **Observability** — Actuator health/info + Prometheus metrics, with a Grafana dashboard (local development, via docker-compose)
- **Audit trail** — `createdBy` / `lastModifiedBy` / timestamps on every record, via Spring Data JPA auditing

## Tech Stack

- Java 21, Spring Boot 4
- Spring Web, Spring Security, Spring Data JPA
- PostgreSQL + Liquibase migrations
- Redis (rate limiting / caching / idempotency) via Lettuce
- JJWT (RS256), springdoc-openapi (Swagger UI)
- Testcontainers, JUnit 5, Mockito
- Docker (multi-stage build, custom `jlink`-trimmed JRE)
- GitHub Actions (CI/CD), Terraform (AWS infrastructure as code), Prometheus + Grafana

---

## Getting Started

### Prerequisites
- JDK 21
- Docker (for Postgres/Redis/Prometheus/Grafana via docker-compose)

### Run locally
```bash
# start Postgres, Redis, Prometheus, Grafana
docker-compose up -d

# run the app (applies Liquibase migrations on startup)
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. Two users are seeded by Liquibase for local development — see `db/changelog` for details; replace these before using this outside local development.

### API docs
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`

### Monitoring (local development)
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin)
- Actuator: `http://localhost:8080/actuator/health`, `/actuator/prometheus`

> Prometheus/Grafana are only run locally via docker-compose — see [Design Decisions & Trade-offs](#design-decisions--trade-offs) for why they're not part of the live AWS deployment.

### Configuration

Key environment variables (see `src/main/resources/application.yaml`):

| Variable | Default | Purpose |
|---|---|---|
| `PUBLIC_URL` | `http://localhost:8080` | Public base URL used in the OpenAPI spec |
| `DB_URL` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `payments` | PostgreSQL connection |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | PostgreSQL credentials |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | *(reads from `src/main/resources/keys/`)* | JWT signing keys — env vars take priority when set, so the same image works locally and in production without rebuilding |
| `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | `localhost` / `6379` / — | Redis connection |

Webhook signature verification uses the `webhook.secret` property. JWT keys and all credentials are injected via environment variables at container runtime — never baked into the Docker image or committed to source control.

---

## API Overview

All endpoints are under `/api/v1`. JWT bearer auth is required except for `/api/v1/auth/**` and `/api/v1/webhooks/**`.

### Auth (`/api/v1/auth`)
| Method | Path | Description |
|---|---|---|
| POST | `/login` | Authenticate, returns access + refresh tokens |
| POST | `/register` | Register a new customer |
| POST | `/refresh` | Exchange a refresh token for a new access token |

### Payments (`/api/v1/payments`) — CUSTOMER or ADMIN unless noted
| Method | Path | Description |
|---|---|---|
| POST | `/` | Initiate a payment (supports `Idempotency-Key` header) |
| GET | `/{id}` | Get a payment by ID |
| POST | `/all` | Filtered, paginated payment search |
| POST | `/exportCsv` | Export filtered payments as CSV |
| POST | `/{id}/requestRefund` | Request a refund |
| POST | `/{id}/refundDecision` | ADMIN — approve/reject a refund |
| GET | `/analytics` | ADMIN — volume/status analytics for a date range |

### Users (`/api/v1/users`) — authenticated
| Method | Path | Description |
|---|---|---|
| PATCH | `/` | Update profile |
| POST | `/password` | Change password |
| PATCH | `/deactivate` | Deactivate own account |
| PATCH | `/reactivate` | Reactivate own account |
| DELETE | `/` | Delete own account |

### Webhooks (`/api/v1/webhooks`) — public, signature-verified
| Method | Path | Description |
|---|---|---|
| POST | `/payment` | Receives payment status updates; requires `X-Signature` header |

---

## Testing

```bash
./mvnw test
```

Unit tests cover controllers and services (mocked dependencies). Integration tests (`src/test/java/.../integration`) use Testcontainers to spin up a real PostgreSQL and Redis instance and exercise the actual HTTP layer end-to-end.

---

## Deployment

The `Dockerfile` builds a slim runtime image via a multi-stage `jlink` build — the final image ships only a custom-trimmed JRE (no Maven, no full JDK, no build toolchain).

`/infra` holds Terraform for AWS deployment — fully as code, no manual console configuration:

- VPC with public subnets across two availability zones
- Security groups forming a trust chain: internet → EC2 (app port + SSH, IP-restricted) → RDS (Postgres, EC2-only — nothing else can reach the database)
- RDS PostgreSQL (`db.t3.micro`, free-tier eligible), SSL-required connection
- EC2 instance (`t3.micro`, free-tier eligible) running the app + Redis as Docker containers

```bash
cd infra
terraform init
terraform apply     # requires terraform.tfvars with db_password - see terraform.tfvars.example
```

Tear down when not in active use, to stay within free tier / avoid ongoing cost:
```bash
terraform destroy
```

### CI/CD

GitHub Actions runs on every push/PR to `master`: run the full test suite (unit + integration, via Testcontainers) then build the Docker image and push to Docker Hub.

---

## Design Decisions & Trade-offs

This project intentionally documents the reasoning behind a few deliberate simplifications, since the "right" production choice and the right choice for a cost-conscious learning project aren't always the same:

- **No NAT Gateway.** EC2 and RDS sit in public subnets rather than private ones, with security groups, not network isolation, enforcing that only the app can reach the database. Avoids the ~$32/month NAT Gateway cost. A production deployment would use private subnets for the database tier.
- **No Application Load Balancer.** With a single EC2 instance, an ALB adds no functional value and costs ~$16 to $20/month to run continuously. A natural addition once this scales to multiple instances or migrates to ECS/Fargate.
- **RDS connection uses `sslmode=require`** without full certificate-chain validation, rather than importing AWS's RDS CA bundle into the JVM truststore. Encrypts traffic in transit without the added certificate-management complexity, appropriate for this project's scope.
- **Prometheus + Grafana are not part of the live AWS deployment.** They're fully working locally via docker-compose, but adding both to the free-tier `t3.micro` EC2 instance (911MB total RAM) alongside the app and Redis was tested directly and caused the instance to become unresponsive under memory pressure. A production deployment would use a larger instance or a managed service (Amazon Managed Prometheus / Managed Grafana) rather than self-hosting monitoring on the same constrained instance as the application.
- **Refunds are full-amount only.** Partial refunds would require a separate `Refund` entity to track partial-refund history against a single payment; out of scope here.
- **Refund approval is two-step (request then admin decision), not automatic.** An unmoderated single-action refund would let customers refund themselves without any actual approval process.

## Roadmap

- Kubernetes deployment (local cluster first, then a managed service)
- Terraform modules + remote state
- ALB + multi-instance / ECS Fargate for real horizontal scaling
- Proper RDS certificate validation (`sslmode=verify-full`)
- Partial refund support
