---
applyTo: "**/*.java"
---

<!-- Managed by dev-standards (stacks/java-spring/instructions/naming-and-readability.instructions.md). Edit it in https://github.com/omkarchorghe16/dev-standards and run sync.sh; local edits are overwritten. -->

# Naming and readability standards

These rules decide how new and changed code is named and laid out. They complement
`coding-standards.instructions.md` and `architecture.instructions.md`. The repository's
`project.instructions.md` lists its own reference classes and any exceptions.

## One feature per class

- Name each controller, service and client after the single feature, resource or provider it owns:
  `PortfolioController` and `PortfolioService`, `FmpProfileClient` and `FmpProfileService`.
- Never use generic names such as `ApiController`, `MainService`, `CommonService`, `Helper`,
  `Manager` or `Utils`. They grow into classes that do everything.
- When a class starts serving a second resource or provider, split it before adding more code.
- A focused sub-use-case may get its own service named after it: `WatchlistImportService`.
- Provider-specific code lives in its own package once it has several classes.
- A controller's class-level `@RequestMapping` holds the resource path (`/api/v1/portfolios`), so
  method mappings stay short.

## Class and type suffixes

| Kind | Pattern | Example |
|---|---|---|
| REST controller | `<Resource>Controller` | `WatchlistController` |
| Use-case service | `<Resource>Service` | `WatchlistService` |
| External API client | `<Provider><Resource>Client` | `FmpProfileClient` |
| Provider rate limiter | `<Provider>RateLimiter` | `AlphaVantageRateLimiter` |
| Typed settings | `<Area>Properties` | `FmpProperties` |
| Notification channel | `<Channel>Notifier` | `SlackNotifier` |
| Scheduled job | `<Job>Scheduler` | `DigestScheduler` |
| Spring Data repository | `<Entity>Repository` | `PortfolioRepository` |
| JPA entity | as set in `project.instructions.md` (with or without `Entity`) | `Stock`, `PortfolioEntity` |
| Request / response DTO | `<Thing>Request` / `<Thing>Response` | `WatchlistRequest` |
| Configuration | `<Area>Config` | `WebConfig` |
| Domain error | `<Cause>Exception` | `ResourceNotFoundException` |

Market-specific types start with the market or provider: `Usa...`, `India...`, `Tapetide...`.

## Method names

Use verb + noun, and keep a controller method's name the same as the service method it calls.

| Operation | HTTP | Name |
|---|---|---|
| Page or list | `GET /things` | `listThings` |
| One by id | `GET /things/{id}` | `getThing` |
| Create | `POST /things` | `createThing` |
| Full replace | `PUT /things/{id}` | `replaceThing` |
| Partial change | `PATCH /things/{id}` | a name for the change: `renameWatchlist` |
| Delete | `DELETE /things/{id}` | `deleteThing` |
| Child items | `/things/{id}/items` | `addItems`, `updateItem`, `removeItem` |

- Use `fetch...` for a call to an external provider, `get...` / `find...` for a database read and
  `refresh...` for "fetch from the provider and save".
- An existing single-resource service that already uses short verbs (`create`, `find`, `update`)
  keeps that style when you add to it. New classes use verb + noun.
- Do not use bare `get()`, `save()` or noun-only names such as `portfolios()` or `profile()`.
- Do not use a `null` argument to switch behavior (for example `save(null, request)` meaning
  "create"). Write two methods.
- Private helpers also say what they do: `findWatchlist`, `toResponse`, `normalizeSymbol`.
- A lookup that throws when nothing is found is `findX` and returns the entity. A lookup that may
  find nothing returns `Optional<X>`.

## Fields, parameters and variables

- Inject dependencies into `private final` fields named after their type: `portfolioRepository`,
  `fmpProfileClient`. Never `service`, `client`, `repo` or a single letter.
- Single-letter names are allowed only for short lambda parameters (`w -> w.getId()`). Method
  parameters and local variables use full words: `request`, `watchlist`, `symbol`.
- Collections are plural (`items`); maps say what they map (`quotesBySymbol`).
- Booleans read as a question: `isFavorite`, `hasApiKey`, `symbolChanged`.
- Repeated literals, error messages and provider endpoints become `private static final`
  constants or configuration properties.

## Layout

- One statement per line, four-space indentation, braces on every `if`/`for`, and lines of 120
  characters or fewer. Never put a whole method body on one line.
- Order members as: constants, fields, constructor, public methods (in HTTP-operation order),
  private helpers, mapping methods (`toResponse` and similar) last.
- Group long classes with short section comments, such as `// ---- Watchlist items ----`.
- Use explicit imports. Wildcard imports only where `project.instructions.md` allows them.
- New endpoints return typed DTOs, not `JsonNode`, `Map` or `Object`.
- Add a short Javadoc sentence to a public method only when its name does not explain the
  behavior: ordering rules, all-or-nothing semantics, rate limits, caching, partial failures.

## Renaming existing code

- A rename must not change public behavior: URL paths, JSON field names, status codes, table names
  and property keys stay the same unless the change is requested.
- Update tests, `README.md`, `docs/`, the Postman collection and the skills that mention the old
  name. If class or method names appear in log lines, mention the rename for anyone searching logs.
- Run `mvn test` after renaming.
