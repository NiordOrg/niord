# Cutover pre-flight — mailing-list trigger audit

Emitted by `CutoverPreflightService.auditTriggers()`, reached at
`GET /rest/publication-series/cutover-preflight`. **Committed whether or not it finds anything**,
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

---

## Findings — test environment, 2026-08-24

**Run against `niord.t-dma.dk`. Result: no hits. This is a real answer, not an empty haystack.**

| | |
|---|---:|
| Mailing lists | **9** |
| Triggers | **15** |
| Triggers carrying a `messageQuery` | **3** |
| Triggers naming a weekly tag | **0** |

The three queries that exist, in full:

| Mailing list | Type | `messageQuery` |
|---|---|---|
| `audio-broadcast` | `SCHEDULED` | `messageSeries=dma-nw&messageSeries=dma-nw-local&status=PUBLISHED&promulgationType=audio` |
| `navwarn-overview` | `SCHEDULED` | `messageSeries=dma-nw&status=PUBLISHED&type=COASTAL_WARNING&sortBy=ID` |
| `navwarn-overview-GL` | `SCHEDULED` | `messageSeries=ako-nw&status=PUBLISHED&type=COASTAL_WARNING&sortBy=ID` |

**All three key on `messageSeries=`, none on `tag=`.** So none of them depends on the weekly tag naming
convention, and none breaks at `C8`. The 12 remaining triggers carry no `messageQuery` at all.

Worth noting: none uses `publication=` either, so `B4.4`'s cure is not exercised on this environment.

### Still to do — run it on PRODUCTION before `B7.1`

The figures above are the test environment's. Production carries its own mailing lists, and the whole
point of this audit is the list somebody set up years ago and nobody has looked at since — exactly the
kind that exists in production and not in test.

**This audit is not discharged until it has run against production.** Re-run
`GET /rest/publication-series/cutover-preflight`, and record the result here beside the test figures.

## The other three assertions

| Check | Rule | Fails when |
|---|---|---|
| One current issue per series | `I-18` | Two imported issues of one series carry `publicTo IS NULL` — the archive forking in public |
| Cadenced issues derive their window | `R8` | A cadenced issue is `MANUAL`, so the first native publish does not cap it |
| The id space does not collide | `X-1` | Two issues share a `publicId`, or a natively created issue reuses a legacy `publicationId` |

All three are one-way after `B7.1`: once `publicAuthority` flips, a wrong window or a colliding id is
being served to the public, and the fix is a migration rather than an edit.

**On 2026-08-24 all three passed VACUOUSLY.** The response carried `importedIssues: 0` — the import has
not been run, so there were no rows to check and `"clear": true` asserted nothing. A green pre-flight
over an empty set is not evidence.

**They become meaningful only after `POST /import-legacy` has run.** The dry run
(`POST /import-legacy/validate`) reported `problems: []` over all 1,077 rows on the same day, so the
import is expected to succeed — but that is a different claim from these three assertions holding on the
rows it writes.
