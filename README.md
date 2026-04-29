# CodeSage

CodeSage is a Spring Boot code-intelligence service for Java repositories. It ingests CodeQL-derived exports, materializes a queryable graph, and exposes deterministic APIs for code search, trace, blast radius, endpoints, and semantic summaries.

## Scope

CodeSage is the **code intelligence core**.

It is intentionally **not** the multi-source orchestration layer for Jira, Confluence, incidents, or broad enterprise knowledge. Those belong in a separate orchestrator that calls CodeSage through stable APIs.

## What is included

- Spring Boot 4.0 application
- Azure OpenAI provider wiring behind a narrow answer-generation seam
- PostgreSQL + Flyway schema
- pgvector dependency for future vector retrieval work
- Graph model for symbols, call edges, endpoints, touchpoints, and persistence flow
- CSV ingestion pipeline for CodeQL exports
- REST APIs for graph queries, search, semantic docs, and code-focused chat

## What is intentionally not included

- Jira ingestion and synchronization
- Confluence ingestion and synchronization
- Cross-source reasoning or orchestration
- Atlassian tenant configuration

## Required environment variables

Database:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Azure OpenAI:

- `AZURE_OPENAI_ENDPOINT`
- `AZURE_OPENAI_API_KEY`
- `AZURE_OPENAI_API_VERSION`
- `AZURE_OPENAI_CHAT_DEPLOYMENT`
- `AZURE_OPENAI_EMBEDDING_DEPLOYMENT`

Optional toggles:

- `CODESAGE_AI_ENABLED=true`
- `CODESAGE_AI_MAX_KNOWLEDGE_RESULTS=8`
- `CODESAGE_DATA_DIR=./data`

## Expected input files

Place CSV exports under `./data` by default:

- `symbols.csv`
- `call_edges.csv`
- `api_endpoints.csv`
- `touchpoints.csv`
- `persistence_flow.csv`

Headers are expected in row 1.

## Run locally

1. Create PostgreSQL database `codesage`
2. Export the environment variables above
3. Start the app

```bash
mvn spring-boot:run
```

Default port:

```text
8090
```

## Ingestion

```bash
curl -X POST 'http://localhost:8090/api/ingestion/run-full?baseDir=./data&projectKey=my-project'
```

## Query examples

```bash
curl 'http://localhost:8090/api/search?projectKey=my-project&q=PaymentService'
curl 'http://localhost:8090/api/graph/callers?projectKey=my-project&method=com.acme.PaymentService.retryPayment'
curl 'http://localhost:8090/api/graph/callees?projectKey=my-project&method=com.acme.PaymentService.retryPayment'
curl 'http://localhost:8090/api/graph/blast-radius?projectKey=my-project&method=com.acme.PaymentService.retryPayment&depth=3'
curl 'http://localhost:8090/api/graph/describe?projectKey=my-project&qualifiedName=com.acme.PaymentService'
```

## Chat example

```bash
curl -X POST 'http://localhost:8090/api/chat/ask' \
  -H 'Content-Type: application/json' \
  -d '{"projectKey":"my-project","question":"What endpoints call com.acme.PaymentService.retryPayment?"}'
```

## Notes

- Chat is grounded in CodeSage retrieval results. If AI generation is unavailable, the API returns a deterministic evidence-based response.
- Keep secrets out of the repository. Use environment variables or an ignored local env file.
- Treat CodeSage as a bounded context. Build Jira, Confluence, and other knowledge layers in a separate orchestrator.
