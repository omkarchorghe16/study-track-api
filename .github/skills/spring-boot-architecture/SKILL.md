---
name: spring-boot-architecture
description: Plan or change application architecture, layer and package boundaries, cross-cutting behavior, persistence topology or deployment topology in a Spring Boot service.
---

<!-- Managed by dev-standards (stacks/java-spring/skills/spring-boot-architecture/SKILL.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Spring Boot architecture workflow

Use this skill when a change spans several layers or packages, moves responsibilities, changes
application-wide conventions, affects deployment topology or introduces a new subsystem. The
repository's current boundaries (packages, shared database, deployment) are described in
`.github/instructions/project.instructions.md`.

## Architecture rules

- Preserve one-way dependencies: controllers -> services/features -> clients/repositories. Keep
  infrastructure responsibilities out of controllers and entities.
- Keep provider transport and response interpretation behind provider clients, and channel
  transport (Slack, Telegram, email) behind notifier components.
- Use typed `@ConfigurationProperties` for settings. Keep credentials out of tracked files and
  redact them from logs and error responses.
- Put transaction boundaries at the service layer and keep slow network calls outside them unless
  atomicity requires otherwise.
- Treat quotas, timeouts, retries, caching, idempotency and failure behavior as explicit design
  constraints.
- Keep public DTOs distinct from JPA entities.
- When two services share one database, keep table names unique across them and never alter or
  map the other service's tables.
- Keep a scheduler-bearing service at one instance unless scheduled work is coordinated across
  instances.
- Do not introduce another datasource, production H2 mirror, migration framework, security
  mechanism or architecture pattern without confirming the trade-off with the owner.

## Implementation sequence

1. Trace the end-to-end flow through the affected controllers, services, clients, configuration,
   models, repositories, tests and docs before editing.
2. Name the existing boundary and the smallest layer that owns the requested behavior; keep the
   change there and avoid unrelated refactoring.
3. Identify impacts on configuration, schema, transactions, public API, secrets, scheduling,
   provider limits and observability.
4. Implement the smallest coherent change and add tests at the affected boundaries.
5. Review every skill in `.github/skills/` and update the affected ones.
6. Update `README.md` and directly related API, configuration, architecture or deployment docs.
7. Run the smallest relevant tests/build and report any decision that needs owner input.

## Review checklist

- Responsibilities and package placement match the existing layers.
- Configuration is typed and safe by default.
- Schema and transaction semantics are explicit; PostgreSQL remains authoritative.
- Network failures, quotas and external side effects have defined behavior.
- Tests cover the boundaries without live external dependencies.
- README, docs and skills match the code; no secrets are exposed.
