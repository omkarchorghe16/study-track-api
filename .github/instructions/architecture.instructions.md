---
applyTo: "**/*.java"
---

<!-- Managed by dev-standards (stacks/java-spring/instructions/architecture.instructions.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Architecture standards

- Keep the application layered: `controller` handles HTTP; `service` (or a feature package)
  coordinates use cases and transactions; provider clients call external APIs; `model` and
  `repository` own JPA entities and database access.
- Controllers must not call repositories or provider clients directly; delegate through a service.
- Keep dependencies one-way: controllers -> services -> clients/repositories. Avoid cycles between
  web, service, client and persistence layers.
- Keep provider-specific request building, response parsing and error translation in that
  provider's client.
- Keep JPA entities focused on persistence, not HTTP behavior or orchestration. Keep public
  transport DTOs separate from entities.
- Put transaction boundaries on service workflows, not controllers or repositories. Do not hold a
  database transaction open across slow provider or notification calls unless atomicity needs it.
- Normalize and validate identifiers (for example stock symbols) once at the service boundary.
- Bind configuration through typed `@ConfigurationProperties` classes and document defaults and
  environment variable names in `application.yml`. Never hard-code credentials.
- PostgreSQL is the application datasource; H2 is for tests only. Do not add a production H2
  mirror, another datasource, migration framework, security mechanism or new architecture pattern
  without an explicit decision.
- Keep schema changes explicit. If the project uses Flyway, every schema change is a new migration.
- Keep provider schemas and persistence tables separate when fields or units differ.
- Docker Compose is the default local container workflow. Kubernetes manifests, when present, are
  optional and local-only; never commit Kubernetes Secrets or local credentials.
- Keep `README.md` and relevant API, configuration and operational documentation synchronized with
  implemented behavior. Public API changes also need synchronized OpenAPI and Postman examples.
