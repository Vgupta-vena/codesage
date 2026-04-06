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
public class ApiEndpointsIngestionJob {

    private final PostgresCopyLoader copyLoader;
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiEndpointsIngestionJob.class);
    private final JdbcTemplate jdbcTemplate;

    public ApiEndpointsIngestionJob(PostgresCopyLoader copyLoader, JdbcTemplate jdbcTemplate) {
        this.copyLoader = copyLoader;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestionResult run(IngestionRequest request) {
        Long scanRunId = request.scanRunId();
        Path file = Path.of(request.baseDir(), "api_endpoints.csv");

        LOGGER.info("Preparing API endpoint staging cleanup scanRunId={} file={}", scanRunId, file);

        int deleted = jdbcTemplate.update("delete from stg_api_endpoints where scan_run_id = ?", scanRunId);

        LOGGER.info("API endpoint staging cleanup finished scanRunId={} deletedRows={}", scanRunId, deleted);
        LOGGER.info("Starting API endpoint copy scanRunId={} file={}", scanRunId, file);

        long count = copyLoader.copyCsvWithScanRunId(
                "stg_api_endpoints",
                new String[]{
                        "scan_run_id",
                        "http_method",
                        "class_path",
                        "method_path",
                        "method_qname",
                        "file_path"
                },
                file,
                request.scanRunId()
        );

        LOGGER.info("Finished API endpoint copy scanRunId={} rows={}", scanRunId, count);

        return new IngestionResult("api_endpoints_copy", (int) count);
    }
}