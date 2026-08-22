# Schema baseline

`baseline-MaDaMe.sql` is the DDL the current entity model produces, generated rather than written.
It is the **"before" picture**: the schema as it stands before the publications redesign adds its
eight tables, so that change shows up as a reviewable 91 → 99 diff with nothing else moving.

It is kept **pristine** — exactly the bytes Hibernate emitted, with no header added — because
regeneration is byte-deterministic (verified), so a straight comparison is enough to tell whether it
has gone stale.

## Regenerating

Needs the test MySQL container from the root README:

```
./mvnw -pl niord-core test -Dtest=CoreQuarkusBootstrapTest \
  -Dquarkus.hibernate-orm.scripts.generation=create \
  -Dquarkus.hibernate-orm.scripts.generation.create-target=<abs path>/baseline-MaDaMe.sql
```

Hibernate emits **CRLF** when it runs on Windows, while the committed form is **LF** (pinned in
`.gitattributes`). Normalise after regenerating, or the comparison fails for a reason that has
nothing to do with the schema:

```
# from the repo root, after regenerating
node -e "const f=process.argv[1],s=require(fs);s.writeFileSync(f,s.readFileSync(f,utf8).replace(/
/g,n))" \
  niord-core/src/test/resources/schema/baseline-MaDaMe.sql
```

## What it establishes

- **91 `create table` statements**, one of them
  `create table hibernate_sequence (next_val bigint) engine=InnoDB;` followed by
  `insert into hibernate_sequence values ( 1 );` — the shared sequence, not per-table identity.
- **Zero** columns carry `auto_increment`.
- **28 native MySQL `ENUM(...)` columns.** Worth stating plainly because it contradicts what was
  assumed while the model was being specified, where these were repeatedly described as
  `VARCHAR(255)`. DDL hand-written from that assumption would have had the wrong column type
  throughout, surfacing much later as a validation failure or a silent behavioural difference.
  A native `ENUM` also **rejects values outside its list**, so adding a Java enum constant later
  needs an `ALTER TABLE`.

The entities are the source of truth for DDL; the specification is the source of truth for the model.
