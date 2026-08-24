-- Flyway migration V5: the shadow-diff results table (B6.2).
--
-- The shadow-diff resolves each new LEGACY release in the new engine and diffs
-- against what legacy actually recorded. Its results have to survive restarts
-- and accumulate over weeks, because B6.3's cutover precondition is "two
-- consecutive green weeks per series" -- a claim about history, which nothing
-- held in memory can make.
--
-- One row per (legacy publication, release stamp). Keying on the stamp as well
-- as the publication is what makes a re-run idempotent AND keeps a re-release
-- as its own row: a publication that is retired and republished is a second
-- release and its diff is a second data point, not a correction of the first.
--
-- The deltas are stored as TEXT holding a JSON array of uids rather than in a
-- child table. They are small -- the measured divergence classes run to single
-- digits per issue -- and a child table would add a join and a second id
-- allocation per uid to a job that runs every hour. Where a delta is genuinely
-- large the counts carry the signal and the endpoint can say "truncated".
--
-- Deliberately NOT foreign-keyed to Publication or PublicationSeries. A diff is
-- evidence about a moment, and it has to stay readable after the legacy row is
-- deleted or the series is renamed -- that is exactly when somebody wants it.

create table ShadowDiffRun (
    id integer not null,
    version integer,
    created datetime(6),
    updated datetime(6),

    legacyPublicationId varchar(36) not null,
    legacyUpdatedAt datetime(6),
    seriesId varchar(64),

    comparedAt datetime(6) not null,
    intervalFrom datetime(6),
    cutoffAt datetime(6),

    green bit not null,
    skipReason varchar(64),

    missingCount integer not null,
    extraCount integer not null,
    missingUids TEXT,
    extraUids TEXT,

    primary key (id)
) engine=InnoDB;

-- One diff per release, so the job can re-run without duplicating rows.
alter table ShadowDiffRun
    add constraint UK_shadowdiff_publication_stamp unique (legacyPublicationId, legacyUpdatedAt);

-- The two reads B6.3 makes: everything for one series, newest first.
create index shadowdiff_series_k on ShadowDiffRun (seriesId, comparedAt);
