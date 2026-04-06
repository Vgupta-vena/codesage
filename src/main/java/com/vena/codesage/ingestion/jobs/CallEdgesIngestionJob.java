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
public class CallEdgesIngestionJob {

    private final PostgresCopyLoader copyLoader;
    private static final Logger LOGGER = LoggerFactory.getLogger(CallEdgesIngestionJob.class);
    private final JdbcTemplate jdbcTemplate;

    public CallEdgesIngestionJob(PostgresCopyLoader copyLoader, JdbcTemplate jdbcTemplate) {
        this.copyLoader = copyLoader;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestionResult run(IngestionRequest request) {
        Long scanRunId = request.scanRunId();
        Path file = Path.of(request.baseDir(), "call_edges.csv");

        LOGGER.info("Preparing call edge staging cleanup scanRunId={} file={}", scanRunId, file);

        int deleted = jdbcTemplate.update("delete from stg_call_edges where scan_run_id = ?", scanRunId);

        LOGGER.info("call edge staging cleanup finished scanRunId={} deletedRows={}", scanRunId, deleted);
        LOGGER.info("Starting call edge copy scanRunId={} file={}", scanRunId, file);

        long count = copyLoader.copyCsvWithScanRunId(
                "stg_call_edges",
                new String[]{"scan_run_id", "caller_qname", "callee_qname", "file_path", "line_number"},
                file,
                request.scanRunId()
        );

        LOGGER.info("Finished call edge copy scanRunId={} rows={}", scanRunId, count);

        return new IngestionResult("call_edges_copy", (int) count);
    }
}