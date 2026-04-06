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
public class SymbolsIngestionJob {

    private final PostgresCopyLoader copyLoader;
    private static final Logger LOGGER = LoggerFactory.getLogger(SymbolsIngestionJob.class);
    private final JdbcTemplate jdbcTemplate;

    public SymbolsIngestionJob(PostgresCopyLoader copyLoader, JdbcTemplate jdbcTemplate) {
        this.copyLoader = copyLoader;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestionResult run(IngestionRequest request) {
        Long scanRunId = request.scanRunId();
        Path file = Path.of(request.baseDir(), "symbols.csv");

        LOGGER.info("Preparing symbols staging cleanup scanRunId={} file={}", scanRunId, file);

        int deleted = jdbcTemplate.update(
                "delete from stg_symbols where scan_run_id = ?",
                scanRunId
        );

        LOGGER.info("Symbols staging cleanup finished scanRunId={} deletedRows={}", scanRunId, deleted);
        LOGGER.info("Starting symbols copy scanRunId={} file={}", scanRunId, file);

        long count = copyLoader.copyCsvWithScanRunId(
                "stg_symbols",
                new String[]{
                        "scan_run_id",
                        "package_name",
                        "declaring_type",
                        "signature",
                        "qualified_name",
                        "file_path"
                },
                file,
                request.scanRunId()
        );

        LOGGER.info("Finished symbols copy scanRunId={} rows={}", scanRunId, count);

        return new IngestionResult("symbols_copy", (int) count);
    }
}