---
applyTo: "**/*.java"
---

<!-- Managed by dev-standards (stacks/java-spring/instructions/logging.instructions.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Logging standards

- Use SLF4J through a class logger (or Lombok `@Slf4j` where the project uses Lombok); never
  `System.out` or `System.err`.
- Log lifecycle events at INFO, recoverable problems at WARN and unexpected failures at ERROR. Use
  DEBUG for diagnostic detail that is too noisy for normal operation.
- Include safe context: operation, provider, market, record count, status.
- Never log passwords, API keys, tokens, authorization headers, credential-bearing URLs, raw
  provider responses, message bodies or full request/response payloads. Strip credentials even when
  they appear in query parameters or exception messages.
- Use parameterized messages (`log.info("Fetched {} profiles", count)`) and log counts or sanitized
  identifiers instead of whole objects or lists.
- Log an exception once, where it is handled; do not log it again at each layer. Propagate it when
  the request must fail.
- If the project has a central invocation logger (for example an AOP `LoggingAspect`), do not add
  per-method entry/exit logs. The aspect must not log arguments or bodies and must rethrow the
  original exception unchanged.
- Keep request context to HTTP method and sanitized path; never add query strings, credentials or
  payloads to access logs.
