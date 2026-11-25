---
name: spring-boot-testing
description: Design and run focused JUnit 5 tests for a Spring Boot service, including MockMvc controller tests, provider HTTP-client tests and H2-backed persistence tests.
---

<!-- Managed by dev-standards (stacks/java-spring/skills/spring-boot-testing/SKILL.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Spring Boot testing workflow

Use this skill when adding behavior, fixing a regression, changing an endpoint or validating a
provider or database workflow. Reference test classes for this repository are listed in
`.github/instructions/project.instructions.md`.

## Conventions

- Use the existing Maven test stack (JUnit 5, Spring Boot Test, Mockito, MockMvc, H2, and WireMock
  or `MockRestServiceServer` where present). Do not add a test framework for routine coverage.
- Controller tests use `@WebMvcTest`, MockMvc and a mocked service. Assert status, JSON contract,
  headers, validation and delegation.
- Provider client tests isolate HTTP with `MockRestServiceServer` or WireMock. Tests never need live
  credentials or internet access.
- Persistence tests use the `test` profile and H2; they never need a developer's PostgreSQL. Cover
  constraints, relationships and transaction behavior where relevant.
- When changing a central logging aspect, test that results and exceptions pass through unchanged.
- When a route moves to another controller, move its tests and remove the old route from docs.
- Fixtures are synthetic: no real tokens, API keys, personal data or full live provider payloads.
- Postman requests against a running app can have real side effects; tests must not depend on them.

## Implementation sequence

1. Find nearby tests and follow their naming, package and setup conventions.
2. Test at the responsible boundary: controller contract, service rule, provider mapping or
   persistence behavior. Load the whole application only when the integration is under test.
3. Cover the normal path and the most important invalid/error path.
4. Assert externally observable results, not private implementation details.
5. Run the affected classes first (`mvn -q -Dtest=FooControllerTest test`), then `mvn test`.
6. When a test fails, tell a product regression apart from an environment or setup problem; never
   weaken an assertion just to pass.
7. Update affected skills, README and docs when behavior or the test workflow changes.

## Verification checklist

- Tests are deterministic and isolated from external systems.
- New public API behavior has MockMvc coverage.
- Changed provider behavior has mapping and failure coverage.
- The targeted Maven command exited successfully before the change is reported as verified.
