# Sluice

**Sluice** is an open-source project being developed into a cloud-native media infrastructure platform designed to handle complex media processing workflows at scale. 

Instead of building custom upload, queueing, storage, and media processing infrastructure for every application, Sluice provides a modular pipeline engine for orchestrating media workflows at scale. Developers will be able to simply define their processing steps, and Sluice will handle the distributed execution.

## 🚀 Key Features

- **Extensible Pipelines:** Define ordered sequences of processing steps (e.g., Resize → Watermark → Upload).
- **Asynchronous Execution:** Long-running jobs are executed asynchronously by distributed workers, ensuring the control API remains highly responsive.
- **Pluggable Processors:** Easily add new processors for Image Optimization, Format Conversion, OCR, AI Captions, and Metadata Extraction.
- **Cloud-Native by Design:** Built for Kubernetes, leveraging distributed messaging and storage.

---

## 🏗️ Target Architecture

The diagram below represents the long-term target architecture that future milestones will progressively implement. Once complete, Sluice will employ an event-driven, decoupled architecture to ensure horizontal scalability and resilience. 

*Note: The current implementation now includes synchronous asset ingestion, Azure Blob Storage, PostgreSQL, RabbitMQ, asynchronous job processing, a modular Pipeline Engine, and pluggable processors. Advanced processors, reliability features, and intelligent orchestration remain future work.*

```mermaid
graph TD
    Client[Client / Dashboard] -->|API Requests| API[Spring Boot API]
    API -->|Reads/Writes State| DB[(PostgreSQL)]
    API -->|Publishes Jobs| MQ[RabbitMQ]
    
    MQ -->|Consumes Jobs| W1[Worker Node 1]
    MQ -->|Consumes Jobs| W2[Worker Node 2]
    
    W1 <-->|Streams/Saves Assets| Storage[(Azure Blob Storage)]
    W2 <-->|Streams/Saves Assets| Storage
    W1 -->|Updates Status| DB
    W2 -->|Updates Status| DB
```

### Core Concepts
- **Asset**: A file ingested into Sluice.
- **Pipeline**: An ordered sequence of configured processing steps.
- **Processor**: A reusable unit of compute (e.g., `ImageResizeProcessor`).
- **Job**: A single execution instance of a Pipeline against an Asset.
- **Worker**: A stateless service responsible for executing processors.
- **Queue**: Coordinates asynchronous task distribution.

---

## 🛠️ Technology Stack

Designed as a modern, enterprise-grade system, Sluice leverages the following technologies:

| Domain | Technology |
|---|---|
| **Backend Core** | Java 25, Spring Boot 4, Spring Framework 7, Gradle |
| **Frontend Dashboard** | Next.js, React, TypeScript, Tailwind CSS, shadcn/ui |
| **Database & Cache** | PostgreSQL |
| **Messaging & Storage** | RabbitMQ, Azure Blob Storage |
| **Infrastructure** | Docker, Kubernetes, Terraform |
| **Observability** | OpenTelemetry, Prometheus, Grafana, Loki |
| **Testing** | JUnit, Mockito, Testcontainers |

---

## 🗺️ Implementation Roadmap

To manage complexity and optimize for learning distributed systems fundamentals, Sluice is being built incrementally.

### Phase 1: The Vertical Slice (Completed)
**Goal:** Establish the core ingestion API.
**Scope:** Upload an asset synchronously via the Spring Boot API, persist the raw file directly to Azure Blob Storage, save metadata to PostgreSQL, and return the generated Asset ID.
*Note: This phase intentionally omits queues to establish a solid baseline for asset storage.*

### Phase 2: Asynchronous Workers (Completed)
**Goal:** Introduce the execution layer.
**Scope:**
- RabbitMQ integration
- Job creation and persistence
- Asynchronous background workers
- Job lifecycle tracking
- Job status API
- Basic background processing

### Phase 3: Pipeline Orchestration (Completed)
**Goal:** Support multi-step processing workflows.
**Scope:** 
- Pipeline Engine
- Processor abstraction
- ProcessingContext
- Modular processing pipeline
- MetadataProcessor
- ChecksumProcessor
- ThumbnailProcessor
- Extensible processor architecture

### Phase 4: Resiliency & Reliability (Current)
**Goal:** Handle distributed failures gracefully.
**Scope:** Implement Dead Letter Queues (DLQs), exponential backoff retries, and idempotency guarantees across all workers.

### Phase 5: Real-time Updates & Direct Uploads
**Goal:** Optimize client performance.
**Scope:** Introduce SAS URLs for direct-to-storage client uploads (bypassing the API buffer) and Server-Sent Events (SSE) for real-time job status updates in the dashboard.

---

## 🔮 Future Vision

Sluice aims to evolve beyond a media processing platform into an intelligent media orchestration and governance platform.

Future releases will support policy-driven and AI-assisted pipeline orchestration capable of automatically selecting appropriate processing workflows based on developer intent. Example future capabilities include:

- AI-assisted pipeline generation
- Media governance
- Content moderation
- OCR and document understanding
- Face detection and anonymisation
- PII detection and redaction
- Intelligent processor selection

*(Note: These features are part of the long-term vision for the platform and are not yet implemented.)*

---

## 💻 Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/sluice-hq/sluice.git
   cd sluice
   ```
2. Start the local infrastructure (PostgreSQL, Azurite) using Docker Compose:
   ```bash
   docker-compose up -d
   ```
3. Navigate to the backend directory and run the Spring Boot API:
   ```bash
   cd backend
   ./gradlew bootRun
   ```
