# Publications redesign — where the specification lives

The specification for this work is **not** in this repository. It lives in the `niord-app` repo, at
`migration_docs/publications-redesign/` and `migration_docs/backend_handoff/`, entered through
`backend_handoff/01-INDEX.md`.

This is a pointer rather than a copy on purpose. An earlier plan mirrored the whole nine-document folder
here and hash-verified it; that copied 812 KB to deliver 408 bytes of build input, and its sync guard went
red on edits to documents no build step reads — which trains people to re-sync without looking, and that is
how the one drift that matters gets waved through.

## What *is* exported here, and why

`niord-core/src/test/resources/rule-ids.txt` — the 92 invariant ids from `DATA-MODEL.md` section 8. The
manifest test binds every one of them to a test and fails the build on any id with no binding, so the list
has to be readable from this repo.

Freshness is checked on the other side, where the edit happens: `spec-check.js` in `niord-app` recomputes
the SHA-256 of section 8 and raises a **BLOCKER** if it no longer matches the header in `rule-ids.txt`. A
rule added without regenerating therefore fails immediately, in the repo that added it, rather than at some
later build over here.

Regenerate with `node migration_docs/publications-redesign/gen-rule-ids.js --write` in `niord-app`, then
commit both repos together.
