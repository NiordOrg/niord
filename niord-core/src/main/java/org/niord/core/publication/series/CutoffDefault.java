package org.niord.core.publication.series;

/**
 * Where a series' cut-off falls by default when an issue is published.
 *
 * The cut-off is the end of an issue's CONTENT period -- the instant membership
 * is decided at -- and the publication moment is when somebody pressed publish.
 * For a weekly list the two are minutes apart and the release stamps the
 * cut-off. For an annual list they can be a year apart: "EfS A 2018" describes
 * what was in force when the 2018 edition took effect, whenever in January the
 * document was finished, and "Akkumuleret EfS 2003" describes what was published
 * during 2003, and came out in 2016.
 *
 * The publish dialog offers this as the default and lets the admin choose a
 * past instant instead (never a future one); the publication moment is always
 * the actual one and never editable.
 */
public enum CutoffDefault {

    /** The moment of the release action. The weekly shape: the release closes the period. */
    RELEASE_MOMENT,

    /**
     * The nominal start of the period the issue is current for. The in-force
     * annual shape: the year's edition is decided where the year opens.
     */
    PERIOD_START,

    /**
     * The nominal end of the period the issue describes. The accumulated annual
     * shape: what was published during the year is known when the year closes.
     */
    PERIOD_END;

    /**
     * The default for a series shape, as the importer decides it and the create
     * form suggests it: yearly series are calendar-driven, everything else is
     * release-driven.
     */
    public static CutoffDefault forShape(SeriesCadence cadence,
                                         org.niord.core.publication.series.resolve.TimeRelation relation) {
        if (cadence == SeriesCadence.YEARLY) {
            return relation == org.niord.core.publication.series.resolve.TimeRelation.IN_FORCE_AT_CUTOFF
                    ? PERIOD_START : PERIOD_END;
        }
        return RELEASE_MOMENT;
    }
}
