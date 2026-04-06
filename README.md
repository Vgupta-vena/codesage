# CodeSage

A Spring Boot + Spring AI starter project for building a Java code-intelligence layer on top of source metadata and CodeQL exports.

## What is included

- Spring Boot 3.5 app
- Spring AI chat wiring with tool calling
- PostgreSQL + Flyway schema
- pgvector starter dependency
- Graph model for symbols, call edges, endpoints, touchpoints, and persistence flow
- CSV ingestion pipeline for CodeQL exports
- REST APIs for graph queries and chat
- Placeholder semantic enrichment service hook

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
2. Set environment variables:
   - `OPENAI_API_KEY`
3. Update `src/main/resources/application.yml` as needed
4. Start the app

```bash
mvn spring-boot:run
```

## Ingestion

```bash
curl -X POST 'http://localhost:8080/api/ingestion/full?baseDir=./data'
```

## Query examples

```bash
curl 'http://localhost:8080/api/graph/callers?method=com.acme.PaymentService.retryPayment'
curl 'http://localhost:8080/api/graph/callees?method=com.acme.PaymentService.retryPayment'
curl 'http://localhost:8080/api/graph/blast-radius?method=com.acme.PaymentService.retryPayment&depth=3'
curl 'http://localhost:8080/api/graph/describe?qualifiedName=com.acme.PaymentService'
```

## Chat example

```bash
curl -X POST 'http://localhost:8080/api/chat/ask' \
  -H 'Content-Type: application/json' \
  -d '{"question":"Who called com.acme.PaymentService.retryPayment"}'
```

## Notes

- This project is a direct-import starter, not a polished product. You will likely want to refine CSV headers to match your CodeQL export format.
- The semantic enrichment component is intentionally lightweight and ready for your model/provider configuration.
