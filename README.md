# Sluice

Sluice is an API-first media processing platform. Developers will create versioned processing pipelines, upload media, start runs from their own applications, and inspect durable outputs and processing data. The dashboard is the control surface for people; the public API is the main product.

The repository is currently at the foundation stage. Account signup/login, projects, project-scoped API keys, asset ingestion, asynchronous jobs, and an early processor engine exist. The V1 processor market, safe pipeline builder, production compression, governance, complete run API, observability, and Azure deployment are still being built.

## Stack

- Java 17 and Spring Boot 4 API
- Next.js 16, React 19, and TypeScript dashboard
- PostgreSQL, RabbitMQ, and Azure Blob Storage (Azurite locally)
- Flyway database migrations, Gradle, Docker Compose, and Testcontainers

## Prerequisites

- Java 17
- Node.js 20 or newer with npm
- Docker Desktop, with the Docker engine running

Docker is required both for the local services and for backend integration tests. The tests create their own temporary PostgreSQL container and never clean the development database.

## Run locally

From the repository root, start PostgreSQL, RabbitMQ, and Azurite:

```powershell
docker compose up -d
```

In a second terminal, start the API:

```powershell
cd backend
.\gradlew.bat bootRun
```

In a third terminal, start the dashboard:

```powershell
cd frontend
npm ci
npm run dev
```

Open [http://localhost:3000/signup](http://localhost:3000/signup), create an account, and use the generated project through the dashboard. The API base path is `http://localhost:8080/api/v1`. RabbitMQ management is available at [http://localhost:15672](http://localhost:15672) with the image's local default credentials.

Stop local infrastructure without deleting its volumes:

```powershell
docker compose down
```

## Verification

Run the backend suite from `backend`:

```powershell
.\gradlew.bat test --rerun-tasks --console=plain
```

The default suite starts disposable PostgreSQL through Testcontainers. It disables real RabbitMQ listeners, scheduled recovery, and Azure Blob initialization. Future tests that deliberately require real broker or storage services use the `external-integration` JUnit tag and run separately:

```powershell
.\gradlew.bat externalIntegrationTest --console=plain
```

Run frontend checks from `frontend`:

```powershell
npm run lint
npm run build
```

## Configuration

Local defaults work with `docker-compose.yml`. Override them through environment variables; do not commit real credentials.

| Variable | Purpose | Local default |
|---|---|---|
| `SLUICE_DB_URL` | API PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/sluice` |
| `SLUICE_DB_USERNAME` | API database user | `sluice` |
| `SLUICE_DB_PASSWORD` | API database password | `sluice_password` |
| `SLUICE_FLYWAY_URL` | Optional Flyway JDBC URL | API database URL |
| `SLUICE_FLYWAY_USERNAME` | Optional Flyway user | API database user |
| `SLUICE_FLYWAY_PASSWORD` | Optional Flyway password | API database password |
| `AZURE_STORAGE_CONNECTION_STRING` | Blob/Azurite connection | Local Azurite account |
| `AZURE_STORAGE_CONTAINER_NAME` | Asset container | `assets` |
| `AZURE_STORAGE_CONFIGURE_CORS` | Configure storage CORS on startup | `true` locally |
| `SLUICE_CORS_ALLOWED_ORIGINS` | Browser origin allowed by API/storage | `http://localhost:3000` |
| `SLUICE_JWT_SECRET` | Signs dashboard access tokens | Development-only fallback |
| `API_BASE_URL` | Server-side backend URL used by the Next.js BFF | `http://localhost:8080/api/v1` |

Production must provide a strong `SLUICE_JWT_SECRET` and managed database, storage, and broker credentials. The checked-in defaults are only for local development.

## Current safety boundaries

- Resources and API keys are scoped to a project.
- Dashboard tokens and selected-project state are held in HttpOnly cookies by the Next.js backend-for-frontend layer, not browser storage.
- API keys are shown once and stored as hashes.
- Default integration tests use an isolated database.
- Arbitrary user-uploaded processor code is intentionally outside V1; V1 will expose curated, versioned processors with compatibility and resource limits.

Azure Container Apps is the V1 deployment target. Azure deployment is part of the final demo, after the local product gate passes.
