package com.vena.codesage.ingestion;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

@Component
public class PostgresCopyLoader {

    private final DataSource dataSource;

    public PostgresCopyLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long copyCsvWithScanRunId(String tableName,
                                     String[] targetColumns,
                                     Path sourceCsv,
                                     Long scanRunId) {
        String columnList = String.join(", ", targetColumns);

        String sql = String.format(
                "COPY %s (%s) FROM STDIN WITH (FORMAT csv, HEADER true)",
                tableName,
                columnList
        );

        try (Connection connection = dataSource.getConnection();
             Reader reader = buildAugmentedCsvReader(sourceCsv, scanRunId)) {

            CopyManager copyManager = new CopyManager(connection.unwrap(BaseConnection.class));
            return copyManager.copyIn(sql, reader);

        } catch (Exception e) {
            throw new RuntimeException(
                    "COPY failed for table " + tableName + " file=" + sourceCsv,
                    e
            );
        }
    }

    private Reader buildAugmentedCsvReader(Path sourceCsv, Long scanRunId) throws IOException {
        StringWriter writer = new StringWriter();

        try (BufferedReader br = Files.newBufferedReader(sourceCsv)) {
            String header = br.readLine();
            if (header == null || header.isBlank()) {
                throw new IllegalArgumentException("CSV is empty: " + sourceCsv);
            }

            writer.write("scan_run_id," + header + System.lineSeparator());

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                writer.write(scanRunId + "," + line + System.lineSeparator());
            }
        }

        return new StringReader(writer.toString());
    }
}