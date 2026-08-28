package org.niord.core.publication.series.replay;

import jakarta.validation.constraints.NotNull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;

import org.niord.core.model.VersionedEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One shadow-diff: what the new engine would have produced for a legacy release.
 *
 * Persisted rather than computed on demand because the cutover
 * precondition is "two consecutive green weeks per series" -- a claim about
 * history. Recomputing it later would answer a different question, since the
 * message estate has moved on: type is mutable and unversioned, so re-resolving
 * last month's week today can differ from what the job saw at the time, and
 * that difference is one of the measured divergence classes rather than news.
 *
 * Not foreign-keyed to Publication or PublicationSeries on purpose. This is
 * evidence about a moment and has to stay readable after the legacy row is gone
 * or the series is renamed, which is precisely when somebody goes looking.
 */
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "UK_shadowdiff_publication_stamp",
                columnNames = {"legacyPublicationId", "legacyUpdatedAt"}),
        indexes = @Index(name = "shadowdiff_series_k", columnList = "seriesId, comparedAt"))
@NamedQueries({
        // NEWEST RELEASE first, never newest diff first. The readiness question is
        // "have the last N releases agreed", and a re-diffed old release with a
        // fresh comparedAt sorted to the head of the list and ended every streak.
        // cutoffAt is the release's own instant; a skipped run may carry none, and
        // then the legacy row's stamp stands in.
        @NamedQuery(name = "ShadowDiffRun.bySeries",
                query = "SELECT r FROM ShadowDiffRun r WHERE r.seriesId = :seriesId "
                        + "ORDER BY COALESCE(r.cutoffAt, r.legacyUpdatedAt) DESC, r.comparedAt DESC"),
        @NamedQuery(name = "ShadowDiffRun.all",
                query = "SELECT r FROM ShadowDiffRun r "
                        + "ORDER BY COALESCE(r.cutoffAt, r.legacyUpdatedAt) DESC, r.comparedAt DESC")
})
@SuppressWarnings("unused")
public class ShadowDiffRun extends VersionedEntity<Integer> {

    @NotNull
    @Column(length = 36, nullable = false)
    private String legacyPublicationId;

    /**
     * The release stamp this diff was taken at.
     *
     * Half the idempotency key. A publication that is retired and republished is
     * a SECOND release, and its diff is a second data point rather than a
     * correction of the first -- which matters, because "two consecutive green
     * weeks" counts releases.
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date legacyUpdatedAt;

    @Column(length = 64)
    private String seriesId;

    @Temporal(TemporalType.TIMESTAMP)
    @NotNull
    @Column(nullable = false)
    private Date comparedAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date intervalFrom;

    @Temporal(TemporalType.TIMESTAMP)
    private Date cutoffAt;

    @Column(nullable = false)
    private boolean green;

    /** Set when the release was not compared at all; null when it was. */
    @Column(length = 64)
    private String skipReason;

    @Column(nullable = false)
    private int missingCount;

    @Column(nullable = false)
    private int extraCount;

    /** Recorded-but-not-resolved, as a JSON array of uids. */
    @Column(columnDefinition = "TEXT")
    private String missingUids;

    /** Resolved-but-not-recorded, as a JSON array of uids. */
    @Column(columnDefinition = "TEXT")
    private String extraUids;

    public String getLegacyPublicationId() {
        return legacyPublicationId;
    }

    public void setLegacyPublicationId(String legacyPublicationId) {
        this.legacyPublicationId = legacyPublicationId;
    }

    public Date getLegacyUpdatedAt() {
        return legacyUpdatedAt;
    }

    public void setLegacyUpdatedAt(Date legacyUpdatedAt) {
        this.legacyUpdatedAt = legacyUpdatedAt;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public Date getComparedAt() {
        return comparedAt;
    }

    public void setComparedAt(Date comparedAt) {
        this.comparedAt = comparedAt;
    }

    public Date getIntervalFrom() {
        return intervalFrom;
    }

    public void setIntervalFrom(Date intervalFrom) {
        this.intervalFrom = intervalFrom;
    }

    public Date getCutoffAt() {
        return cutoffAt;
    }

    public void setCutoffAt(Date cutoffAt) {
        this.cutoffAt = cutoffAt;
    }

    public boolean isGreen() {
        return green;
    }

    public void setGreen(boolean green) {
        this.green = green;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public int getExtraCount() {
        return extraCount;
    }

    public String getMissingUids() {
        return missingUids;
    }

    public String getExtraUids() {
        return extraUids;
    }

    /**
     * Records both deltas and everything derived from them together.
     *
     * One setter rather than five, because green, the counts and the uid lists
     * are four views of one fact. Letting a caller set the count without the
     * list -- or green without either -- is how a row ends up claiming to be
     * green while carrying a delta.
     */
    public void setDelta(Set<String> missing, Set<String> extra) {
        this.missingCount = missing.size();
        this.extraCount = extra.size();
        this.missingUids = toJson(missing);
        this.extraUids = toJson(extra);
        this.green = missing.isEmpty() && extra.isEmpty();
    }

    public Set<String> missing() {
        return fromJson(missingUids);
    }

    public Set<String> extra() {
        return fromJson(extraUids);
    }

    private static String toJson(Set<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        List<String> ordered = new ArrayList<>(uids);
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(ordered.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    private static Set<String> fromJson(String json) {
        Set<String> out = new LinkedHashSet<>();
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return out;
        }
        String body = json.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        for (String part : body.split(",")) {
            String uid = part.trim();
            if (uid.startsWith("\"") && uid.endsWith("\"") && uid.length() >= 2) {
                uid = uid.substring(1, uid.length() - 1);
            }
            if (!uid.isEmpty()) {
                out.add(uid.replace("\\\"", "\""));
            }
        }
        return out;
    }
}
