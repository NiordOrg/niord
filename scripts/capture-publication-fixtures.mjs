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
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const HOST = 'https://niord.t-dma.dk';
const DOMAIN = 'niord-nm';

/* The weekly tags. Positive controls first: these two are the validated pair
 * and their counts (123 / 2) are what the acceptance check pins. */
const TAGS = [
  'nm-pt-w01-2026',
  'nm-w01-2026',
  ...[23, 24, 25, 26, 27, 28].flatMap((w) => [`nm-w${String(w).padStart(2, '0')}-2026`, `nm-pt-w${String(w).padStart(2, '0')}-2026`]),
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

    const distinctShort = new Set(members.map((m) => m.shortId)).size;
    console.log(`  ok  ${name.padEnd(16)} ${String(members.length).padStart(4)} members` + (distinctShort !== members.length ? `  (only ${distinctShort} distinct shortIds -- uid keying is load-bearing)` : ''));
  } catch (e) {
    failed++;
    console.error(`  FAIL ${name}: ${e.message}`);
  }
}

if (!only) {
  hashes.sort();
  writeFileSync(join(outDir, 'hashes.txt'), hashes.join('\n') + '\n', 'utf8');
  console.log(`\nwrote ${hashes.length} fixtures + hashes.txt to ${outDir}`);
}
if (failed) {
  console.error(`\n${failed} tag(s) failed -- fixtures NOT trustworthy`);
  process.exit(1);
}
