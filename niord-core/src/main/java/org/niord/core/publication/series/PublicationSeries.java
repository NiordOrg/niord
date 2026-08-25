package org.niord.core.publication.series;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import org.niord.core.publication.series.vo.PublicationSeriesVo;
import org.niord.core.publication.series.vo.PublicationSeriesDescVo;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;
import java.util.LinkedHashMap;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.domain.Domain;
import org.niord.core.model.VersionedEntity;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.JpaCriteriaAttributeConverter;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.ILocalizable;
import org.niord.model.search.PagedSearchParamsVo;

/**
 * The definition of a publication: what an issue of it IS, how often, over which query, and
 * rendered by which report. Replaces the legacy template publication.
 *
 * Identity comes from VersionedEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityIdentityTest enforces it.
 */
@Entity
public class PublicationSeries extends VersionedEntity<Integer> implements ILocalizable<PublicationSeriesDesc> {

    @Column(length = 64, nullable = false, unique = true)
    private String seriesId;

    @Column(length = 36, unique = true)
    private String legacyTemplateId;

    @Column(length = 255)
    private String importSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeriesStatus status = SeriesStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentMode contentMode = ContentMode.GENERATED_FROM_QUERY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeriesCadence cadence = SeriesCadence.NONE;

    @Enumerated(EnumType.STRING)
    private CutoffDay nominalCutoffDay;

    private Integer nominalCutoffDayOfMonth;

    private Integer nominalCutoffMonth;

    @Column(length = 5)
    private String nominalCutoffTime;

    @Column(length = 64)
    private String nominalCutoffTimeZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NumberingScheme numberingScheme = NumberingScheme.NONE;

    @Temporal(TemporalType.TIMESTAMP)
    private Date firstIssueStartsAt;

    @Enumerated(EnumType.STRING)
    private TimeRelation timeRelation;

    private Boolean aliveAtCutoff;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaCriteriaAttributeConverter.class)
    private IssueCriteriaVo criteria;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private PublicationCategory category;

    @ManyToOne
    private Domain domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessagePublication messagePublication = MessagePublication.NONE;

    private Integer sortOrder;

    @Column(length = 64)
    private String reportId;

    @Enumerated(EnumType.STRING)
    private PageSize pageSize;

    @Enumerated(EnumType.STRING)
    private PageOrientation pageOrientation;

    private Boolean mapThumbnails;

    @Column(length = 32)
    private String messageSortBy;

    @Enumerated(EnumType.STRING)
    private PagedSearchParamsVo.SortOrder messageSortOrder;

    // Initialised, not left null. JpaPropertiesAttributeConverter returns an empty
    // map for a null column, so an entity LOADED from the database always has a
    // map; only a freshly constructed one did not, which made the create path the
    // single place updateFromVo could dereference null. Publication does the same
    // for its two maps, and descs and languages below do it for the same reason.
    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    private Map<String, Object> reportParams = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReleaseMode releaseMode = ReleaseMode.MANUAL_GATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NextIssueCreation nextIssueCreation = NextIssueCreation.AUTO_ON_PUBLISH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicAuthority publicAuthority = PublicAuthority.LEGACY;

    @Column(nullable = false)
    private boolean languageSpecific = true;

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public String getLegacyTemplateId() {
        return legacyTemplateId;
    }

    public void setLegacyTemplateId(String legacyTemplateId) {
        this.legacyTemplateId = legacyTemplateId;
    }

    public String getImportSource() {
        return importSource;
    }

    public void setImportSource(String importSource) {
        this.importSource = importSource;
    }

    public SeriesStatus getStatus() {
        return status;
    }

    public void setStatus(SeriesStatus status) {
        this.status = status;
    }

    public ContentMode getContentMode() {
        return contentMode;
    }

    public void setContentMode(ContentMode contentMode) {
        this.contentMode = contentMode;
    }

    public SeriesCadence getCadence() {
        return cadence;
    }

    public void setCadence(SeriesCadence cadence) {
        this.cadence = cadence;
    }

    public CutoffDay getNominalCutoffDay() {
        return nominalCutoffDay;
    }

    public void setNominalCutoffDay(CutoffDay nominalCutoffDay) {
        this.nominalCutoffDay = nominalCutoffDay;
    }

    public Integer getNominalCutoffDayOfMonth() {
        return nominalCutoffDayOfMonth;
    }

    public void setNominalCutoffDayOfMonth(Integer nominalCutoffDayOfMonth) {
        this.nominalCutoffDayOfMonth = nominalCutoffDayOfMonth;
    }

    public Integer getNominalCutoffMonth() {
        return nominalCutoffMonth;
    }

    public void setNominalCutoffMonth(Integer nominalCutoffMonth) {
        this.nominalCutoffMonth = nominalCutoffMonth;
    }

    public String getNominalCutoffTime() {
        return nominalCutoffTime;
    }

    public void setNominalCutoffTime(String nominalCutoffTime) {
        this.nominalCutoffTime = nominalCutoffTime;
    }

    public String getNominalCutoffTimeZone() {
        return nominalCutoffTimeZone;
    }

    public void setNominalCutoffTimeZone(String nominalCutoffTimeZone) {
        this.nominalCutoffTimeZone = nominalCutoffTimeZone;
    }

    public NumberingScheme getNumberingScheme() {
        return numberingScheme;
    }

    public void setNumberingScheme(NumberingScheme numberingScheme) {
        this.numberingScheme = numberingScheme;
    }

    public Date getFirstIssueStartsAt() {
        return firstIssueStartsAt;
    }

    public void setFirstIssueStartsAt(Date firstIssueStartsAt) {
        this.firstIssueStartsAt = firstIssueStartsAt;
    }

    public TimeRelation getTimeRelation() {
        return timeRelation;
    }

    public void setTimeRelation(TimeRelation timeRelation) {
        this.timeRelation = timeRelation;
    }

    public Boolean getAliveAtCutoff() {
        return aliveAtCutoff;
    }

    public void setAliveAtCutoff(Boolean aliveAtCutoff) {
        this.aliveAtCutoff = aliveAtCutoff;
    }

    public IssueCriteriaVo getCriteria() {
        return criteria;
    }

    public void setCriteria(IssueCriteriaVo criteria) {
        this.criteria = criteria;
    }

    public PublicationCategory getCategory() {
        return category;
    }

    public void setCategory(PublicationCategory category) {
        this.category = category;
    }

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public MessagePublication getMessagePublication() {
        return messagePublication;
    }

    public void setMessagePublication(MessagePublication messagePublication) {
        this.messagePublication = messagePublication;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public PageSize getPageSize() {
        return pageSize;
    }

    public void setPageSize(PageSize pageSize) {
        this.pageSize = pageSize;
    }

    public PageOrientation getPageOrientation() {
        return pageOrientation;
    }

    public void setPageOrientation(PageOrientation pageOrientation) {
        this.pageOrientation = pageOrientation;
    }

    public Boolean getMapThumbnails() {
        return mapThumbnails;
    }

    public void setMapThumbnails(Boolean mapThumbnails) {
        this.mapThumbnails = mapThumbnails;
    }

    public String getMessageSortBy() {
        return messageSortBy;
    }

    public void setMessageSortBy(String messageSortBy) {
        this.messageSortBy = messageSortBy;
    }

    public PagedSearchParamsVo.SortOrder getMessageSortOrder() {
        return messageSortOrder;
    }

    public void setMessageSortOrder(PagedSearchParamsVo.SortOrder messageSortOrder) {
        this.messageSortOrder = messageSortOrder;
    }

    public Map<String, Object> getReportParams() {
        return reportParams;
    }

    public void setReportParams(Map<String, Object> reportParams) {
        this.reportParams = reportParams;
    }

    public ReleaseMode getReleaseMode() {
        return releaseMode;
    }

    public void setReleaseMode(ReleaseMode releaseMode) {
        this.releaseMode = releaseMode;
    }

    public NextIssueCreation getNextIssueCreation() {
        return nextIssueCreation;
    }

    public void setNextIssueCreation(NextIssueCreation nextIssueCreation) {
        this.nextIssueCreation = nextIssueCreation;
    }

    public PublicAuthority getPublicAuthority() {
        return publicAuthority;
    }

    public void setPublicAuthority(PublicAuthority publicAuthority) {
        this.publicAuthority = publicAuthority;
    }

    public boolean isLanguageSpecific() {
        return languageSpecific;
    }

    public void setLanguageSpecific(boolean languageSpecific) {
        this.languageSpecific = languageSpecific;
    }

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    private List<PublicationSeriesDesc> descs = new ArrayList<>();

    @Override
    public List<PublicationSeriesDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationSeriesDesc> descs) {
        this.descs = descs;
    }

    /** Creates and attaches a desc for the given language. */
    public PublicationSeriesDesc createDesc(String lang) {
        PublicationSeriesDesc desc = new PublicationSeriesDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }

    /**
     * The configured language list, ordered. DECLARED, never inferred from which desc
     * rows happen to exist: "this series has no Danish name yet" and "this series is not
     * published in Danish" are different facts.
     */
    @ElementCollection
    @OrderColumn(name = "indexNo")
    private List<String> languages = new ArrayList<>();

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }


    /**
     * Converts to a value object, public fields first and operational fields
     * only for the system type.
     *
     * The branch on instanceof is the whole access control. A field that is not
     * declared on the public type cannot be serialised from it, however the
     * caller got hold of the object -- which is a stronger guarantee than a flag
     * checked at every serialisation point, where the one that forgets leaks
     * everything.
     */
    /**
     * The inverse of {@link #toVo}: a system value object onto this entity.
     *
     * The category and the domain are NOT resolved here -- they are references to
     * other entities and this class has no persistence context. The caller
     * resolves them and sets them, and `SeriesValidator` refuses a series with no
     * category, so a caller that forgets cannot save.
     *
     * ENUMS FAIL LOUDLY. A value the enum does not know is a client sending a
     * name this build does not have -- a stale frontend, or a typo -- and the
     * quiet alternative is to leave the field null, which for `contentMode` or
     * `timeRelation` means the issue silently resolves nothing. `SeriesValidator`
     * would then report "required field missing" for a field the client did send,
     * which sends whoever is debugging it to the wrong place entirely.
     */
    public void updateFromVo(SystemPublicationSeriesVo vo) {
        seriesId = vo.getSeriesId();
        sortOrder = vo.getSortOrder();

        status = enumOf(SeriesStatus.class, vo.getStatus(), "status");
        contentMode = enumOf(ContentMode.class, vo.getContentMode(), "contentMode");
        cadence = enumOf(SeriesCadence.class, vo.getCadence(), "cadence");
        nominalCutoffDay = enumOf(CutoffDay.class, vo.getNominalCutoffDay(), "nominalCutoffDay");
        nominalCutoffDayOfMonth = vo.getNominalCutoffDayOfMonth();
        nominalCutoffMonth = vo.getNominalCutoffMonth();
        nominalCutoffTime = vo.getNominalCutoffTime();
        numberingScheme = enumOf(NumberingScheme.class, vo.getNumberingScheme(), "numberingScheme");
        timeRelation = enumOf(TimeRelation.class, vo.getTimeRelation(), "timeRelation");
        aliveAtCutoff = vo.getAliveAtCutoff();
        firstIssueStartsAt = vo.getFirstIssueStartsAt();
        reportId = vo.getReportId();
        pageSize = enumOf(PageSize.class, vo.getPageSize(), "pageSize");
        pageOrientation = enumOf(PageOrientation.class, vo.getPageOrientation(), "pageOrientation");
        mapThumbnails = vo.getMapThumbnails();
        messageSortBy = vo.getMessageSortBy();
        messageSortOrder = enumOf(PagedSearchParamsVo.SortOrder.class, vo.getMessageSortOrder(),
                "messageSortOrder");
        messagePublication = enumOf(MessagePublication.class, vo.getMessagePublication(),
                "messagePublication");
        releaseMode = enumOf(ReleaseMode.class, vo.getReleaseMode(), "releaseMode");
        nextIssueCreation = enumOf(NextIssueCreation.class, vo.getNextIssueCreation(),
                "nextIssueCreation");
        publicAuthority = enumOf(PublicAuthority.class, vo.getPublicAuthority(), "publicAuthority");
        // Absent means "unchanged": an older client that does not send it must not
        // silently flip a series to one file for every language.
        if (vo.getLanguageSpecific() != null) {
            languageSpecific = vo.getLanguageSpecific();
        }
        legacyTemplateId = vo.getLegacyTemplateId();
        importSource = vo.getImportSource();

        criteria = criteriaOf(vo.getCriteria());

        languages.clear();
        if (vo.getLanguages() != null) {
            languages.addAll(vo.getLanguages());
        }
        if (vo.getReportParams() != null) {
            reportParams.clear();
            reportParams.putAll(vo.getReportParams());
        }

        // MERGED IN PLACE, keeping the row that already exists for a language.
        //
        // The semantics are the wholesale ones -- a desc the client did not send is
        // a desc the client deleted -- but the MECHANISM has to differ. The table is
        // unique on (lang, entity_id) and Hibernate orders inserts before deletes
        // within a flush, so clearing the list and re-adding "da" inserts a second
        // "da" while the first is still awaiting deletion: "Duplicate entry da-72488".
        //
        // Every save after the FIRST failed on it. It went unseen because the flush
        // lands wherever the next query runs, which was a category lookup that caught
        // Exception and returned null -- so an admin editing a series was told their
        // perfectly good publication category did not exist.
        List<String> sent = new ArrayList<>();
        if (vo.getDescs() != null) {
            for (PublicationSeriesDescVo dv : vo.getDescs()) {
                sent.add(dv.getLang());
            }
        }
        descs.removeIf(d -> !sent.contains(d.getLang()));

        if (vo.getDescs() != null) {
            for (PublicationSeriesDescVo dv : vo.getDescs()) {
                PublicationSeriesDesc d = descs.stream()
                        .filter(x -> x.getLang().equals(dv.getLang()))
                        .findFirst()
                        .orElseGet(() -> createDesc(dv.getLang()));
                d.setName(dv.getName());
                d.setNameSuggestionPattern(dv.getNameSuggestionPattern());
                d.setFileNamePattern(dv.getFileNamePattern());
                d.setMessageReferenceFormat(dv.getMessageReferenceFormat());
                d.setLinkPattern(dv.getLinkPattern());
            }
        }
    }

    /** Null stays null; anything else must be a name the enum knows. */
    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, String field) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.trim());
        } catch (IllegalArgumentException e) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    field + " = '" + name + "' is not one of " + java.util.Arrays.toString(type.getEnumConstants()));
        }
    }

    /**
     * The criteria document, which arrives as whatever JSON-B made of it.
     *
     * Round-tripped through the mapper rather than cast, because a POST body
     * deserialises to maps and lists while the column holds an IssueCriteriaVo,
     * and a cast would succeed on the way in and fail at flush time -- far from
     * the request that caused it.
     */
    private static IssueCriteriaVo criteriaOf(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof IssueCriteriaVo already) {
            return already;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            return m.convertValue(raw, IssueCriteriaVo.class);
        } catch (RuntimeException e) {
            throw new IssueLifecycleService.TransitionRefusedException("CRITERIA_INVALID",
                    "the criteria document could not be read: " + e.getMessage());
        }
    }

    public <V extends PublicationSeriesVo> V toVo(Class<V> clz) {
        V vo;
        try {
            vo = clz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot instantiate " + clz, e);
        }

        vo.setSeriesId(seriesId);
        vo.setCreated(getCreated());
        vo.setUpdated(getUpdated());
        vo.setCategoryId(category == null ? null : category.getCategoryId());
        vo.setSortOrder(sortOrder);

        for (PublicationSeriesDesc d : getDescs()) {
            PublicationSeriesDescVo dv = new PublicationSeriesDescVo();
            dv.setLang(d.getLang());
            dv.setName(d.getName());
            dv.setNameSuggestionPattern(d.getNameSuggestionPattern());
            dv.setFileNamePattern(d.getFileNamePattern());
            dv.setMessageReferenceFormat(d.getMessageReferenceFormat());
            dv.setLinkPattern(d.getLinkPattern());
            vo.getDescs().add(dv);
        }

        if (vo instanceof SystemPublicationSeriesVo sys) {
            sys.setStatus(status == null ? null : status.name());
            sys.setContentMode(contentMode == null ? null : contentMode.name());
            sys.setCadence(cadence == null ? null : cadence.name());
            sys.setNominalCutoffDay(nominalCutoffDay == null ? null : nominalCutoffDay.name());
            sys.setNominalCutoffDayOfMonth(nominalCutoffDayOfMonth);
            sys.setNominalCutoffMonth(nominalCutoffMonth);
            sys.setNominalCutoffTime(nominalCutoffTime);
            sys.setNumberingScheme(numberingScheme == null ? null : numberingScheme.name());
            sys.setTimeRelation(timeRelation == null ? null : timeRelation.name());
            sys.setAliveAtCutoff(aliveAtCutoff);
            sys.setFirstIssueStartsAt(firstIssueStartsAt);
            sys.setCriteria(criteria);
            sys.setDomainId(domain == null ? null : domain.getDomainId());
            sys.getLanguages().addAll(languages);
            sys.setReportId(reportId);
            sys.setPageSize(pageSize == null ? null : pageSize.name());
            sys.setPageOrientation(pageOrientation == null ? null : pageOrientation.name());
            sys.setMapThumbnails(mapThumbnails);
            sys.setMessageSortBy(messageSortBy);
            sys.setMessageSortOrder(messageSortOrder == null ? null : messageSortOrder.name());
            sys.setMessagePublication(messagePublication == null ? null : messagePublication.name());
            sys.setReleaseMode(releaseMode == null ? null : releaseMode.name());
            sys.setNextIssueCreation(nextIssueCreation == null ? null : nextIssueCreation.name());
            sys.setPublicAuthority(publicAuthority == null ? null : publicAuthority.name());
            sys.setLanguageSpecific(languageSpecific);
            sys.setLegacyTemplateId(legacyTemplateId);
            sys.setImportSource(importSource);
            if (reportParams != null) {
                sys.setReportParams(new LinkedHashMap<>(reportParams));
            }
        }
        return vo;
    }

    /**
     * The time zone every cut-off of this series is reckoned in.
     *
     * Lives on the entity because the zone is a property of the series, and
     * because three callers were each resolving it themselves -- naming, issue
     * creation and gap synthesis. A blank or unparseable zone falls back to UTC
     * rather than throwing: a misconfigured zone shifts a cut-off by hours, while
     * throwing here would take out the screens that merely wanted to name a week.
     */
    /**
     * The zone this series' cut-offs are read and written in.
     *
     * THE DOMAIN AND NOTHING ELSE. Timezone is a domain setting and the domains
     * really do differ -- Atlantic/Faeroe, UTC for Greenland, Europe/Copenhagen for
     * the rest -- so a series that answered from anywhere else would schedule its
     * cut-off in a zone nobody configured. A per-series timezone column would be a
     * second source that can disagree with the domain, which is why this no longer
     * consults one.
     *
     * The UTC branch is a last resort for a series with no domain, which S-20
     * refuses. It is reachable only by a series that is already invalid, and it is
     * here so that resolving one cannot throw rather than as a policy about zones.
     */
    public ZoneId cutoffZone() {
        return domain == null ? ZoneId.of("UTC") : domain.timeZone().toZoneId();
    }

}
