---
name: spring-boot-provider-integration
description: Add or change a third-party HTTP provider integration (market data, news, notification APIs) in a Spring Boot service, with typed parsing, bounded timeouts, rate limits, safe errors and tests.
---

<!-- Managed by dev-standards (stacks/java-spring/skills/spring-boot-provider-integration/SKILL.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# External provider integration workflow

Use this skill for market-data, news, notification or any other remote HTTP API. The providers this
repository already integrates, and their specific behavior, are described in
`.github/instructions/project.instructions.md` and any project-local skill.

## Conventions

- Keep provider HTTP and response parsing in that provider's client package/class; keep
  orchestration, normalization and persistence in a service.
- Reuse the project's configured `RestClient`/HTTP bean with its timeouts. Never create a client
  with unbounded timeouts.
- Bind provider URLs, credentials, limits and feature switches through typed configuration
  properties and document names and defaults in `application.yml`. Keep real values in environment
  variables or ignored local secrets files; never overwrite or relocate a developer's local secrets
  file, and keep tracked profile files free of credentials.
- Normalize symbols once at the service boundary with the existing normalizer.
- Enforce rate limits before issuing requests; keep durable quotas in persisted usage records so a
  restart does not reset them.
- Map responses to typed models. Translate provider errors close to the client and propagate them;
  never return empty success-shaped data for an upstream failure.
- Replace stored downloaded data (files or rows) only after the new data has been fully parsed and
  validated, so a failed download keeps the previous copy.
- Never log credentials, authorization headers, credential-bearing URLs, raw provider responses or
  unsanitized exception messages that can contain them.

## Implementation sequence

1. Inspect the provider's properties, client, service, persistence model, tests and documented API
   limits.
2. Add configuration with safe defaults and no committed credentials.
3. Implement request construction, response parsing and error mapping in the client.
4. Put normalization, caching/quota checks and persistence in the service; keep slow network calls
   outside database transactions unless required.
5. Add deterministic tests with `MockRestServiceServer`, WireMock or service mocks: success parsing,
   empty/malformed responses, HTTP failures, missing configuration and quota boundaries.
6. Update OpenAPI, Postman and README if the integration is reachable through a public endpoint.
7. Run the provider tests, then compile/package.
8. Review every `.github/skills/*/SKILL.md` and update the affected ones and related docs.

## Verification checklist

- Timeout and retry behavior are bounded and intentional.
- Rate limits and caching match the provider plan and survive restarts when meant to be durable.
- Failures are actionable and do not expose upstream payloads or credentials.
- Tests verify mapping and error cases without network access.
