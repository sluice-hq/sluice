# Milestone 1: The Vertical Slice - Design Document

## 1. Goal & Scope
**What it is:** Milestone 1 establishes the foundational vertical slice of the Sluice application's core ingestion API.
**Why it exists:** The primary objective is to build a solid, working backend application capable of handling end-to-end file uploads. We intentionally adopt a minimalist philosophy here: we are focusing entirely on a robust ingestion flow. To keep the scope tightly controlled, we will not introduce features from future milestones (such as RabbitMQ, caching, authentication, Kubernetes, or pipelines). Every component introduced in this milestone must directly support delivering this single vertical slice.

## 2. Architectural Design: Synchronous 3-Tier Architecture
**What it is:** The application will follow a traditional, synchronous 3-tier architecture, dividing the codebase into three primary layers:
1.  **Presentation (Controllers):** Handles HTTP requests and responses.
2.  **Business Logic (Services):** Orchestrates the upload, storage, and persistence rules.
3.  **Data Access (Repositories):** Manages interactions with the PostgreSQL database.

**Why it exists:** While future phases will introduce asynchronous workers, keeping this initial ingestion flow synchronous simplifies the mental model, ensures reliable processing for our first milestone, and establishes a stable baseline for the control plane API.

### 2.1 StorageService Abstraction
**What it is:** Rather than coupling our application directly to Azure SDKs, the `AssetService` will depend on a generic `StorageService` interface. We will then provide an `AzureBlobStorageService` that implements this interface.
**Why it exists:** This abstraction decouples our core business logic from specific cloud providers. It allows us to seamlessly swap or support future implementations—such as AWS S3, Google Cloud Storage, or Local Storage—without modifying the underlying business logic in the `AssetService`.

## 3. Package Structure: Package-by-Feature
**What it is:** We will organize our Java packages based on business capabilities (features) rather than technical layers. The structure will look like this:

```
com.sluice.api.asset
    controller/
    service/
    repository/
    domain/
    dto/

com.sluice.api.config
com.sluice.api.exception
```

**Why it exists:** Package-by-Feature scales significantly better than Package-by-Layer. As the application grows to include modules like jobs, pipelines, users, and authentication, encapsulating all related classes (Controllers, Services, Entities) within a single feature package ensures high cohesion. It prevents developers from having to navigate across the entire codebase to understand or modify a single feature.

## 4. Request Lifecycle & Validation
**What it is:** The lifecycle for an incoming asset upload request will strictly follow these steps:
1.  **Validation:** The incoming `MultipartFile` is validated to ensure it is non-empty, falls within allowed size limits, and has a supported content type.
2.  **Upload to Blob Storage:** The validated file is uploaded to Azure Blob Storage (Azurite locally) via the `StorageService`.
3.  **Persist Metadata:** The asset's metadata (e.g., storage URL, file size, content type) is saved to PostgreSQL inside a database transaction.
4.  **Map Entity to DTO:** The persisted Entity is mapped to an `AssetResponse` Data Transfer Object (DTO).
5.  **Return Response:** The API returns an HTTP `201 Created` status containing the DTO.

**Why it exists:** Validating data at the edges prevents invalid requests from wasting compute and storage resources. Mapping internal Entities to DTOs ensures that internal database schema changes do not accidentally leak or break the external API contract.

## 5. Transaction Boundaries & Failure Handling
**What it is:** The upload process spans both external network storage (Azure Blob) and a relational database (PostgreSQL). We must handle failures carefully:
-   **Transaction Boundaries:** The metadata persistence into PostgreSQL occurs strictly inside a `@Transactional` service method. However, the blob upload is an external operation and is **not** part of the database transaction.
-   **Compensation (Failure Handling):**
    -   If the blob upload fails, the process aborts immediately, and we do not attempt to write to the database.
    -   If the database persistence fails *after* a successful blob upload, we catch the database exception, perform a compensating action to delete the newly uploaded blob from storage, and then propagate the exception.

**Why it exists:** Keeping external network calls outside of database transactions prevents long-running operations from holding database connections hostage, which can exhaust connection pools. Deleting the orphaned blob upon a database failure is a simple but effective compensation strategy appropriate for Milestone 1. It ensures our system avoids storage leaks while intentionally postponing more advanced distributed consistency patterns (like the Outbox pattern) until they are truly needed.

## 6. Execution Plan: Git Feature-Branch Workflow
**What it is:** Instead of a single massive commit, we will execute this milestone using a professional Git feature-branch workflow. Work will be isolated into branches, verified, and merged into `main` via Pull Requests.
**Why it exists:** This workflow establishes healthy development habits from day one, enabling isolated testing, easier rollbacks, and a structure ready for code reviews.

### Branch Execution Order:

1.  **Branch:** `feature/docker-environment`
    *   Generate the Spring Boot project.
    *   Verify the application boots successfully.
    *   Create `docker-compose.yml`.
    *   Configure PostgreSQL and Azurite containers.
    *   Open a Pull Request and merge into `main` after manual verification.

2.  **Branch:** `feature/flyway-setup`
    *   Configure Flyway.
    *   Create the initial migration script.
    *   Verify the database schema is created correctly on application startup.
    *   Open a Pull Request and merge into `main`.

3.  **Branch:** `feature/blob-storage`
    *   Create the `StorageService` interface.
    *   Implement `AzureBlobStorageService`.
    *   Verify file uploads to the local Azurite container.
    *   Open a Pull Request and merge into `main`.

4.  **Branch:** `feature/asset-upload-api`
    *   Create the `Asset` entity and `Repository`.
    *   Implement the `AssetService`.
    *   Implement the `AssetController` and DTOs.
    *   Add request validation.
    *   Verify the complete upload flow manually using Postman.
    *   Open a Pull Request and merge into `main`.

## 7. Testing and CI/CD Strategy
**What it is:** For Milestone 1, manual verification using Postman is sufficient to prove the vertical slice works. We will not be introducing automated testing or CI/CD pipelines in this phase.
**Why it exists:** Introducing CI/CD, GitHub Actions, automated testing, and branch protection status checks distracts from proving the core architectural ingestion flow. These will be layered on in later milestones. However, adhering to the Git feature-branch and Pull Request workflow from the start ensures the repository is culturally and structurally ready for CI/CD when it is introduced.

## 8. Definition of Done
Milestone 1 is considered strictly complete only when the following conditions are met:
*   [x] `POST /api/v1/assets` successfully accepts multipart file uploads.
*   [x] Files are successfully stored in the local Azurite instance.
*   [x] Asset metadata is successfully persisted in PostgreSQL.
*   [x] Flyway automatically manages the database schema creation.
*   [x] An `AssetResponse` DTO is returned with an HTTP `201 Created` status code.
*   [x] The entire upload flow has been manually verified using Postman.
*   [x] The completed feature branch has been merged into `main`.
