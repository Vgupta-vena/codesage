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
public class PersistenceFlowIngestionJob {

    private final PostgresCopyLoader copyLoader;
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceFlowIngestionJob.class);
    private final JdbcTemplate jdbcTemplate;

    public PersistenceFlowIngestionJob(PostgresCopyLoader copyLoader, JdbcTemplate jdbcTemplate) {
        this.copyLoader = copyLoader;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestionResult run(IngestionRequest request) {
        Long scanRunId = request.scanRunId();
        Path file = Path.of(request.baseDir(), "persistence_flow.csv");

        LOGGER.info("Preparing persistence flow staging cleanup scanRunId={} file={}", scanRunId, file);

        int deleted = jdbcTemplate.update("delete from stg_flow_edges where scan_run_id = ?", scanRunId);

        LOGGER.info("persistence flow staging cleanup finished scanRunId={} deletedRows={}", scanRunId, deleted);
        LOGGER.info("Starting persistence flow copy scanRunId={} file={}", scanRunId, file);

        long count = copyLoader.copyCsvWithScanRunId(
                "stg_flow_edges",
                new String[]{
                        "scan_run_id",
                        "source_method_qname",
                        "parameter_name",
                        "sink_method_qname",
                        "source_declaring_type"
                },
                file,
                request.scanRunId()
        );

        LOGGER.info("Finished persistence flow copy scanRunId={} rows={}", scanRunId, count);

        return new IngestionResult("persistence_flow_copy", (int) count);
    }
}