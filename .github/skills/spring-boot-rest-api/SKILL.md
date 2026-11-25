---
name: spring-boot-rest-api
description: Implement or change REST endpoints in a Spring Boot service, including DTO validation, status codes, OpenAPI documentation, MockMvc tests and Postman requests.
---

<!-- Managed by dev-standards (stacks/java-spring/skills/spring-boot-rest-api/SKILL.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Spring Boot REST API workflow

Use this skill when adding or changing HTTP routes, request/response contracts, status codes or
controller tests. The repository's controller/DTO packages, exception handler and Postman
collection path are listed in `.github/instructions/project.instructions.md`.

## Conventions

- Keep controllers limited to HTTP mapping, boundary validation, service delegation and response
  construction. Never call repositories or provider clients from a controller.
- Use typed request/response DTOs and Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@NotNull`,
  `@NotEmpty`, `@Size`). Do not duplicate business rules in the controller.
- Use explicit status codes: `201 Created` with a body and `Location` header for creates,
  `204 No Content` for deletes, unless the existing API already does otherwise.
- Let the project's exception handler produce error responses; do not build error bodies in
  controllers.
- Document endpoints with springdoc `@Operation`, `@ApiResponse(s)` and `@Tag`.
- Keep the controller method name the same as the service method it calls.

## Implementation sequence

1. Inspect the controller, service, DTOs, exception handler, OpenAPI setup, Postman collection and
   nearby tests.
2. Define or update DTO validation; keep use-case rules in the service.
3. Add or update the mapping and its OpenAPI documentation.
4. Add `@WebMvcTest`/MockMvc tests for status, body, headers, validation failures and service
   delegation. Mock the service, not its repositories.
5. Update the Postman collection (use variables such as `{{url}}`, no secrets) and the README for
   public contract changes.
6. Run the affected controller tests, then a Maven package/compile if the change reaches beyond the
   test slice.
7. Review every `.github/skills/*/SKILL.md` and update the affected ones. If a frontend calls the
   endpoint, check and update its client code too.

## Verification checklist

- Route and method are correct and do not conflict with sibling mappings.
- Required values are validated at the HTTP boundary; service rules still hold for non-HTTP callers.
- Success and failure status codes match the API behavior.
- OpenAPI, Postman and README describe the actual DTO fields and constraints.
- Tests do not call live databases or external providers.
- No credentials or sensitive values are added to logs or fixtures.
