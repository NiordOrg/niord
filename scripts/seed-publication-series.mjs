#!/usr/bin/env node
/**
 * Seeds one publication series and one published issue, through the real API.
 *
 * WHY THIS EXISTS. Everything on the series and issue surface was reachable
 * EXCEPT creation: the series create endpoint was a stub and the issue service
 * had no create route at all. So the whole new REST surface had never been
 * exercised against a running system, and the first thing to touch it would
 * have been the historical importer -- 1,077 publications, all-or-nothing,
 * against endpoints nobody had ever called. This walks the same path first,
 * with one row.
 *
 * It drives the PUBLIC endpoints deliberately. A seed that reaches past the API
 * into the entity layer proves the entity layer works, which was never in
 * doubt; what needs proving is that the wire shapes, the validator, the
 * transactions and the security annotations line up.
 *
 * WHAT IT PROVES, in order, failing loudly at the first step that does not:
 *
 *   1. a series can be created from a value object            (S8)
 *   2. the canonical citation format is ACCEPTED              (S-13 + S-14)
 *   3. the series can be activated, which runs the validator  (S10, S-17)
 *   4. an issue can be created on it                          (I1)
 *   5. the issue can be published                             (the transaction)
 *   6. and it reports whether the issue reaches the PUBLIC list  (the union)
 *
 * Step 6 currently reports a GAP rather than passing, and that is the most
 * useful thing this script does: publicAuthority can be set at create and never
 * changed afterwards, so there is no way to flip an existing series from LEGACY
 * to NEW. That flip is the whole of B7.1. The public adapter has therefore never
 * served a NEW-authority row anywhere but a test, and cannot be made to.
 *
 * Usage:
 *   node scripts/seed-publication-series.mjs [--keep] [--base <url>]
 *
 *   --keep   leave the series behind. Without it the seed deletes what it made,
 *            so running it twice is safe and it leaves no fixture pretending to
 *            be real data.
 */

const args = process.argv.slice(2);
const BASE = args.includes('--base') ? args[args.indexOf('--base') + 1] : 'https://niord.t-dma.dk/rest';
const KEEP = args.includes('--keep');
const KEYCLOAK = (process.env.NIORD_KEYCLOAK
    || 'https://login.t-dma.dk/auth/realms/niord/protocol/openid-connect/token');
const USER = process.env.NIORD_USER || 'test-sysadmin';
const PASS = process.env.NIORD_PASS || 'test1234';

// A recognisable id, so anything this leaves behind is obviously a seed.
const SERIES_ID = 'seed-probe-series';

let token;
let step = 0;

function ok(what, detail) {
    console.log(`  ${String(++step).padStart(2)}. PASS  ${what}${detail ? '  -- ' + detail : ''}`);
}

function fail(what, detail) {
    console.error(`  ${String(++step).padStart(2)}. FAIL  ${what}\n        ${detail}`);
    process.exitCode = 1;
    throw new Error(what);
}

async function call(method, path, body, expect = [200, 204]) {
    const res = await fetch(BASE + path, {
        method,
        headers: {
            Authorization: `Bearer ${token}`,
            ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    if (!expect.includes(res.status)) {
        throw new Error(`${method} ${path} -> ${res.status}\n        ${text.slice(0, 400)}`);
    }
    return text ? JSON.parse(text) : null;
}

async function authenticate() {
    const res = await fetch(KEYCLOAK, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            grant_type: 'password', client_id: 'niord-web', username: USER, password: PASS,
        }),
    });
    if (!res.ok) {
        throw new Error(`token request failed: ${res.status}`);
    }
    token = (await res.json()).access_token;
}

/** A category the public list actually serves, so step 6 can see the issue. */
async function publishingCategory() {
    const cats = await call('GET', '/publication-categories/all?lang=da');
    const publishing = (Array.isArray(cats) ? cats : cats.data || []).find(c => c.publish);
    if (!publishing) {
        throw new Error('no publishing publication category exists; step 6 could not distinguish '
            + 'a correct empty list from a broken one');
    }
    return publishing.categoryId;
}

/**
 * Removes what this run made.
 *
 * The issues are deleted by the ids this process created, because there is no
 * endpoint that lists the issues of a series -- so a previous run that died
 * between creating an issue and deleting it leaves one behind, and the series
 * delete then refuses with SERIES_HAS_ISSUES. That is the correct refusal; it
 * just means the leftovers need clearing by hand. The message says so rather
 * than swallowing it.
 */
async function cleanup(createdIssueIds = []) {
    try {
        for (const publicId of createdIssueIds) {
            await call('DELETE', `/publication-issues/issue/${publicId}`, undefined,
                [204, 200, 400, 404, 409]);
        }
        const existing = await call('GET', `/publication-series/series/${SERIES_ID}`,
            undefined, [200, 404, 400]);
        if (existing && existing.seriesId) {
            await call('DELETE', `/publication-series/series/${SERIES_ID}`, undefined,
                [204, 200, 400, 404, 409]);
        }
    } catch (e) {
        console.log(`  --. NOTE  cleanup incomplete: ${e.message.split('\n')[0]}`);
        console.log('        a published issue cannot be deleted, which is correct -- clear the '
            + 'leftover series by hand before re-running');
    }
}

async function main() {
    console.log(`seeding against ${BASE}\n`);
    await authenticate();
    await cleanup();


    const categoryId = await publishingCategory();

    // 1. Create.
    const created = await call('POST', '/publication-series/series/', {
        seriesId: SERIES_ID,
        categoryId,
        contentMode: 'GENERATED_FROM_QUERY',
        cadence: 'WEEKLY',
        numberingScheme: 'ISO_WEEK_YEAR',
        timeRelation: 'PUBLISHED_IN_INTERVAL',
        aliveAtCutoff: false,
        nominalCutoffDay: 'WEDNESDAY',
        nominalCutoffTime: '12:00',
        nominalCutoffTimeZone: 'Europe/Copenhagen',
        firstIssueStartsAt: Date.parse('2026-08-01T00:00:00Z'),
        releaseMode: 'MANUAL_GATE',
        nextIssueCreation: 'MANUAL',
        messagePublication: 'EXTERNAL',
        publicAuthority: 'LEGACY',
        languages: ['da'],
        criteria: { criteria: [{ type: 'MESSAGE_SERIES', values: ['dma-nm'] }] },
        descs: [{
            lang: 'da',
            name: 'Seed probe',
            fileNamePattern: 'seed-${week}-${year}.pdf',
            // The format that S-14 used to reject while S-13 required one. This
            // step is the live proof of that fix; the unit test cannot reach the
            // endpoint and the endpoint could not reach the validator.
            messageReferenceFormat: 'Seed ${week}/${year} ${parameters}',
        }],
    });
    ok('series created from a value object', `status ${created.status}`);
    ok('the canonical citation format was accepted', 'S-13 + S-14 no longer deadlock');

    // 2. Activate -- this is what runs the validator.
    const active = await call('PUT', `/publication-series/series/${SERIES_ID}/status`, 'ACTIVE');
    if (active.status !== 'ACTIVE') {
        fail('series activation', `status is ${active.status}`);
    }
    ok('series activated', 'the validator passed a complete series');

    // 3. Create an issue.
    const issue = await call('POST', '/publication-issues/issue', {
        seriesId: SERIES_ID,
        intervalFrom: Date.parse('2026-08-05T12:00:00Z'),
        recovered: false,
    });
    if (!/^[0-9a-f-]{36}$/.test(issue.publicId)) {
        fail('publicId shape', `${issue.publicId} is not a lowercase UUID`);
    }
    ok('issue created', `publicId ${issue.publicId}`);

    // 4. Publish it.
    const published = await call('PUT', `/publication-issues/issue/${issue.publicId}/publish`, {
        acknowledgeWarnings: true,
        acknowledged: [],
        stamp: Date.parse('2026-08-12T12:00:00Z'),
    });
    ok('issue published', `members ${published.memberCount ?? '?'}`);

    // 5. The union. It cannot run yet, and that is the finding.
    //
    // publicAuthority is settable only on create, and create forces DRAFT then
    // ACTIVE -- there is NO endpoint anywhere that flips an existing series from
    // LEGACY to NEW. That flip is the whole of B7.1, so as things stand cutover
    // has no API. Recorded here rather than worked around, because a seed that
    // reached past the API to set a column would hide exactly this.
    const at = Date.parse('2026-08-12T13:00:00Z');
    const publicList = await fetch(
        `${BASE}/public/v1/publications?lang=da&from=${at}&to=${at}`).then(r => r.json());
    const served = publicList.some(pub => pub.publicationId === issue.publicId);

    if (served) {
        ok('the published issue is on the PUBLIC list',
            'the new half of the union serves a real row');
    } else {
        console.log('  --. GAP   the issue is PUBLISHED but its series is still LEGACY, and there is '
            + 'no\n        endpoint to flip publicAuthority. The union step cannot run, and\n'
            + '        B7.1 -- the cutover itself -- has no API. This is the seed\n'
            + '        reporting a missing capability, not a failure.');
    }

    if (!KEEP) {
        await cleanup([issue.publicId]);
        console.log('\n  cleaned up. Pass --keep to leave the series behind.');
    } else {
        console.log(`\n  left behind: series ${SERIES_ID}`);
    }

    console.log('\nseed complete.');
}

main().catch(e => {
    console.error('\nSEED FAILED:', e.message);
    process.exit(1);
});
