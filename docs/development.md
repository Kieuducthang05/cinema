# Development guide

English | [Tiếng Việt](development.vi.md)

For the Docker Compose quick start, see the [README](../README.md#getting-started).

## Running locally

You need JDK 21 and Docker (for PostgreSQL), or JDK 21 and your own PostgreSQL 17.

Start only the database in Docker, then run the app with Maven:

```bash
docker compose up -d db
set -a; source .env; set +a   # export the variables from .env
./mvnw spring-boot:run
```

From an IDE, run `TestCinemaApplication` in `src/test`. It starts PostgreSQL in a container through Testcontainers. Set `JWT_SECRET` and `ADMIN_PASSWORD` in the run configuration.

To run the image CI published from `main` instead of building it:

```bash
docker compose pull app
docker compose up --no-build
```

## Configuration

The service reads its configuration from environment variables. Secrets have no defaults.

| Variable | Required | Default | Description |
|---|:---:|---|---|
| `DB_URL` | | `jdbc:postgresql://localhost:5432/cinema` | JDBC URL |
| `DB_USERNAME` | | `cinema` | Database user |
| `DB_PASSWORD` | ✓ | | Database password |
| `JWT_SECRET` | ✓ | | HS256 signing key, at least 32 bytes (`openssl rand -base64 32`) |
| `ADMIN_EMAIL` | | `admin@cinema.local` | The Admin account, created at startup if missing |
| `ADMIN_PASSWORD` | ✓ | | The Admin's password, reset at every startup; change it and restart to rotate |

Without `JWT_SECRET` or `ADMIN_PASSWORD` the app refuses to start. Without `DB_PASSWORD` it fails to connect to the database. Docker Compose checks all three before it starts anything.

## Authentication

Log in to get a bearer token (valid for one hour), then send it in the `Authorization` header:

```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123","fullName":"Alice"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123"}' | jq -r .accessToken)

curl localhost:8080/api/me -H "Authorization: Bearer $TOKEN"
```

In Swagger UI, call `POST /api/auth/login`, click **Authorize** and paste the `accessToken`. The [booking walkthrough](booking-walkthrough.md) goes through a whole booking this way.

## Project structure

```
.
├── src/
│   ├── main/
│   │   ├── java/vn/edu/uet/cinema/
│   │   │   ├── api/            # REST controllers, DTOs, exception → ProblemDetail
│   │   │   ├── business/       # domain models, services, repository interfaces
│   │   │   ├── persistence/    # JPA entities, Spring Data repositories, adapters
│   │   │   └── config/         # security, bean wiring, OpenAPI
│   │   └── resources/
│   │       └── db/migration/   # Flyway migrations V1 to V9
│   └── test/                   # unit, integration, architecture and contract tests
├── loadtest/                   # k6 scenarios, seed data, Kaggle notebook
├── docs/                       # OpenAPI spec, ADRs, walkthrough, load-test baseline
├── .github/workflows/          # CI pipeline
├── Dockerfile                  # multi-stage build
└── docker-compose.yml          # app + PostgreSQL
```

Inside `persistence`, each aggregate has a Spring Data interface named `<X>EntityRepository` and an adapter `Jpa<X>Repository` that implements the business layer's `<X>Repository`.
