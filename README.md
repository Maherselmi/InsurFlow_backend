#  Agentic Insurance Workflow Orchestration
## Backend Documentation — Hexagonal Architecture

> Spring Boot 3 backend for the InsureFlow agentic insurance platform, built with **Hexagonal Architecture**, powered by **Spring AI**, **LangChain4j**, **Ollama** (local LLM), **RAG pipeline**, and **PostgreSQL**.

---

# Table of Contents

1. [Project Overview](#1-project-overview)
2. [Hexagonal Architecture](#2-hexagonal-architecture)
3. [Folder Structure](#3-folder-structure)
4. [Layer Details](#4-layer-details)
5. [AI & RAG Pipeline](#5-ai--rag-pipeline)
6. [Tech Stack & Dependencies](#6-tech-stack--dependencies)
7. [REST API Overview](#7-rest-api-overview)
8. [Installation & Setup](#8-installation--setup)
9. [Security](#9-security)

---

# 1. Project Overview

**InsureFlow Back** is the backend service of the agentic insurance workflow orchestration platform.

Built with **Spring Boot 3** following a strict **Hexagonal Architecture (Ports & Adapters)**, the backend ensures a clear separation between:

- business logic,
- application orchestration,
- infrastructure concerns.

The backend exposes REST APIs consumed by the Angular frontend and orchestrates AI-powered insurance workflows using:

- **Spring AI** for LLM communication,
- **LangChain4j** for the RAG pipeline,
- **Ollama** for local AI inference,
- **PostgreSQL + Milvus** for persistence and vector search,
- **Spring Security + JWT** for authentication and authorization.

---

# 2. Hexagonal Architecture

The project follows the **Hexagonal Architecture** pattern organized into three layers:

- Domain Layer
- Application Layer
- Infrastructure Layer



## Layer Responsibilities

| Layer | Responsibility |
|---|---|
| Domain | Pure business rules and entities |
| Application | Use cases and workflow orchestration |
| Infrastructure | Technical implementations and adapters |

This architecture improves:

- maintainability,
- scalability,
- testability,
- flexibility.

---

# 3. Folder Structure

```txt
src/
├── main/
│   ├── java/tn/esprit/insureflow_back/
│   │
│   │   ├── application/
│   │   │   ├── dto/
│   │   │   ├── Orchestrator/
│   │   │   └── service/
│   │   │
│   │   ├── domain/
│   │   │   ├── enums/
│   │   │   ├── model/
│   │   │   └── port/
│   │   │
│   │   ├── infrastructure/
│   │   │   ├── adapter/
│   │   │   │   ├── in/web/
│   │   │   │   └── out/
│   │   │   ├── config/
│   │   │   └── Security/
│   │   │
│   │   └── InsureFlowBackApplication.java
│   │
│   └── resources/
│       ├── application.properties
│       └── uploads/
│
└── test/
```



# 4. Layer Details

##  Domain Layer — `domain/`

The Domain layer contains the core business logic of the application.

It is framework-independent and contains:

- business entities,
- business rules,
- enumerations,
- ports/interfaces.

### Main Components

| Package | Description |
|---|---|
| `model/` | Business entities such as Claim, Client, Policy |
| `enums/` | Business enums like ClaimStatus |
| `port/` | Repository and external service contracts |

---

##  Application Layer — `application/`

The Application layer contains:

- business use cases,
- workflow orchestration,
- DTOs,
- application services.

### Main Components

| Package | Description |
|---|---|
| `dto/` | Request and response DTOs |
| `Orchestrator/` | AI workflow orchestration |
| `service/` | Business service implementations |

---

##  Infrastructure Layer — `infrastructure/`

The Infrastructure layer contains all technical implementations.

### Main Components

| Package | Description |
|---|---|
| `adapter/in/web/` | REST controllers |
| `adapter/out/` | Database, AI, and external adapters |
| `config/` | Spring and AI configuration |
| `Security/` | JWT and Spring Security configuration |

---

# 5. AI & RAG Pipeline

The platform uses a **Retrieval-Augmented Generation (RAG)** pipeline.

The pipeline allows the AI to answer using contextual insurance documents.

## Workflow

```txt
Document Upload
      │
      ▼
Document Loader
      │
      ▼
Text Splitter
      │
      ▼
Embedding Generation
      │
      ▼
Vector Store (Milvus)
      │
      ▼
Similarity Search
      │
      ▼
Prompt Assembly
      │
      ▼
LLM Inference (Ollama)
      │
      ▼
AI Response
```

## Components Used

| Component | Technology |
|---|---|
| Document Loading | LangChain4j |
| Text Splitting | LangChain4j |
| Embeddings | Ollama (`nomic-embed-text`) |
| Vector Database | PostgreSQL + Milvus |
| LLM | Ollama (`llama3`, `mistral`) |
| Prompt Orchestration | Spring AI |

---

# 6. Tech Stack & Dependencies

| Technology | Role |
|---|---|
| Java 21 | Main backend language |
| Spring Boot 3 | Backend framework |
| Spring AI | AI integration |
| LangChain4j | RAG orchestration |
| Ollama | Local LLM runtime |
| PostgreSQL | Relational database |
| Milvus | Vector similarity search |
| Spring Security | Authentication and authorization |
| JWT | Stateless authentication |
| Maven | Dependency management |
| Swagger/OpenAPI | API documentation |

---

# 7. REST API Overview

All endpoints are exposed under:

```txt
/api
```

## Main API Modules

| Module | Base Path |
|---|---|
| Authentication | `/api/auth` |
| Claims | `/api/claims` |
| Policies | `/api/policies` |
| Clients | `/api/clients` |
| Experts | `/api/experts` |
| Agents | `/api/agents` |
| Decisions | `/api/decisions` |
| AI Assistant | `/api/assistant` |
| AI Settings | `/api/ai-settings` |
| Uploads | `/api/uploads` |

Swagger documentation:

```txt
```



# 8. Installation & Setup

## Prerequisites

| Tool | Version |
|---|---|
| Java | 17+ |
| Maven | 3.9+ |
| PostgreSQL | 15+ |
| Ollama | Latest |

---













# 9. Security

The backend uses **JWT-based stateless authentication** with Spring Security.

## Security Workflow

```txt
Client Request
      │
      ▼
JWT Filter
      │
      ▼
SecurityContext
      │
      ▼
Protected REST API
```

## Security Features

- JWT Authentication
- Stateless Sessions
- BCrypt password encoding
- Protected REST APIs
- Role-based authorization


