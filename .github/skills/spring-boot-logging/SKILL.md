---
name: spring-boot-logging
description: Add or change logging, exception reporting, request diagnostics or a central logging aspect in a Spring Boot service, keeping logs free of secrets and payloads.
---

<!-- Managed by dev-standards (stacks/java-spring/skills/spring-boot-logging/SKILL.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Logging and observability workflow

Use this skill when changing logs, exception reporting or request-level diagnostics. The rules are
in `.github/instructions/logging.instructions.md`; this repository's logging components (aspect,
exception handler, log-level variable) are named in `.github/instructions/project.instructions.md`.

## Conventions

- A central aspect, when present, records start, completion and duration of public controller and
  service methods with the HTTP method and sanitized path. It never logs arguments, DTOs, file
  contents or query strings, and it rethrows the original exception unchanged.
- The exception handler logs handled 4xx failures without payloads and unexpected exceptions with
  their stack trace, and returns a sanitized client response.
- Log a stack trace only at the error-handling boundary.
- Logs go to standard output through SLF4J; the log level is configurable through a property or
  environment variable.

## Implementation sequence

1. Identify the operation and the layer that owns the diagnostic event.
2. Prefer the central aspect for lifecycle telemetry over repeated inline logs.
3. Keep exception propagation intact; log actionable context without request content or secrets.
4. When changing the aspect, test that return values and thrown exceptions pass through unchanged.
5. Update README and affected instructions/skills when logging behavior or configuration changes.
6. Run targeted tests and check that emitted logs contain no sensitive inputs.

## Verification checklist

- Controller and service calls log identity, outcome and duration once.
- Exceptions still reach the exception handler.
- Logs omit arguments, payloads, query strings and credentials.
- 4xx and 5xx responses stay consistent with the API contract.
