# Cutover pre-flight — mailing-list trigger audit

Emitted by `CutoverPreflightService.auditTriggers()`. **Committed whether or not it finds anything**,
because "we found no triggers" and "nobody ran the audit" are indistinguishable after the fact, and the
failure being guarded against is silent.

## What this looks for, and why

`B4.4` cured `publication=` in mailing-list triggers. **Nothing audited the triggers that name a TAG.**

After `C8` no new `nm-wNN-YYYY` tag is minted. A mailing list whose `messageQuery` hand-types one — 
`tag=nm-w27-2026`, `tag=nm-pt-w51-2017` — therefore **stops matching, silently**. Nobody is told. The
visible failure is a mailing that does not go out, which surfaces when a recipient eventually asks why
they stopped receiving it, by which time the cause is weeks behind.

Searched shape (case-insensitive), against every `MailingListTrigger.messageQuery`:

```
tag=["']?nm-(pt-)?w\d{1,2}(-\d{1,2})?-\d{4}
```

Deliberately loose. This is a report a person reads: a miss is a mailing list that quietly dies, a false
positive is one line to dismiss.

## This is a report, not a rewrite

What to do with each hit is **the user's call, made before `B7.1`**. The audit does not edit triggers —
re-pointing a mailing list changes who receives what, which is not a migration decision.

The options per hit are roughly: re-point it at `publication=<publicId>` (which `B4.4` made resolvable),
re-point it at a tag that will still be minted, or accept that the list is finished and retire it.

## Findings

**None recorded yet.** The audit runs against the imported estate, and the import has not been run on
an environment carrying real mailing lists — the test database holds none. Run
`CutoverPreflightService.run()` against the test environment after the next import and paste the
`triggerAudit` rows here, or record explicitly that it returned empty.

| Mailing list | Trigger type | Matched tag | messageQuery |
|---|---|---|---|
| _(not yet run against an environment with mailing lists)_ | | | |

## Pre-flight assertions that run beside this

| Check | Rule | Fails when |
|---|---|---|
| One current issue per series | `I-18` | Two imported issues of one series carry `publicTo IS NULL` — the archive forking in public |
| Cadenced issues derive their window | `R8` | A cadenced issue is `MANUAL`, so the first native publish does not cap it |
| The id space does not collide | `X-1` | Two issues share a `publicId`, or a natively created issue reuses a legacy `publicationId` |

All three are one-way after `B7.1`: once `publicAuthority` flips, a wrong window or a colliding id is
being served to the public, and the fix is a migration rather than an edit.
