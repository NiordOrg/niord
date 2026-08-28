#!/usr/bin/env node
/**
 * Captures the whole legacy publication estate from the test environment.
 *
 * WHAT THIS IS FOR. Phase B5 imports 1,077 legacy publications into the series/
 * issue model. Its source of truth is what the legacy recorder actually wrote:
 * each publication's system-level fields, and the locked MessageTag it resolved
 * to. B6 then shadow-diffs against the same corpus. Both need a FROZEN copy --
 * an importer verified against a moving target is verified against nothing.
 *
 * WHY THE TEST ENVIRONMENT AND NOT PRODUCTION. niord.t-dma.dk is a static full
 * production snapshot taken 17.08.2026. Production moves; a re-capture from it
 * would silently change expectations under a passing test, which is the failure
 * mode fixtures exist to prevent.
 *
 * THE TRAPS THIS SCRIPT GUARDS, all of which have bitten before:
 *
 *   1. `tag=` must be the tag UUID, not its name. Passing a name does not error:
 *      the filter is silently dropped and the unfiltered domain corpus comes
 *      back. Measured against this environment, the wrong answer is 188 -- and a
 *      deliberately bogus tag id returns the identical 188, which is the
 *      mechanism confirmed rather than inferred.
 *
 *   2. The message search DEFAULTS to status=PUBLISHED when no status is given.
 *      Historical members are mostly EXPIRED or CANCELLED -- all 2,324 members of
 *      the blank-era issues are, and not one is PUBLISHED -- so an unqualified
 *      capture silently loses almost everything.
 *
 *   3. A tag that is not locked is not an oracle. An unlocked tag can still be
 *      written to, so what it holds today is not what was published.
 *
 *   4. total must equal the tag's own messageCount. If they disagree, something
 *      filtered the result and the capture is a subset wearing the right shape.
 *
 * Every one of these produces a plausible-looking file rather than an error, so
 * each is asserted rather than trusted.
 *
 * Output is byte-deterministic: sorted keys, sorted arrays, LF endings, UTC. Run
 * it twice and the files are identical, so a diff means the DATA changed.
 *
 * Usage:
 *   node scripts/capture-legacy-publication-estate.mjs [--out <dir>] [--members]
 *
 *   --members   also capture each publication's frozen member list (~1,000 extra
 *               requests, several minutes). Without it, only the estate is taken.
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

// No defaults for the target or the login. A script that can authenticate and
// write against a live system with no arguments at all is one accident away
// from doing so, and a password in a file is a password in every clone.
const API = process.env.NIORD_API;
const KEYCLOAK = process.env.NIORD_KEYCLOAK;
const USER = process.env.NIORD_USER;
const PASS = process.env.NIORD_PASS;
if (!API || !KEYCLOAK || !USER || !PASS) {
  console.error('set NIORD_API, NIORD_KEYCLOAK, NIORD_USER and NIORD_PASS in the environment');
  process.exit(2);
}

const args = process.argv.slice(2);
const outDir = args.includes('--out')
    ? args[args.indexOf('--out') + 1]
    : path.join('niord-core', 'src', 'test', 'resources', 'fixtures', 'legacy-estate');
const withMembers = args.includes('--members');

const STATUSES = ['ACTIVE', 'DRAFT', 'RECORDING', 'INACTIVE'];

// Historical members are mostly not PUBLISHED. Naming every public status
// explicitly is the only way to get them; the default is PUBLISHED alone.
const MEMBER_STATUSES = ['PUBLISHED', 'EXPIRED', 'CANCELLED'];

/** Sorts object keys recursively so the JSON is stable across runs. */
function canonical(value) {
    if (Array.isArray(value)) {
        return value.map(canonical);
    }
    if (value && typeof value === 'object') {
        const out = {};
        for (const key of Object.keys(value).sort()) {
            out[key] = canonical(value[key]);
        }
        return out;
    }
    return value;
}

function writeJson(file, data) {
    const text = JSON.stringify(canonical(data), null, 2) + '\n';
    fs.writeFileSync(file, text, 'utf8');
    return crypto.createHash('sha256').update(text, 'utf8').digest('hex');
}

async function token() {
    const res = await fetch(KEYCLOAK, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            grant_type: 'password', client_id: 'niord-web', username: USER, password: PASS,
        }),
    });
    if (!res.ok) {
        throw new Error(`token request failed: ${res.status} ${await res.text()}`);
    }
    const json = await res.json();
    if (!json.access_token) {
        throw new Error('token response carried no access_token');
    }
    return json.access_token;
}

async function get(url, auth, extraHeaders = {}) {
    const res = await fetch(url, { headers: { Authorization: `Bearer ${auth}`, ...extraHeaders } });
    if (!res.ok) {
        throw new Error(`GET ${url} -> ${res.status}`);
    }
    return res.json();
}

async function main() {
    fs.mkdirSync(outDir, { recursive: true });
    const auth = await token();
    const hashes = {};

    // ---------------------------------------------------------------- the estate
    const estate = {};
    for (const mainType of ['PUBLICATION', 'TEMPLATE']) {
        const qs = new URLSearchParams({ mainType, maxSize: '5000' });
        STATUSES.forEach(s => qs.append('status', s));

        const page = await get(`${API}/publications/search-details?${qs}`, auth);

        if (page.data.length !== page.total) {
            throw new Error(`${mainType}: got ${page.data.length} of ${page.total}; raise maxSize`);
        }
        estate[mainType] = page.data.sort((a, b) => a.publicationId.localeCompare(b.publicationId));
        console.log(`${mainType.padEnd(11)} ${page.total}`);
    }

    if (estate.PUBLICATION.length !== 1077) {
        console.warn(`  ! expected 1077 publications, captured ${estate.PUBLICATION.length}. `
            + `MODEL-VERIFICATION.md is written against 1077 -- if this is a different snapshot, `
            + `every count in that document is about different data.`);
    }

    hashes['publications.json'] = writeJson(path.join(outDir, 'publications.json'), estate.PUBLICATION);
    hashes['templates.json'] = writeJson(path.join(outDir, 'templates.json'), estate.TEMPLATE);

    // ------------------------------------------------------------- the shape of it
    const withTag = estate.PUBLICATION.filter(p => p.messageTag && p.messageTag.tagId);
    const tagUsers = {};
    withTag.forEach(p => {
        (tagUsers[p.messageTag.tagId] ||= []).push(p.publicationId);
    });
    const shared = Object.entries(tagUsers).filter(([, ids]) => ids.length > 1);

    const filters = {};
    estate.PUBLICATION.forEach(p => {
        const f = p.messageTagFilter == null ? '(null)' : p.messageTagFilter;
        filters[f] = (filters[f] || 0) + 1;
    });

    const profile = {
        capturedFrom: API,
        snapshot: 'static full-production snapshot of 17.08.2026',
        publications: estate.PUBLICATION.length,
        templates: estate.TEMPLATE.length,
        byStatus: count(estate.PUBLICATION, p => p.status),
        byType: count(estate.PUBLICATION, p => p.type),
        byMessagePublication: count(estate.PUBLICATION, p => p.messagePublication),
        withMessageTag: withTag.length,
        activeWithoutMessageTag: estate.PUBLICATION
            .filter(p => p.status === 'ACTIVE' && !(p.messageTag && p.messageTag.tagId)).length,
        withoutDomain: estate.PUBLICATION.filter(p => !p.domain).length,
        sharedTags: shared.map(([tagId, ids]) => ({ tagId, publications: ids.sort() }))
            .sort((a, b) => a.tagId.localeCompare(b.tagId)),
        distinctMessageTagFilters: Object.entries(filters)
            .map(([filter, n]) => ({ filter, publications: n }))
            .sort((a, b) => b.publications - a.publications),
    };
    hashes['profile.json'] = writeJson(path.join(outDir, 'profile.json'), profile);

    console.log(`\n  with a message tag        ${profile.withMessageTag}`);
    console.log(`  ACTIVE with NO tag        ${profile.activeWithoutMessageTag}`);
    console.log(`  no domain                 ${profile.withoutDomain}`);
    console.log(`  shared tags               ${profile.sharedTags.length}`
        + (profile.sharedTags.length
            ? ` (max ${Math.max(...profile.sharedTags.map(s => s.publications.length))} publications on one tag)`
            : ''));
    console.log(`  distinct tag filters      ${profile.distinctMessageTagFilters.length}`);

    // ---------------------------------------------------------------- the members
    if (withMembers) {
        console.log(`\ncapturing members for ${withTag.length} publications...`);
        const members = {};
        const anomalies = [];
        let done = 0;

        for (const p of withTag) {
            const tag = p.messageTag;

            // Trap 3: an unlocked tag can still be written to.
            if (tag.locked === false) {
                anomalies.push({ publicationId: p.publicationId, tagId: tag.tagId, why: 'tag not locked' });
            }

            const qs = new URLSearchParams({ tag: tag.tagId, maxSize: '5000' });
            MEMBER_STATUSES.forEach(s => qs.append('status', s));
            if (p.domain) {
                qs.set('domain', p.domain.domainId);
            }

            let page;
            try {
                page = await get(`${API}/messages/search?${qs}`, auth,
                    p.domain ? { NiordDomain: p.domain.domainId } : {});
            } catch (e) {
                anomalies.push({ publicationId: p.publicationId, tagId: tag.tagId, why: String(e) });
                continue;
            }

            // Trap 4: the count must agree with the tag's own. A disagreement means
            // something filtered the result, and a subset looks exactly like a
            // complete capture.
            if (typeof tag.messageCount === 'number' && page.total !== tag.messageCount) {
                anomalies.push({
                    publicationId: p.publicationId, tagId: tag.tagId,
                    why: `total ${page.total} != tag.messageCount ${tag.messageCount}`,
                });
            }

            members[p.publicationId] = {
                tagId: tag.tagId,
                tagName: tag.name ?? null,
                locked: tag.locked ?? null,
                messageCount: tag.messageCount ?? null,
                returned: page.total,
                uids: page.data.map(m => m.id ?? m.uid).filter(Boolean).sort(),
                shortIds: page.data.map(m => m.shortId).filter(Boolean).sort(),
            };

            if (++done % 100 === 0) {
                console.log(`  ${done}/${withTag.length}`);
            }
        }

        hashes['members.json'] = writeJson(path.join(outDir, 'members.json'), members);
        hashes['member-anomalies.json'] = writeJson(path.join(outDir, 'member-anomalies.json'), anomalies);

        console.log(`\n  captured ${Object.keys(members).length} member lists`);
        console.log(`  anomalies ${anomalies.length}`
            + (anomalies.length ? '  -- see member-anomalies.json; these are FINDINGS, not failures' : ''));
    }

    // ------------------------------------------------------------------- the hashes
    const lines = Object.entries(hashes).sort(([a], [b]) => a.localeCompare(b))
        .map(([f, h]) => `${h}  ${f}`).join('\n') + '\n';
    fs.writeFileSync(path.join(outDir, 'hashes.txt'), lines, 'utf8');

    console.log(`\nwritten to ${outDir}`);
    console.log(lines.trimEnd());
    console.log('\nRe-run and diff: identical hashes mean the capture is reproducible. A changed hash '
        + 'means the DATA changed, which for a static snapshot is itself the finding.');
}

function count(rows, of) {
    const out = {};
    rows.forEach(r => {
        const k = of(r) ?? '(null)';
        out[k] = (out[k] || 0) + 1;
    });
    return Object.fromEntries(Object.entries(out).sort(([a], [b]) => a.localeCompare(b)));
}

main().catch(e => {
    console.error('\ncapture FAILED:', e.message);
    process.exit(1);
});
