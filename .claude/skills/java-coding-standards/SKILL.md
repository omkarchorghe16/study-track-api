---
name: java-coding-standards
description: Coding, naming, architecture, logging and testing standards for this Spring Boot repository. Use before writing, changing, reviewing or renaming any Java code, schema migration, REST endpoint, provider client, scheduler or notifier.
---

<!-- Managed by dev-standards (stacks/java-spring/claude-skills/java-coding-standards/SKILL.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Java coding standards

This skill applies the repository's rules to Claude Code. The rules live in `.github/` and are
shared with GitHub Copilot, so there is one source of truth. Do not copy them here.

## 1. Load the rules

Read these before writing code:

- `.github/copilot-instructions.md`: repository overview and rules.
- Every `.github/instructions/*.instructions.md` file whose `applyTo` matches the files you will
  change. For Java that is at least `change-hygiene`, `coding-standards`,
  `naming-and-readability`, `architecture`, `spring-boot`, `logging` and `project`.
  `project.instructions.md` holds this repository's packages, reference classes and exceptions,
  and wins where it is more specific.

Then read the task-specific workflow from `.github/skills/`:

| Task | Skill |
|---|---|
| New or changed endpoint, DTO, status code | `spring-boot-rest-api` |
| Entity, repository, schema migration, transaction | `spring-boot-jpa` |
| New package, layer or cross-cutting design | `spring-boot-architecture` |
| Logging or error handling | `spring-boot-logging` |
| Third-party HTTP provider | `spring-boot-provider-integration` |
| Tests | `spring-boot-testing` |

Also read any other skill in `.github/skills/` whose description matches the task; repositories
add their own (for example scheduling and notifications).

## 2. Look at nearby code

Open the closest existing controller, service, client and test for the same feature (the reference
classes in `project.instructions.md`) and match them.

## 3. Write the code

- Put code in the feature's or provider's own classes; create `<Resource>Controller`,
  `<Resource>Service` or `<Provider><Resource>Client` instead of adding to unrelated ones.
- Controller -> service -> client or repository, never controller -> client or repository.
- Tests never call live providers or a developer's database.

## 4. Check before finishing

- [ ] Names and layout follow `naming-and-readability.instructions.md`.
- [ ] No one-line method bodies, single-letter parameters, generic class names or untyped
      (`JsonNode`, `Map`) responses in new endpoints.
- [ ] No credentials, tokens, credential-bearing URLs or payloads in code, tests, Postman or logs.
- [ ] Tests added or updated, and `mvn test` passes (actually run).
- [ ] README, OpenAPI, Postman and config examples updated when the API or behavior changed.
- [ ] Every affected `.github/skills/*` file updated.
- [ ] The project-specific checks in `project.instructions.md` are done.
- [ ] Shared files (marked `Managed by dev-standards`) were not edited here; shared rule changes go
      to the `dev-standards` repository.
