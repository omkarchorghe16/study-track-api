# Repository instructions: study-track-api

Spring Boot (Java 21, Maven) API on port 8083 for StudyTrack. It owns subjects, topics, study items,
media, progress, imports and LLM settings in the `study_track` PostgreSQL database. The build plan is
in `docs/BUILD_PLAN.md`.

For every code change, follow:

- `.github/instructions/project.instructions.md`: this repository's packages, reference classes,
  Flyway and current-user rules, docs paths and final checks. Edited here.
- Shared standards, copied from https://github.com/omkarchorghe16/dev-standards (do not edit them
  here; change them there and run its `sync.sh`):
  - `.github/instructions/change-hygiene.instructions.md`
  - `.github/instructions/coding-standards.instructions.md`
  - `.github/instructions/naming-and-readability.instructions.md`
  - `.github/instructions/architecture.instructions.md`
  - `.github/instructions/spring-boot.instructions.md`
  - `.github/instructions/logging.instructions.md`

Reusable workflows are in `.github/skills/*/SKILL.md` (REST API, JPA, architecture, logging,
testing, providers). Use the relevant skill; the instruction files stay authoritative for rules
that apply to every change. `project.instructions.md` wins where it is more specific.
