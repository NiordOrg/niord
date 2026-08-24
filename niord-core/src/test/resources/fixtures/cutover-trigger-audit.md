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

Searched shape (case-insensitive), against **every expression a trigger carries** --
`messageQuery`, `messageFilter` and `scriptResourcePaths`:

```
(nm-(?:pt-)?w\d{1,2}(?:-\d{1,2})?-\d{4})
```

Deliberately loose. This is a report a person reads: a miss is a mailing list that quietly dies, a false
positive is one line to dismiss.

---

## Findings — 2026-08-24

**No hits, and the reason is stronger than "we looked and found none".**

| | |
|---|---:|
| Mailing lists | **9** |
| Triggers | **15** |
| ... carrying a `messageQuery` | **3** |
| ... carrying a `messageFilter` | **12** |
| Triggers naming a weekly tag | **0** |

### Every mailing list is a NAVIGATIONAL WARNING feature. Publications are NOTICES TO MARINERS.

That is the finding. The three scheduled triggers key on `messageSeries=dma-nw`,
`dma-nw-local` and `ako-nw`; the twelve status-change triggers key on
`msg.messageSeries.seriesId` for those same series, or on `msg.promulgation('navtex')`. The lists are
`audio-broadcast`, `navwarn-*`, `navtex-*` and `LW-update`.

The weekly `nm-wNN-YYYY` tags belong to the NM publication machinery — `dma-nm`, `dma-nm-almanac`,
`dma-nm-annex`. **No mailing list touches NM at all.**

So G-12's premise — "a mailing list keyed on the tag naming convention silently stops matching after C8"
— has no instance in this system. Not "none found today": the two features do not overlap. A new
NM-driven mailing list could reintroduce the risk, which is why the audit stays in the pre-flight.

Worth noting: none uses `publication=` either, so `B4.4`'s cure is not exercised by any live trigger.

### The hole this exposed in the audit itself

The first version scanned **`messageQuery` only**. Twelve of the fifteen triggers carry no
`messageQuery` at all — they express themselves in `messageFilter` — so that version read **a fifth of
the triggers and reported a clean result**. Silence that reads as success, which is the exact failure
this committed report exists to prevent.

The audit now scans `messageQuery`, `messageFilter` and `scriptResourcePaths`, and the pattern is no
longer anchored on `tag=`: in a script expression a tag appears quoted (`msg.tags.contains('nm-w27-2026')`),
not as a query parameter. Each hit records which field it came from.

Found by Rasmus asking whether these lists use tags at all.

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
