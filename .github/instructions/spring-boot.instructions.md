---
applyTo: "**/*.java"
---

<!-- Managed by dev-standards (stacks/java-spring/instructions/spring-boot.instructions.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Spring Boot standards

- Use the existing Java 21, Spring Boot 3.x and Maven dependency-management setup. Use existing
  dependencies unless a requirement cannot be met otherwise.
- Use the narrowest Spring stereotype (`@RestController`, `@Service`, `@Component`, `@Repository`)
  and constructor injection.
- Bind runtime configuration through Spring properties; do not read environment variables from
  business logic.
- Map request bodies to typed DTOs and validate them with `@Valid` and Jakarta Validation.
- Return intentional HTTP status codes and let the project's established exception handler shape
  error responses; do not hide failures.
- Keep JPA mappings and repository methods consistent with the actual schema. Make upserts explicit
  and safe for repeated requests.
- Keep blocking provider calls within configured timeouts and enforce provider rate limits before
  issuing calls. Count durable daily quotas from persisted data so restarts do not reset them.
- Document every REST endpoint with springdoc OpenAPI annotations and keep `/v3/api-docs`, Swagger
  UI and the Postman collection accurate. Never put credentials in a Postman collection or
  environment export.
- Test web mappings with MockMvc and isolate provider and database behavior. Tests must not depend
  on a developer's PostgreSQL server or a live external API.
- Configuration and CORS changes take effect only after the service restarts.
