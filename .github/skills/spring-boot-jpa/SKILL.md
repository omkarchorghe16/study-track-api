---
name: spring-boot-jpa
description: Add or change JPA entities, Spring Data repositories, schema migrations, transactions and database-backed services in a PostgreSQL-backed Spring Boot service.
---

<!-- Managed by dev-standards (stacks/java-spring/skills/spring-boot-jpa/SKILL.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Spring Data JPA workflow

Use this skill when changing persisted data, repository queries, schema or transactional service
workflows. How this repository manages its schema (Flyway or Hibernate `update`), its packages and
any shared-database rules are in `.github/instructions/project.instructions.md`.

## Conventions

- PostgreSQL is the application datasource (`src/main/resources/application.yml`); H2 is for tests
  only.
- Entities live in the `model` package, Spring Data repositories in `repository`, and transaction
  boundaries in services.
- Flyway projects: every schema change is a new `V<n>__<description>.sql`; never edit an applied
  migration, never rely on Hibernate schema creation, and mirror the change in the H2 test schema
  if the project keeps one.
- Hibernate `update` projects: `update` cannot add a `NOT NULL` column to a non-empty table; add
  such columns with a manual backfill first.
- Preserve existing entity, table and column names. Keep provider-specific records separate when
  their schemas or units differ.
- Keep HTTP concerns out of entities and repositories.
- Use `@Transactional(readOnly = true)` for reads and `@Transactional` for mutations or multi-step
  workflows that need one transaction.
- Avoid N+1 queries when mapping entity relations to response DTOs, and never return lazy entities
  to the web layer.

## Implementation sequence

1. Inspect the entities, repositories, services, datasource configuration, migrations and tests
   that touch the data.
2. Make schema and relationship changes explicit: nullability, uniqueness, foreign keys, indexes,
   cascade and lifecycle behavior.
3. Put query selection and business decisions in the service; keep repository methods focused on
   data access.
4. Add focused tests with the test profile and H2; do not require a local PostgreSQL.
5. If the data is exposed over HTTP, update DTOs, OpenAPI, MockMvc tests, Postman and README.
6. Run targeted tests and a Maven package/compile.
7. Review every `.github/skills/*/SKILL.md` and update the affected ones and related docs.

## Verification checklist

- Production uses PostgreSQL; tests use the test configuration.
- Transaction boundaries cover the intended writes without wrapping network calls unnecessarily.
- Queries match the schema and do not leak lazy entities.
- Duplicate, missing-reference and not-found behavior is explicit.
- No cross-database mirroring or distributed atomicity is assumed.
