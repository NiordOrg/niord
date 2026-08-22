/*
 * Seeds a developer database with the real message corpus, pulled from the
 * public API.
 *
 *     node scripts/seed-dev-database.mjs [--host localhost] [--port 13306]
 *                                        [--database niord] [--dry-run]
 *
 * Why this exists: the tests that matter most for the resolver -- the
 * differential test in particular -- need real messages narrowed by real SQL.
 * Without them the suite can only be exercised against synthetic input, and a
 * developer machine has no data of its own. Getting a database dump means
 * waiting on someone; the corpus turns out to be publicly readable, so this
 * waits on nobody.
 *
 * It is a DEVELOPER tool. It writes to a local database, never to a deployed
 * one, and it is never run by CI.
 *
 * The one trap it has to work around: `status` is silently ignored unless
 * `domain` is also supplied. Ask for CANCELLED without a domain and the API
 * cheerfully returns the default published corpus instead -- 277 rows that look
 * entirely plausible. Every fetch here therefore sends both, and asserts the
 * result actually carries the status it asked for.
 */
import { execFileSync } from 'node:child_process';
import { writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const HOST = 'https://niord.t-dma.dk';
/* Every domain that carries a distinct corpus. Seeding only niord-nm was a
 * mistake worth recording: it silently excludes dma-fa, which is where the
 * firing-areas fixtures live AND where shortIds actually collide -- 357
 * messages under 331 distinct shortIds. A domain name the backend does not
 * recognise does not error either; it returns the 277-row default corpus, so
 * each domain is asserted to bring its own series. */
const DOMAINS = [
  { domain: 'niord-nm', expectSeries: 'dma-nm' },
  { domain: 'niord-fa', expectSeries: 'dma-fa' },
  { domain: 'niord-nw', expectSeries: 'dma-nw' },
  { domain: 'niord-almanac', expectSeries: 'dma-nm-almanac' },
];
const PAGE_SIZE = 1000;

/* The three PUBLIC statuses, and only those.
 *
 * DRAFT and VERIFIED are not readable anonymously: asking for them returns the
 * published default instead, silently, which the status guard below catches.
 * That is a permission boundary rather than a defect, and it costs nothing here
 * -- membership only ever considers Status.isPublic(), so a draft could not be a
 * member anyway. A test that needs to prove a draft is EXCLUDED must therefore
 * author its own row; it cannot seed one.
 *
 * DELETED is absent for a different reason: it is not part of any membership
 * question and only adds noise.
 */
const STATUSES = ['PUBLISHED', 'EXPIRED', 'CANCELLED'];

const arg = (name, fallback) => {
  const i = process.argv.indexOf('--' + name);
  return i > -1 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
};
const DB_CONTAINER = arg('container', 'niord-test-db');
const DATABASE = arg('database', 'niord');
const DRY_RUN = process.argv.includes('--dry-run');

function getJson(url) {
  const body = execFileSync('curl.exe', ['-sS', '--fail', '--retry', '3', url], {
    encoding: 'utf8',
    maxBuffer: 256 * 1024 * 1024,
  });
  try {
    return JSON.parse(body);
  } catch {
    throw new Error(`not JSON from ${url}: ${body.slice(0, 200)}`);
  }
}

/** Fetches every message of one status, paging until the corpus is exhausted. */
function fetchStatus(domain, status) {
  const out = [];
  let page = 0;
  let total = null;

  for (;;) {
    const url = `${HOST}/rest/messages/search?domain=${domain}&status=${status}`
      + `&maxSize=${PAGE_SIZE}&page=${page}`;
    const res = getJson(url);
    const data = res.data ?? [];
    if (total === null) total = res.total;

    // The guard. A dropped status filter returns the default corpus, which
    // looks like a perfectly ordinary answer until something downstream is
    // quietly wrong.
    const wrong = data.filter((m) => m.status !== status);
    if (wrong.length) {
      throw new Error(
        `asked for ${status} and got ${wrong.length} row(s) of other statuses `
        + `(${[...new Set(wrong.map((m) => m.status))].join(', ')}) -- the status filter was dropped`);
    }

    out.push(...data);
    if (data.length === 0 || out.length >= total) break;
    page++;
    if (page > 100) throw new Error(`${status}: paging did not terminate`);
  }

  if (out.length !== total) {
    console.warn(`  ! ${status}: collected ${out.length} of a declared ${total}`);
  }
  return out;
}

const sql = (v) => (v === null || v === undefined ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`);
const ts = (ms) => (ms === null || ms === undefined ? 'NULL' : `FROM_UNIXTIME(${Math.floor(ms / 1000)})`);

console.log(`seeding ${DATABASE} in container ${DB_CONTAINER} from ${HOST}`);
console.log();

const messages = [];
for (const { domain, expectSeries } of DOMAINS) {
  process.stdout.write(`  ${domain.padEnd(16)} `);
  const before = messages.length;
  for (const status of STATUSES) messages.push(...fetchStatus(domain, status));
  const got = messages.slice(before);

  // An unrecognised domain returns the default corpus rather than erroring, so
  // check the rows really came from the series this domain is supposed to hold.
  const series = new Set(got.map((m) => m.messageSeries && m.messageSeries.seriesId));
  if (!series.has(expectSeries)) {
    throw new Error(`${domain}: expected ${expectSeries}, got [${[...series].join(', ')}] -- domain not recognised`);
  }
  console.log(`${String(got.length).padStart(6)} messages  (${[...series].join(', ')})`);
}

// uid is the key, never shortId -- shortId is not unique and nothing prevents reuse.
const byUid = new Map();
for (const m of messages) byUid.set(m.id, m);
const unique = [...byUid.values()].sort((a, b) => (a.id < b.id ? -1 : 1));

const seriesIds = [...new Set(unique.map((m) => m.messageSeries?.seriesId).filter(Boolean))].sort();

console.log();
console.log(`  ${unique.length} distinct messages, ${seriesIds.length} message series`);
console.log(`  distinct shortIds: ${new Set(unique.map((m) => m.shortId)).size}`);

/* Ids come from the shared sequence in production. Here they are assigned from
 * a high offset so they cannot collide with anything a test inserts through
 * Hibernate, which draws from hibernate_sequence starting low. */
const ID_BASE = 900_000;
const seriesRowId = new Map(seriesIds.map((s, i) => [s, ID_BASE + i]));

const lines = [];
lines.push('SET FOREIGN_KEY_CHECKS = 0;');
lines.push('DELETE FROM Message WHERE id >= ' + ID_BASE + ';');
lines.push('DELETE FROM MessageSeries WHERE id >= ' + ID_BASE + ';');

for (const s of seriesIds) {
  const mainType = unique.find((m) => m.messageSeries?.seriesId === s)?.mainType ?? 'NM';
  lines.push(
    `INSERT INTO MessageSeries (id, version, seriesId, mainType) VALUES `
    + `(${seriesRowId.get(s)}, 0, ${sql(s)}, ${sql(mainType)});`);
}

let nextId = ID_BASE + seriesIds.length;
for (const m of unique) {
  const id = nextId++;
  lines.push(
    `INSERT INTO Message (id, version, revision, autoTitle, hasGeometry, uid, repoPath, shortId, `
    + `number, year, messageSeries_id, publishDateFrom, publishDateTo, status, type, mainType) VALUES (`
    + [
      id, 0, 1, 0, 0,
      sql(m.id),
      sql('messages/seed/' + m.id),
      sql(m.shortId ?? null),
      m.number ?? 'NULL',
      m.year ?? 'NULL',
      m.messageSeries?.seriesId ? seriesRowId.get(m.messageSeries.seriesId) : 'NULL',
      ts(m.publishDateFrom),
      ts(m.publishDateTo),
      sql(m.status),
      sql(m.type ?? null),
      sql(m.mainType ?? null),
    ].join(', ') + ');');
}
lines.push('SET FOREIGN_KEY_CHECKS = 1;');

const scratch = join(tmpdir(), 'niord-seed');
mkdirSync(scratch, { recursive: true });
const file = join(scratch, 'seed.sql');
writeFileSync(file, lines.join('\n') + '\n', 'utf8');
console.log(`  ${lines.length} statements written to ${file}`);

if (DRY_RUN) {
  console.log('\n--dry-run: nothing was applied.');
  process.exit(0);
}

console.log('\n  applying...');
execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'mysql', '-uroot', '-pmysql', DATABASE], {
  input: lines.join('\n') + '\n',
  stdio: ['pipe', 'inherit', 'pipe'],
  maxBuffer: 256 * 1024 * 1024,
});

const count = execFileSync('docker',
  ['exec', DB_CONTAINER, 'mysql', '-uroot', '-pmysql', DATABASE, '-N', '-e',
    'SELECT CONCAT((SELECT COUNT(*) FROM Message), " messages, ", (SELECT COUNT(*) FROM MessageSeries), " series");'],
  { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim();
console.log(`  done: ${count}`);
