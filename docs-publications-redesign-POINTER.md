# Publications — where the specification lives

The specification for the publications domain is **not** in this repository. It lives in the
`niord-app` repository under `docs/publications/`: `DATA-MODEL.md` (entities, invariants, migrations),
`API-CONTRACT.md` (every endpoint and wire shape), `domain-rules.md` (the rules the screens enforce)
and `go-live/` (the process, the runbook and the scripts for the go-live window).

This is a pointer rather than a copy on purpose. A mirrored copy with a sync guard trains people to
re-sync without reading, and that is how the one drift that matters gets waved through.

## What *is* exported here, and why

Two files under `niord-core/src/test/resources/` are generated from `DATA-MODEL.md` in `niord-app`
and read by tests in this repository:

- `rule-ids.txt` — the invariant ids of section 8. The manifest test binds every one of them to a test
  and fails the build on any id with no binding.
- `entity-fields.json` — the declared columns of every entity. `EntityContractTest` fails the build on
  a declared column with no field on its entity, and on a field with no declared column.

Freshness is checked on the other side, where the edit happens: `node scripts/publications/spec-check.js`
in `niord-app` recomputes the hash of the generating sections and raises a **BLOCKER** when either file
no longer matches. A rule or a column changed without regenerating therefore fails immediately, in the
repository that changed it.

Regenerate in `niord-app` with

```bash
node scripts/publications/gen-rule-ids.js --write --out <this checkout>/niord-core/src/test/resources/rule-ids.txt
node scripts/publications/gen-field-manifest.js --write --out <this checkout>/niord-core/src/test/resources/entity-fields.json
```

Without `--out` (or `NIORD_REPO=<this checkout>`) both scripts write into the checkout that sits beside
`niord-app`, which is the right target only when that is the checkout the matching branch is in. Then
commit both repositories together.
