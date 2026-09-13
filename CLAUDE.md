# CLAUDE.md

Niord is the Quarkus/Jakarta EE backend of Niord, the maritime safety information system for
Navigational Warnings and Notices to Mariners: `org.niord.base:niord-base-parent`, a Maven
multi-module build of the domain model and entities, the JAX-RS surface with its AngularJS webapp, and
the S-124 promulgation libraries. It is not the deployable — the country overlay `niord-dk`
(github.com/NiordOrg/niord-dk) consumes these jars, supplies the JDBC driver, carries Flyway at runtime
and produces the deployable Quarkus artifact and its container image; no country logic belongs here.

## Non-negotiables

- **Java 21 (Temurin): set `JAVA_HOME` to it for every Maven run.** Above 21 Byte Buddy refuses the
  newer class-file version while enhancing entities and the `niord-dk-web` augmentation fails;
  `-Dnet.bytebuddy.experimental=true` only disables the check behind lazy loading and dirty tracking.
- **An applied Flyway migration is never edited.** Flyway checksums the file, comments included — a
  migration that has run is the record of what the schema did on the day it ran, which is why V1, V5
  and V12 still describe objects V17 has dropped. Its header is the written record of the decision:
  the rule, the measurement or incident behind it, and why the obvious alternative is wrong.
- **Reshaping DDL is guarded.** MySQL 8 has no `ADD/DROP COLUMN IF EXISTS` and no
  `CREATE INDEX IF NOT EXISTS`, so each statement goes through an `information_schema` procedure
  (canonical form in `V16__drop_issue_week_to_label.sql`, add-if-absent in `V15`), and every
  migration from V6 onward must be re-runnable. Flyway resolves `${...}` before parsing, so naming
  tokens are spelled out in words — an unresolvable placeholder refuses the whole migration.
- **The database tests run against a real MySQL 8**, not Dev Services and not H2: the shared
  `hibernate_sequence`, the native `ENUM` columns and the spatial types are what they exist to
  exercise. Without the container they skip silently and a green run proves nothing.
- **Closed sets are Java enums persisted as native MySQL `ENUM`**, which reject unknown values, so a
  new constant needs an `ALTER TABLE` migration beside it. No entity brings its own id generator;
  every id comes from the one shared `hibernate_sequence` row.
- **Error codes are a closed catalogue**, one status per code, in `PublicationErrorCatalogue`; clients
  branch on the code, never on message text. 409 means the same request may succeed later, 400 that
  it never can, and an unmapped code is a bug (it defaults to 500).
- **Marker annotations get their teeth from tests, not interceptors.** `@DomainScoped`, `@VersionChecked`
  and `@ProgrammaticAdmin` are no-ops enforced by contract tests; an interceptor is the wrong shape
  because the entity is resolved inside the method from a path parameter and would guard the wrong row.
- **Comments are self-contained**: no tracking ids, phase labels, dates as history markers or
  pointers at documents. State the rule, then why the obvious alternative is wrong.
- **Copyright header on every Java file** (Apache 2.0). Files created 2026 or later say
  `Copyright 2026 Danish Emergency Management Agency`; older 2016 DMA headers stay as they are.

## Commands

From the repository root; POSIX `./mvnw`, Windows `mvnw.cmd`. `-o` works once `~/.m2` is warm. Leave
`-Dtest` patterns unquoted — cmd keeps POSIX single quotes literally, the pattern then matches nothing,
and `-Dsurefire.failIfNoSpecifiedTests=false` reports that silent zero as BUILD SUCCESS.

```bash
./mvnw -DskipTests -Dmaven.source.skip=true install                      # full build (skip sources only offline)
./mvnw -pl niord-core -am test > build.log 2>&1                          # core suite — always to a log
./mvnw -o -pl niord-model,niord-core install -DskipTests                 # refresh the ~/.m2 jars niord-web resolves
./mvnw -o -pl niord-web -am test -Dtest=org.niord.web.**.*Test -Dsurefire.failIfNoSpecifiedTests=false > build.log 2>&1
docker exec -i niord-test-db mysql -uroot -pmysql niord < niord-core/src/test/resources/schema/baseline-MaDaMe.sql
```

Seed the container (`niord-test-db`, port 13306; its `docker run` is in `README.md`) once from the
baseline; Flyway baselines at 0 and applies the migrations on top, the real delivery path. PowerShell
has no `<`: `Get-Content <file> | docker exec -i niord-test-db mysql -uroot -pmysql niord`.

- **Always redirect Maven to a log file and grep it afterwards.** Piping into `head`/`grep` and
  killing the shell leaves the launcher JVM wedged with the surefire fork waiting; the next run hangs
  at "Scanning for projects" — a lone ~140 MB `java.exe` and an empty log; kill every `java.exe`.
- **The reactor hides niord-web.** `-pl niord-core,niord-web test` stops at the first core failure and
  prints "Skipping Niord web application"; `-fae` does not help because niord-web depends on
  niord-core. Read *both* modules' "Tests run:" lines.
- **`-pl niord-web` alone resolves niord-core from the snapshot jar in `~/.m2`**, which can be weeks
  old (`NoClassDefFoundError` for new core classes) — install niord-model and niord-core first. With
  `-am`, upstream modules fail on "No tests matching pattern" without
  `-Dsurefire.failIfNoSpecifiedTests=false`; `-DfailIfNoTests` is the wrong flag.
- **Never `mvn install` from a feature branch** on a machine that also builds another branch: the
  shared `~/.m2` core jar would carry this branch's migrations into that build.
- Two Maven/Quarkus runs at once clash on port 8081 — add `-Dquarkus.http.test-port=0` to the second.
- Keep one invocation under about ten minutes by splitting `-Dtest` lists by package;
  `org.niord.core.publication.series.**.*Test` alone is ~950 tests and takes three or four runs.
- `test-compile` does not delete the `.class` files of removed sources — stale `target/` classes
  surface as CDI unsatisfied dependencies; run `clean`.
- `QUARKUS_DATASOURCE_JDBC_URL` overrides the URL in the test `application.properties` and reaches the
  surefire fork, which is how a run gets an isolated schema. Amending an uncommitted migration after
  it was applied means deleting its `flyway_schema_history` row or running repair.

## Layout

- `niord-model` → `niord-base-model` — the wire and interchange value objects. `PublicationVo` is a
  published XSD consumed by clients this codebase does not own; its field order is pinned by a test.
- `niord-core` → `niord-base-core` — all domain logic and entities (area, aton, batch, message,
  promulgation, publication, repo, report, schedule, …), and the Flyway migrations under
  `src/main/resources/db/migration/`, beside the entities they serve.
- `niord-web` → `niord-base-web` — the JAX-RS resources under `@ApplicationPath("/rest")` and the
  AngularJS webapp under `src/main/resources/META-INF/resources/`: the Quarkus static-resource root,
  not the war convention `src/main/webapp`, which is why the module declares no `<packaging>` and
  builds a jar. No `@QuarkusTest` and no test `application.properties` — web tests need no container.
- `niord-s124`, `niord-s124-madame` — the older S-124 library and the S-124 2.0.1 promulgation (the
  build's only jitpack dependency); `niord-josm-seachart` — the vendored JOSM seachart renderer.
- `scripts/` — four Node ESM tools, run by hand and never by CI: publication fixture capture, legacy
  estate capture, dev-database seeding, and a series seeder driving the real REST endpoints. A full
  `seed-publication-series.mjs` run cannot be undone: it leaves an undeletable published issue behind.

## Where the conventions are written

There is no `docs/` here; the rules live in code comments and in tests that fail when they break.

1. `README.md` — building, the MySQL test container, the country-overlay split.
2. `docs-publications-redesign-POINTER.md` — the publications specification lives in `niord-app` under
   `docs/publications/`; a pointer, not a copy, because a mirrored copy with a sync guard trains
   people to re-sync without reading.
3. `niord-core/src/test/resources/application.properties` — the test profile, every setting with its
   reason: the URL's time-zone parameters, Flyway on with Hibernate generation off, the 600 s
   transaction timeout, the scheduler off.
4. In niord-core, `src/main/resources/db/migration/.gitkeep` and `V17__drop_public_authority.sql` — the
   migration convention and the public-list rule; `src/test/resources/schema/README.md` — regenerating
   the byte-deterministic baseline (Hibernate emits CRLF on Windows; the committed form is LF).
5. `niord-web/src/main/java/org/niord/web/publication/PublicationErrorCatalogue.java` — every code with
   the justification for its status; the redesign's rules are in the `core/publication/series/` headers.

Two generated test inputs are exported from `niord-app` and must not be hand-edited:
`niord-core/src/test/resources/rule-ids.txt` and `entity-fields.json`, both carrying the source hash.
Regenerate them under `niord-app/scripts/publications/` with `node gen-rule-ids.js --write` and
`node gen-field-manifest.js --write`, and commit both repositories together.

## Publications

- **The public list is new-model issues union the legacy rows no published issue has taken over.** No
  per-series switch, no estate-wide switch, and none may come back — V17 removed the last one. The
  series lifecycle status is deliberately not consulted: a retired series keeps serving its issues.
- `PublicationPublicAdapter` maps `publishDateFrom = issue.publicFrom` and
  `publishDateTo = issue.publicTo` (the next issue's stamp minus 1 ms, because the legacy overlap
  helper is closed at both ends) — **not** the interval, which would make every issue carry the
  previous period's window.
- `PublicationResolver` is the single place a `publication=` id is resolved: new-model issue, then
  legacy publication, then refuse. Resolution never widens: an id resolving to nothing is refused, not
  dropped, because a dropped filter returns the whole default corpus. It is no existence oracle — an id
  that exists but is not servable to this caller is refused with text identical to one that does not.
- **Writes are domain-scoped, reads are not.** A series that names a domain belongs to it; a series with
  no owner is writable by nobody except through `PUT /rest/publication-series/series/{seriesId}/owner`,
  which still demands admin in the target and a reason. Sharing never grants a write. Refusal is 403
  `NOT_IN_DOMAIN`, distinct from a missing role so the client offers "switch domain".
- **The legacy import is one-shot**: `plan()` reads and translates without writing and collects every
  problem, `apply()` writes what plan produced, all-or-nothing. It refuses to merge (`ALREADY_IMPORTED`);
  the only way back is `DELETE /rest/publication-series/import-legacy`, refused once an imported series
  has left DRAFT. Once an imported issue is published, rolling back go-live is a full database restore.
  `GET /rest/publication-series/import-check` is the go/no-go: always 200, `clear: false` means not ready.
- The import entry points are `@Transactional(NOT_SUPPORTED)` and open their own
  `QuarkusTransaction.requiringNew().timeout(1800)`: `BaseService`'s class-level `@Transactional` is
  `@Inherited`, so without the method-level binding the ambient transaction is reaped mid-run and the
  commit surfaces as a 500 over a full database. Bulk writes are latency-bound on the shared
  `hibernate_sequence` row — a `SELECT … FOR UPDATE` plus `UPDATE` per id, 3.4 ms against the deployed
  database; JDBC batching does not help, so reduce rows.
- Roles are per-domain Keycloak composites, `user < editor < admin < sysadmin`, plus
  `publication-curate` outside the ladder for hand-editing an issue's members. Endpoints needing it
  also accept ADMIN as a transitional fallback, removed one release after the realm gains the role.

## Guards

- `TestSuiteGuardTest` — discovers everything under `org.niord` and asserts a floor of 590; raise it as
  tests are added, never lower it to make a build pass. `quarkus-junit5` flips Surefire to the JUnit
  Platform provider, and without `junit-vintage-engine` the JUnit 4 suite goes undiscovered: "Tests
  run: 0 … BUILD SUCCESS".
- `InvariantManifestTest` + `@BindsRule` — every id in `rule-ids.txt` bound to a named assertion,
  `pending` only for a task in the closed list. `EntityContractTest` — no entity brings its own id
  generator, and `entity-fields.json` and the fields agree in both directions, types included.
- `MigrationIdiomTest` (V6 onward re-runnable), `FlywayBaselineTest`, `CoreQuarkusBootstrapTest` (the
  server must really report MySQL 8.0.x, so it fails rather than passes with no container), and
  `FixtureCoverageTest` (19 hazard fixtures matched by name and hashed, never merely counted).
- `PublicationTierMatrixTest` — every publication endpoint and caller in one declared table of four
  tiers; a new endpoint fails the build until its tier is written down. Alongside it the API,
  exception-mapper, optimistic-lock, transaction, rail-refusal and payload contract tests.
- `.gitattributes` pins LF on `mvnw`, `*.mjs`, `fixtures/**`, `schema/**` and `rule-ids.txt` — each is
  hashed or compared byte for byte, and a CRLF checkout fires the drift guards on a clean clone.
- CI is `niord-app-azure-pipelines.yml` (JDK 21, Maven 3.9.9, then niord-dk and the Docker image);
  `azure-pipelines.yml` and `.github/workflows/buildonpush.yml` are stale JDK 11 leftovers. No pipeline
  runs MySQL, so the database tests skip on CI by design; Maven Central answers 429 — re-run the job.
