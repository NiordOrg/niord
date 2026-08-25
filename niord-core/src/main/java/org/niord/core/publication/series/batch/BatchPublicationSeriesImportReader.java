package org.niord.core.publication.series.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;
import org.niord.core.util.JsonUtils;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads publication series from an uploaded JSON file.
 *
 * The file format is whatever {@code GET /publication-series/search-details}
 * emits, unchanged. That is deliberate and it is the whole design: the export IS
 * the admin list endpoint, so there is no export format to keep in step with an
 * import format, and a round trip cannot lose a field that only one side knows
 * about.
 *
 * The job XML lives in niord-web's META-INF/batch-jobs rather than here, like
 * every other batch job in the system -- a Wildfly class-loading bug that the
 * existing jobs already document.
 */
@Dependent
@Named("batchPublicationSeriesImportReader")
public class BatchPublicationSeriesImportReader extends AbstractItemHandler {

    private List<SystemPublicationSeriesVo> series;
    private int seriesNo = 0;

    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());
        series = JsonUtils.readJson(new TypeReference<List<SystemPublicationSeriesVo>>() {
        }, path);

        if (prevCheckpointInfo != null) {
            seriesNo = (Integer) prevCheckpointInfo;
        }
        getLog().info("Start processing " + series.size() + " publication series from index " + seriesNo);
    }

    @Override
    public Object readItem() {
        if (seriesNo < series.size()) {
            return series.get(seriesNo++);
        }
        return null;
    }

    @Override
    public Serializable checkpointInfo() {
        return seriesNo;
    }
}
