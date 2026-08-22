/*
 * Captures publication membership fixtures from the locked message tags on the
 * static test snapshot, and writes them as byte-reproducible JSON.
 *
 * Run explicitly, never from CI:
 *     node scripts/capture-publication-fixtures.mjs [--out <dir>] [--only <tag>]
 *
 * The oracle is a live system. An automatic refresh would let a regression
 * rewrite its own expectations and report itself green, so re-running this and
 * committing the diff is a deliberate, reviewed act. The diff is the point.
 */
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const HOST = 'https://niord.t-dma.dk';
const DOMAIN = 'niord-nm';

/* The weekly tags. Positive controls first: these two are the validated pair
 * and their counts (123 / 2) are what the acceptance check pins. */
const TAGS = [
  'nm-pt-w01-2026',
  'nm-w01-2026',
  ...[23, 24, 25, 26, 27, 28].flatMap((w) => [`nm-w${String(w).padStart(2, '0')}-2026`, `nm-pt-w${String(w).padStart(2, '0')}-2026`]),
  // Hazard weeks.
  'nm-w45-2018', // the explicit NEGATIVE fixture -- the resolver is expected to differ here
  'nm-pt-w12-2024', // release-moment precision: membership changes 4s after the stamped cut-off
];

/* Publications, resolved through ?publication=<uuid>. Unlike tags these carry no
 * messageCount to check against, so the corpus guard does that job instead: a
 * publication id the backend does not recognise is NOT an error, it silently
 * returns the entire default corpus. */
const PUBLICATIONS = [
  { case: 'skydeomraader-2017-ed1', id: '38a25c50-47d1-4ac4-bbe7-2a37f0dfee5a' },
  { case: 'skydeomraader-2017-ed2', id: 'f2fa5eb8-e72a-4198-a883-fbcf1202274f' }, // supersede-moment superset
  { case: 'skydeomraader-2018', id: '58a37fcf-10f6-41ba-b791-1e77d1a70667' }, // the union case
  { case: 'skydeomraader-2026', id: '558aa1ac-7807-43b0-8d00-9b074baca026' }, // the 31-of-32 pair
  { case: 'skydeomraader-2027', id: '46c4ed07-17a7-4afc-87f9-c78d266c4805' },
];

/* Single messages, fetched by shortId. A 204 is a RESULT here, not a failure:
 * the rolled-back-publish class is defined by the message not existing, so its
 * absence is the fact being frozen. */
const MESSAGES = [
  { case: 'nm-780-18', shortId: 'NM-780-18' }, // NULL publishDateFrom
  { case: 'nm-1116-22', shortId: 'NM-1116-22' }, // back-dated pair
  { case: 'nm-300-24', shortId: 'NM-300-24' }, // type mutation
  { case: 'nm-466-26', shortId: 'NM-466-26' }, // 62s past the cut-off
  { case: 'nm-473-26', shortId: 'NM-473-26' }, // rolled-back publish
  { case: 'nm-962-25', shortId: 'NM-962-25' }, // rolled-back publish
  { case: 'nm-1046-25', shortId: 'NM-1046-25' }, // rolled-back publish
];

/* The contaminated-oracle blocklist, carried as data rather than as quietly
 * absent files. Each entry says why it cannot be trusted, so the decision stays
 * reviewable instead of looking like an oversight.
 *
 * "Blocklisted" means NOT AN ORACLE -- the recorded set must not be used to
 * assert that the resolver reproduces it. It does not always mean "do not
 * capture": the union-over-window annuals are captured deliberately, because
 * asserting the resolver DIFFERS from them is itself a test. Those carry
 * fixture: true. Everything with fixture: false must have no file at all. */
const BLOCKLIST = [
  { tag: 'nm-w01-2025', fixture: false, reason: 'shared by three publications, not one -- the tag cannot say which issue it is the oracle for' },
  { tag: 'nm-pt-w01-2025', fixture: false, reason: 'shared by three publications, not one' },
  { tag: 'nm-w52-2024', fixture: false, reason: 'cut-off destroyed by a later save; retire/republish batches re-stamped updated' },
  { tag: 'nm-pt-w52-2024', fixture: false, reason: 'cut-off destroyed by a later save' },
  { tag: 'nm-pt-w02-2025', fixture: false, reason: 'name drift -- it belongs to the "uge 3 - 2025" publication, and nm-pt-w03-2025 does not exist' },
  { tag: 'efs-2019-changeover-1', fixture: false, reason: 'structurally irreproducible: two tags recorded simultaneously for six days across the 2019 changeover fortnight; 12 messages sit in 2-3 issues at once and 2 in none, so tiling does not hold' },
  { tag: 'efs-2019-changeover-2', fixture: false, reason: 'structurally irreproducible -- 2019 changeover fortnight, issue 2 of 3' },
  { tag: 'efs-2019-changeover-3', fixture: false, reason: 'structurally irreproducible -- 2019 changeover fortnight, issue 3 of 3' },
  { tag: 'skydeomraader-2018', fixture: true, reason: 'union over a long window: 71 members, two full annual cohorts. Every publishDate boundary +/-1ms was swept across the series history and no instant reproduces the set. Captured anyway, as a divergence fixture -- the resolver is EXPECTED to differ' },
  { tag: 'skydeomraader-2020-ed1', fixture: false, reason: 'union over a long window (66 members); not captured -- skydeomraader-2018 already covers the class' },
  { tag: 'skydeomraader-2022-ed1', fixture: false, reason: 'union over a long window (41 members); not captured -- covered by skydeomraader-2018' },
  { tag: 'efs-a-2020-ed1', fixture: false, reason: 'union over a long window (56 members); not captured -- covered by skydeomraader-2018' },
];

/* curl.exe rather than fetch(): proven to work on this box, where other HTTP
 * paths are blocked. */
function getJson(url) {
  const body = execFileSync('curl.exe', ['-sS', '--fail', '--retry', '3', url], {
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  });
  try {
    return JSON.parse(body);
  } catch {
    throw new Error(`Not JSON from ${url}: ${body.slice(0, 200)}`);
  }
}

function resolveTag(name) {
  const hits = getJson(`${HOST}/rest/tags/search?name=${encodeURIComponent(name)}&limit=20`).filter((t) => t.name === name);
  if (hits.length !== 1) throw new Error(`${name}: expected exactly 1 tag, got ${hits.length}`);
  const tag = hits[0];
  // An unlocked tag is not an oracle -- its membership can still move.
  if (tag.locked !== true) throw new Error(`${name}: tag is NOT locked; refusing to use it as an oracle`);
  return tag;
}

function fetchMembers(tag) {
  // tag= MUST be the UUID. Passing the name is not an error: the filter is
  // silently dropped and the whole domain corpus comes back instead. The
  // total check below is what catches it.
  const res = getJson(`${HOST}/rest/messages/search?domain=${DOMAIN}&tag=${tag.tagId}&maxSize=1000`);
  if (res.total !== tag.messageCount) {
    throw new Error(`${tag.name}: total ${res.total} != tag.messageCount ${tag.messageCount} -- filter silently dropped, or the tag moved`);
  }
  const data = res.data ?? [];
  if (data.length !== res.total) throw new Error(`${tag.name}: returned ${data.length} of ${res.total}; raise maxSize`);
  return data;
}

/* The public VO has no `uid` field -- the message uid is exposed as `id`.
 * shortId is captured for readability only and is NEVER a key: it is not
 * unique across the corpus, and a shortId-keyed comparison has already
 * reported a false clean diff on issues that differed by eight members. */
function toFacts(m) {
  return {
    uid: m.id,
    shortId: m.shortId ?? null,
    publishDateFrom: m.publishDateFrom ?? null,
    publishDateTo: m.publishDateTo ?? null,
    status: m.status ?? null,
    type: m.type ?? null,
    mainType: m.mainType ?? null,
    seriesId: m.messageSeries?.seriesId ?? null,
  };
}

/* Byte-reproducibility: members sorted by uid, keys in a fixed order, no
 * capture timestamp anywhere, LF endings, one trailing newline. */
function serialise(fixture) {
  return JSON.stringify(fixture, null, 2).replace(/\r\n/g, '\n') + '\n';
}

const args = process.argv.slice(2);
const outDir = args.includes('--out') ? args[args.indexOf('--out') + 1] : join('niord-core', 'src', 'test', 'resources', 'fixtures', 'publications');
const only = args.includes('--only') ? args[args.indexOf('--only') + 1] : null;

mkdirSync(outDir, { recursive: true });
const hashes = [];
const manifestTags = [];
const manifestOther = [];
let failed = 0;

for (const name of only ? [only] : TAGS) {
  try {
    const tag = resolveTag(name);
    const members = fetchMembers(tag).map(toFacts).sort((a, b) => (a.uid < b.uid ? -1 : a.uid > b.uid ? 1 : 0));

    const uids = new Set(members.map((m) => m.uid));
    if (uids.size !== members.length) throw new Error(`${name}: duplicate uid in member set`);

    const text = serialise({
      tag: tag.name,
      tagId: tag.tagId,
      locked: tag.locked,
      messageCount: tag.messageCount,
      tagCreated: tag.created, // the cut-off recovery source
      source: HOST,
      synthetic: false,
      members,
    });
    const file = `${name}.json`;
    writeFileSync(join(outDir, file), text, 'utf8');
    hashes.push(`${createHash('sha256').update(text, 'utf8').digest('hex')}  ${file}`);

    // Both silent-failure guards recorded as values, so a later re-capture can be
    // checked rather than trusted. tagCreated is the cut-off recovery source.
    manifestTags.push({
      tag: tag.name,
      tagId: tag.tagId,
      locked: tag.locked,
      messageCount: tag.messageCount,
      created: tag.created,
      fixture: `${name}.json`,
      memberCount: members.length,
    });

    const distinctShort = new Set(members.map((m) => m.shortId)).size;
    console.log(`  ok  ${name.padEnd(16)} ${String(members.length).padStart(4)} members` + (distinctShort !== members.length ? `  (only ${distinctShort} distinct shortIds -- uid keying is load-bearing)` : ''));
  } catch (e) {
    failed++;
    console.error(`  FAIL ${name}: ${e.message}`);
  }
}


/* The corpus guard.
 *
 * Tags carry a messageCount to check a fetch against. Publications do not, and
 * an unrecognised publication id does not error -- the filter is dropped and
 * the whole default corpus comes back looking like a perfectly ordinary answer.
 * So fetch that corpus once, and treat any capture that reproduces it exactly
 * as a dropped filter rather than a result. */
const corpus = new Set(
  (getJson(`${HOST}/rest/messages/search?domain=${DOMAIN}&maxSize=1000`).data ?? []).map((m) => m.id),
);
const sameAsCorpus = (uids) => uids.length === corpus.size && uids.every((u) => corpus.has(u));

function writeFixture(name, fixture) {
  const text = serialise(fixture);
  writeFileSync(join(outDir, `${name}.json`), text, 'utf8');
  hashes.push(`${createHash('sha256').update(text, 'utf8').digest('hex')}  ${name}.json`);
}

if (!only) {
  for (const pub of PUBLICATIONS) {
    try {
      const res = getJson(`${HOST}/rest/messages/search?domain=${DOMAIN}&publication=${pub.id}&maxSize=1000`);
      const data = res.data ?? [];
      if (data.length !== res.total) throw new Error(`returned ${data.length} of ${res.total}; raise maxSize`);
      const members = data.map(toFacts).sort((a, b) => (a.uid < b.uid ? -1 : a.uid > b.uid ? 1 : 0));
      if (sameAsCorpus(members.map((m) => m.uid))) {
        throw new Error(`result is identical to the unfiltered corpus (${corpus.size}) -- the publication filter was dropped`);
      }
      writeFixture(pub.case, {
        case: pub.case,
        kind: 'publication',
        publicationId: pub.id,
        memberCount: members.length,
        source: HOST,
        synthetic: false,
        members,
      });
      manifestOther.push({ case: pub.case, kind: 'publication', publicationId: pub.id, memberCount: members.length, fixture: `${pub.case}.json` });
      console.log(`  ok  ${pub.case.padEnd(24)} ${String(members.length).padStart(4)} members`);
    } catch (e) {
      failed++;
      console.error(`  FAIL ${pub.case}: ${e.message}`);
    }
  }

  for (const msg of MESSAGES) {
    try {
      // A 204 is a result, not a failure. curl --fail does not treat it as one.
      const body = execFileSync('curl.exe', ['-sS', '--retry', '3', `${HOST}/rest/messages/message/${msg.shortId}`], {
        encoding: 'utf8',
        maxBuffer: 16 * 1024 * 1024,
      });
      const absent = body.trim() === '';
      const fixture = absent
        ? { case: msg.case, kind: 'message', shortId: msg.shortId, absent: true, source: HOST, synthetic: false }
        : { case: msg.case, kind: 'message', shortId: msg.shortId, absent: false, source: HOST, synthetic: false, facts: toFacts(JSON.parse(body)) };
      writeFixture(msg.case, fixture);
      manifestOther.push({ case: msg.case, kind: 'message', shortId: msg.shortId, absent, fixture: `${msg.case}.json` });
      console.log(`  ok  ${msg.case.padEnd(24)} ${absent ? 'ABSENT (no such message)' : 'captured'}`);
    } catch (e) {
      failed++;
      console.error(`  FAIL ${msg.case}: ${e.message}`);
    }
  }
}


if (!only) {
  /* Synthetic fixtures are hand-authored, never written by this script, but they
   * are hashed alongside the captured ones so an accidental edit shows up as
   * drift rather than passing unnoticed. */
  for (const f of readdirSync(outDir).filter((n) => n.startsWith('synthetic-') && n.endsWith('.json')).sort()) {
    const text = readFileSync(join(outDir, f), 'utf8');
    hashes.push(`${createHash('sha256').update(text, 'utf8').digest('hex')}  ${f}`);
  }
  writeFileSync(
    join(outDir, 'manifest.json'),
    serialise({
      source: HOST,
      domain: DOMAIN,
      tags: manifestTags,
      other: manifestOther,
      blocklist: BLOCKLIST,
    }),
    'utf8',
  );
  const manifestText = readFileSync(join(outDir, 'manifest.json'), 'utf8');
  hashes.push(`${createHash('sha256').update(manifestText, 'utf8').digest('hex')}  manifest.json`);

  hashes.sort();
  writeFileSync(join(outDir, 'hashes.txt'), hashes.join('\n') + '\n', 'utf8');
  console.log(`\nwrote ${hashes.length} fixtures + hashes.txt to ${outDir}`);
}
if (failed) {
  console.error(`\n${failed} tag(s) failed -- fixtures NOT trustworthy`);
  process.exit(1);
}
