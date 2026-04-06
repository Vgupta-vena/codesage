package com.vena.codesage.ingestion.jobs;

import com.vena.codesage.ingestion.IngestionRequest;
import com.vena.codesage.ingestion.IngestionResult;
import com.vena.codesage.ingestion.PostgresCopyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class TouchpointsIngestionJob {

    private final PostgresCopyLoader copyLoader;
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiEndpointsIngestionJob.class);
    private final JdbcTemplate jdbcTemplate;

    public TouchpointsIngestionJob(PostgresCopyLoader copyLoader, JdbcTemplate jdbcTemplate) {
        this.copyLoader = copyLoader;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestionResult run(IngestionRequest request) {
        Long scanRunId = request.scanRunId();
        Path file = Path.of(request.baseDir(), "touchpoints.csv");


        LOGGER.info("Preparing touchpoint staging cleanup scanRunId={} file={}", scanRunId, file);

        int deleted = jdbcTemplate.update("delete from stg_touchpoints where scan_run_id = ?", scanRunId);

        LOGGER.info("Touchpoint staging cleanup finished scanRunId={} deletedRows={}", scanRunId, deleted);
        LOGGER.info("Starting touchpoint copy scanRunId={} file={}", scanRunId, file);


        long count = copyLoader.copyCsvWithScanRunId(
                "stg_touchpoints",
                new String[]{
                        "scan_run_id",
                        "category",
                        "caller_qname",
                        "target_qname",
                        "file_path"
                },
                file,
                request.scanRunId()
        );

        LOGGER.info("Finished touchpoint copy scanRunId={} rows={}", scanRunId, count);

        return new IngestionResult("touchpoints_copy", (int) count);
    }
}