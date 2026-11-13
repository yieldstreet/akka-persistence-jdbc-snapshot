<!-- The shared backend review rules, kept in yieldstreet/platform-llm at sync/backend/.codacy/instructions/review.md and synced into every repo tagged type=backend. Edit them in platform-llm: the next sync overwrites every copy. A repository's own review rules belong in its CLAUDE.md. -->

# Backend review rules

These rules apply to every Willow Wealth backend repository. Comment where code the pull request adds
or changes breaks one, naming the rule. Leave code the pull request doesn't touch alone.

## Security

- **Investor endpoints check ownership.** An endpoint for investors (not an admin endpoint under the
  next rule, and not an `/internal/` one) that takes a user-owned id (an investor entity, an investor
  account, …) checks it belongs to the session user before using it, and returns the forbidden result
  when it doesn't.
- **Admin endpoints check the role their action needs.** An admin endpoint is one meant for staff rather
  than investors. Most sit under an `admin` path, but many don't, so judge by who the action is for. It
  requires a specific role: a constant from `yieldstreet.role.Roles`, or from the service's own `Roles`. A
  login alone isn't enough: investors log in too, and no gateway checks roles, so the service's own check
  is the only one.
  - Play: session-locator's `@RequiredRole(Roles.…)` or `@Restricted(role = Roles.…)`, on the action or
    its class. In play-core, Deadbolt's `@Restrict(@Group(Roles.…))`.
  - Spring: `@RolesAllowed(Roles.…)` on the controller or service method, or on its class, with JSR-250
    enabled (`@EnableMethodSecurity(jsr250Enabled = true)`). `hasRole(Roles.…)` works too.
    `hasAuthority(Roles.…)` and `@Secured(Roles.…)` never match, because session-locator adds a `ROLE_`
    prefix to every role, so they refuse everyone, staff included.
  - Lagom, websockets and anything else without annotations: a role check in code, such as
    `checkRole(…)`, `authenticate(request, Roles.…)` or `userContext.hasRole(Roles.…)`.

  Flag an admin endpoint whose only check is a login (`@Authenticated`, `@SubjectPresent`,
  `.authenticated()`, `@PreAuthorize("isAuthenticated()")`) or that has no check at all. Also flag one
  that relies, for a specific action, on a role every staff user holds (`Roles.Dashboard.VIEW`) or on a
  deprecated catch-all (`Roles.Legacy.YS_ADMIN`, `YS_ADMIN_RO`). An endpoint that lets through either the
  data's owner or a role, and a staff login or logout endpoint, are fine.
- **No secrets in the repository.** Keys, tokens and passwords go in SOPS or the secrets manager.
- **No sensitive data in logs.** Tokens, passwords, keys, account numbers and investor personal data
  stay out of log lines, including whole requests or headers logged by an HTTP client.
- **SQL uses bound parameters.** Never values concatenated or formatted into a query.

## Architecture

These are the key principles from section 05 of the Tech Architecture Vision, with its section numbers.
The [full document](https://github.com/yieldstreet/platform-llm/blob/master/docs/arch/tech-architecture-vision.md#05-architectural-principles-and-patterns)
has the reasoning, for authors who want it. Much existing code predates these principles, so raise them
on new code as suggestions, not defects.

### Domains and data

- **Each domain owns its data.** Only the service, or the module within a service, that owns a domain
  reads and writes its data directly. Flag queries against another service's database, and one
  domain's concepts leaking into another's models. (5.1, 5.4)
- **Read from the source of truth.** Get another service's data with a synchronous call to that
  service's API, not from a local projection or copy kept in sync from its events, which lags behind.
  A new projection needs a business reason the owner's API can't meet. The exception is the Aggregation
  service (portfolio, statements, taxes, comms, CDP), which handles the asynchronous side effects of
  other services' actions and defaults to processing their events. (5.3.2, 5.4)
- **Within a service, go through the other module's interface.** Joins across modules are the
  exception, for tightly coupled data, and are backed by database constraints. (5.4)

### State

- **Persist state changes synchronously.** Commit business-critical state changes synchronously,
  ideally in one transaction inside one service. Asynchronous persistence needs a reason: a third party
  or the flow forces it, scale needs it (batch processing), or the user doesn't need the result.
  (5.2, 5.3.1)
- **Keep services stateless.** An operation depends only on its inputs and what it loads from the
  database, never on state held in memory between requests. (5.9)
- **Never delete transactional data.** Mark it obsolete instead. The exception is erasing a user's
  personal data when they exercise the right to be forgotten and haven't transacted with us. Enforce
  non-null columns, foreign keys and business rules with database constraints. (5.13)
- **Keep critical entities auditable.** A current-state table, plus an audit table that records each
  change of state. (5.7)

### Layers and third parties

- **Keep business logic out of the plumbing.** Controller, then service, then repository. Business
  rules belong in the service layer, not in controllers, repositories, event listeners or API clients.
  (5.6)
- **Wrap third parties.** Describe the vendor's part of the domain in a model we own, and talk to the
  vendor through a gateway that implements it, so vendor types stay out of core models. Refer to our
  entities by UUID in a mapping table. (5.5)
- **Assume calls fail.** Circuit breakers around third parties and calls between services. State-changing
  APIs and event handlers are idempotent, so a retry can't create a duplicate. Bulkheads around
  operations that could starve shared resources. (5.5, 5.8)

### Contracts

- **Keep API and event changes backward compatible.** Existing consumers must keep working, the mobile
  app above all, since we don't control which version investors run. There is no API versioning.
  (5.10.4)
- **APIs follow standard REST patterns.** Standard status codes, standard error and validation
  payloads, and error keys rather than messages. The OpenAPI contract is the source of truth. (5.10.2)
- **Events carry the entity's whole state and its current status**, in a registered JSON schema.
  (5.10.1)
- **Calls between services carry auth.** Pass the user's auth token on where the called service needs
  the user's context. Service-to-service authentication on `/internal/` routes isn't in place everywhere
  yet, so this is direction for new calls. (5.11)
- **Feature flags stay safe to roll back.** Prefer flags in the frontend clients. A flag split in the
  backend must not make data changes that aren't backward compatible. (5.15)

## The service's own architecture

Check the change fits how this service is already built. If the repository has an `openwiki/` folder,
read the pages relevant to the change first: it is a generated summary of the service's architecture,
its modules and the patterns it follows. Otherwise, go by the surrounding code.

Flag a major departure and ask the author to explain it, for example:

- a second way of doing something the service already does one way, such as data access, error
  handling, calling another service or publishing events;
- logic put in a module or layer that, by the wiki or the code, belongs to another;
- a change that contradicts a decision the wiki records.

Moving toward one of the Architecture principles above isn't a departure to flag, and following the
service's established pattern is at most an Architecture suggestion. Don't flag a departure the pull
request description already explains, or small differences in style. If the service is deliberately
changing course, ask for the reason in the pull request description. The wiki is regenerated from the
code after merge, so never ask for its pages to be edited by hand. Where the wiki and the code on the
default branch disagree, the code is authoritative.

## This repository's own rules

If the repository's `CLAUDE.md` has review rules, apply them too. They are guidelines specific to this
codebase, kept and edited in the repository itself. They can add rules, and narrow the architecture ones
for this service, but they can't relax the security rules.
