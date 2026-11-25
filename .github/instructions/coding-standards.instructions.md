---
applyTo: "**/*.java"
---

<!-- Managed by dev-standards (stacks/java-spring/instructions/coding-standards.instructions.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Java coding standards

- Use Java 21 and the project's existing Spring Boot, Maven and library conventions.
- Prefer small, cohesive classes and methods with explicit types and clear names.
- Keep controllers limited to HTTP mapping, boundary validation, service delegation and response
  handling.
- Use constructor injection with `private final` dependencies. Use Lombok
  (`@RequiredArgsConstructor`, `@Slf4j`) only where the project and nearby code already use it.
- Validate external input at the boundary with Jakarta Bean Validation and explicit constraints;
  keep use-case rules in the service layer.
- Preserve type safety. Avoid unchecked casts, `Object`-typed APIs, untyped `Map`/`JsonNode`
  responses and unnecessary abstractions.
- Handle expected failures explicitly. Do not swallow exceptions, add broad catches, or return
  empty success-shaped values for errors.
- Keep credentials outside source-controlled files, tests, fixtures and Postman files; use
  environment variables or ignored local files.
- Put each external API behind a provider-specific client and map its responses to typed models.
- Keep endpoint changes in sync across the controller, OpenAPI annotations, MockMvc tests, the
  Postman collection and the README when the public API changes.
- Keep package names descriptive, lowercase and aligned with source directories.
- Run the smallest relevant existing Maven test/build command after changes.
