package com.vena.codesage.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class GraphMaterializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphMaterializer.class);

    private static final int CALL_EDGE_CHUNK_SIZE = 25_000;
    private static final int CALL_EDGE_PROGRESS_LOG_INTERVAL = 25_000;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public GraphMaterializer(JdbcTemplate jdbcTemplate,
                             TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public void materializeSymbols(Long scanRunId) {
        long startedAt = System.currentTimeMillis();

        LOGGER.info("Cleaning code_entity for scanRunId={}", scanRunId);
        int deleted = jdbcTemplate.update("delete from code_entity where scan_run_id = ?", scanRunId);
        LOGGER.info("Cleaned code_entity for scanRunId={} deletedRows={}", scanRunId, deleted);

        LOGGER.info("Materializing symbols started for scanRunId={}", scanRunId);

        String sql = """
            insert into code_entity (
                scan_run_id,
                entity_key,
                entity_type,
                package_name,
                declaring_type,
                simple_name,
                qualified_name,
                signature,
                file_path,
                summary,
                content_hash,
                is_active
            )
            select
                dedup.scan_run_id,
                md5(
                    coalesce(dedup.qualified_name, '') || '|' ||
                    coalesce(dedup.signature, '') || '|' ||
                    coalesce(dedup.file_path, '')
                ) as entity_key,
                'METHOD',
                dedup.package_name,
                dedup.declaring_type,
                case
                    when position('.' in reverse(coalesce(dedup.qualified_name, ''))) > 0
                        then right(
                            coalesce(dedup.qualified_name, ''),
                            position('.' in reverse(coalesce(dedup.qualified_name, ''))) - 1
                        )
                    else coalesce(dedup.qualified_name, '')
                end as simple_name,
                dedup.qualified_name,
                dedup.signature,
                dedup.file_path,
                null,
                null,
                true
            from (
                select distinct on (
                    s.scan_run_id,
                    s.qualified_name,
                    coalesce(s.signature, ''),
                    coalesce(s.file_path, '')
                )
                    s.scan_run_id,
                    s.package_name,
                    s.declaring_type,
                    s.signature,
                    s.qualified_name,
                    s.file_path
                from stg_symbols s
                where s.scan_run_id = ?
                  and s.qualified_name is not null
                  and s.qualified_name <> ''
                order by
                    s.scan_run_id,
                    s.qualified_name,
                    coalesce(s.signature, ''),
                    coalesce(s.file_path, '')
            ) dedup
            on conflict (scan_run_id, entity_key) do nothing
        """;

        int inserted = jdbcTemplate.update(sql, scanRunId);

        LOGGER.info(
                "Materializing symbols completed for scanRunId={} insertedRows={} elapsedMs={}",
                scanRunId,
                inserted,
                System.currentTimeMillis() - startedAt
        );
    }

    public void materializeCallEdges(Long scanRunId) {
        long startedAt = System.currentTimeMillis();

        LOGGER.info("Cleaning call_edge for scanRunId={}", scanRunId);
        int deleted = jdbcTemplate.update("delete from call_edge where scan_run_id = ?", scanRunId);
        LOGGER.info("Cleaned call_edge for scanRunId={} deletedRows={}", scanRunId, deleted);

        LOGGER.info("Preparing call edge temp tables for scanRunId={}", scanRunId);

        jdbcTemplate.execute("drop table if exists tmp_code_entity_map");
        jdbcTemplate.execute("""
            create temporary table tmp_code_entity_map as
            select
                scan_run_id,
                qualified_name,
                min(entity_key) as entity_key
            from code_entity
            where scan_run_id = %d
            group by scan_run_id, qualified_name
        """.formatted(scanRunId));
        jdbcTemplate.execute("""
            create index if not exists idx_tmp_code_entity_map_qname
            on tmp_code_entity_map(scan_run_id, qualified_name)
        """);

        jdbcTemplate.execute("drop table if exists tmp_call_edges_dedup");
        jdbcTemplate.execute("""
            create temporary table tmp_call_edges_dedup as
            select
                row_number() over (order by caller_qname, callee_qname, file_path, line_number) as rn,
                scan_run_id,
                caller_qname,
                callee_qname,
                file_path,
                line_number
            from (
                select distinct
                    scan_run_id,
                    caller_qname,
                    callee_qname,
                    file_path,
                    line_number
                from stg_call_edges
                where scan_run_id = %d
            ) s
        """.formatted(scanRunId));
        jdbcTemplate.execute("""
            create index if not exists idx_tmp_call_edges_dedup_rn
            on tmp_call_edges_dedup(rn)
        """);

        Integer maxRn = jdbcTemplate.queryForObject(
                "select coalesce(max(rn), 0) from tmp_call_edges_dedup",
                Integer.class
        );

        if (maxRn == null || maxRn == 0) {
            LOGGER.info("No call edges to materialize for scanRunId={}", scanRunId);
            return;
        }

        LOGGER.info(
                "Materializing call edges started for scanRunId={} totalDedupedRows={} chunkSize={}",
                scanRunId,
                maxRn,
                CALL_EDGE_CHUNK_SIZE
        );

        int insertedTotal = 0;

        String chunkSql = """
            insert into call_edge (
                scan_run_id,
                edge_key,
                caller_entity_key,
                callee_entity_key,
                caller_qname,
                callee_qname,
                file_path,
                line_number,
                edge_type,
                is_active
            )
            select
                s.scan_run_id,

                md5(
                    coalesce(s.caller_qname,'') || '|' ||
                    coalesce(s.callee_qname,'') || '|' ||
                    coalesce(s.file_path,'') || '|' ||
                    coalesce(s.line_number::text,'')
                ) as edge_key,

                coalesce(
                    caller.entity_key,
                    md5('UNRESOLVED_CALLER|' || coalesce(s.caller_qname, ''))
                ) as caller_entity_key,

                coalesce(
                    callee.entity_key,
                    md5('UNRESOLVED_CALLEE|' || coalesce(s.callee_qname, ''))
                ) as callee_entity_key,

                s.caller_qname,
                s.callee_qname,
                s.file_path,
                s.line_number,
                'DIRECT_CALL',
                true
            from tmp_call_edges_dedup s
            left join tmp_code_entity_map caller
              on caller.scan_run_id = s.scan_run_id
             and caller.qualified_name = s.caller_qname
            left join tmp_code_entity_map callee
              on callee.scan_run_id = s.scan_run_id
             and callee.qualified_name = s.callee_qname
            where s.rn between ? and ?
            on conflict (scan_run_id, edge_key) do nothing
        """;

        for (int start = 1; start <= maxRn; start += CALL_EDGE_CHUNK_SIZE) {
            final int chunkStart = start;
            final int chunkEnd = Math.min(start + CALL_EDGE_CHUNK_SIZE - 1, maxRn);
            long chunkStartedAt = System.currentTimeMillis();

            Integer inserted = transactionTemplate.execute(status ->
                    jdbcTemplate.update(chunkSql, chunkStart, chunkEnd)
            );

            int chunkInserted = inserted == null ? 0 : inserted;
            insertedTotal += chunkInserted;

            if (insertedTotal == chunkInserted
                    || insertedTotal == maxRn
                    || insertedTotal / CALL_EDGE_PROGRESS_LOG_INTERVAL
                    > (insertedTotal - chunkInserted) / CALL_EDGE_PROGRESS_LOG_INTERVAL) {
                LOGGER.info(
                        "Materialized call edge chunk scanRunId={} range={}..{} insertedChunk={} insertedTotal={} elapsedMs={}",
                        scanRunId,
                        chunkStart,
                        chunkEnd,
                        chunkInserted,
                        insertedTotal,
                        System.currentTimeMillis() - chunkStartedAt
                );
            }
        }

        LOGGER.info(
                "Materializing call edges completed for scanRunId={} insertedTotal={} elapsedMs={}",
                scanRunId,
                insertedTotal,
                System.currentTimeMillis() - startedAt
        );
    }

    public void materializeEndpoints(Long scanRunId) {
        long startedAt = System.currentTimeMillis();

        LOGGER.info("Cleaning endpoint_mapping for scanRunId={}", scanRunId);
        int deleted = jdbcTemplate.update("delete from endpoint_mapping where scan_run_id = ?", scanRunId);
        LOGGER.info("Cleaned endpoint_mapping for scanRunId={} deletedRows={}", scanRunId, deleted);

        LOGGER.info("Materializing endpoints started for scanRunId={}", scanRunId);

        String sql = """
            insert into endpoint_mapping (
                scan_run_id,
                endpoint_key,
                http_method,
                class_path,
                method_path,
                method_qname,
                file_path,
                is_active
            )
            select
                s.scan_run_id,
                md5(
                    coalesce(s.http_method, '') || '|' ||
                    coalesce(s.class_path, '') || '|' ||
                    coalesce(s.method_path, '') || '|' ||
                    coalesce(s.method_qname, '')
                ) as endpoint_key,
                s.http_method,
                s.class_path,
                s.method_path,
                s.method_qname,
                s.file_path,
                true
            from stg_api_endpoints s
            where s.scan_run_id = ?
            on conflict (scan_run_id, endpoint_key) do nothing
        """;

        int inserted = jdbcTemplate.update(sql, scanRunId);

        LOGGER.info(
                "Materializing endpoints completed for scanRunId={} insertedRows={} elapsedMs={}",
                scanRunId,
                inserted,
                System.currentTimeMillis() - startedAt
        );
    }

    public void materializeTouchpoints(Long scanRunId) {
        long startedAt = System.currentTimeMillis();

        LOGGER.info("Cleaning touchpoint for scanRunId={}", scanRunId);
        int deleted = jdbcTemplate.update("delete from touchpoint where scan_run_id = ?", scanRunId);
        LOGGER.info("Cleaned touchpoint for scanRunId={} deletedRows={}", scanRunId, deleted);

        LOGGER.info("Materializing touchpoints started for scanRunId={}", scanRunId);

        String sql = """
            insert into touchpoint (
                scan_run_id,
                touchpoint_key,
                category,
                caller_qname,
                target_qname,
                file_path,
                is_active
            )
            select
                s.scan_run_id,
                md5(
                    coalesce(s.category, '') || '|' ||
                    coalesce(s.caller_qname, '') || '|' ||
                    coalesce(s.target_qname, '') || '|' ||
                    coalesce(s.file_path, '')
                ) as touchpoint_key,
                s.category,
                s.caller_qname,
                s.target_qname,
                s.file_path,
                true
            from stg_touchpoints s
            where s.scan_run_id = ?
            on conflict (scan_run_id, touchpoint_key) do nothing
        """;

        int inserted = jdbcTemplate.update(sql, scanRunId);

        LOGGER.info(
                "Materializing touchpoints completed for scanRunId={} insertedRows={} elapsedMs={}",
                scanRunId,
                inserted,
                System.currentTimeMillis() - startedAt
        );
    }

    public void materializeFlowEdges(Long scanRunId) {
        long startedAt = System.currentTimeMillis();

        LOGGER.info("Cleaning flow_edge for scanRunId={}", scanRunId);
        int deleted = jdbcTemplate.update("delete from flow_edge where scan_run_id = ?", scanRunId);
        LOGGER.info("Cleaned flow_edge for scanRunId={} deletedRows={}", scanRunId, deleted);

        LOGGER.info("Materializing flow edges started for scanRunId={}", scanRunId);

        String sql = """
            insert into flow_edge (
                scan_run_id,
                flow_key,
                source_method_qname,
                parameter_name,
                sink_method_qname,
                source_declaring_type,
                is_active
            )
            select
                s.scan_run_id,
                md5(
                    coalesce(s.source_method_qname, '') || '|' ||
                    coalesce(s.parameter_name, '') || '|' ||
                    coalesce(s.sink_method_qname, '') || '|' ||
                    coalesce(s.source_declaring_type, '')
                ) as flow_key,
                s.source_method_qname,
                s.parameter_name,
                s.sink_method_qname,
                s.source_declaring_type,
                true
            from stg_flow_edges s
            where s.scan_run_id = ?
            on conflict (scan_run_id, flow_key) do nothing
        """;

        int inserted = jdbcTemplate.update(sql, scanRunId);

        LOGGER.info(
                "Materializing flow edges completed for scanRunId={} insertedRows={} elapsedMs={}",
                scanRunId,
                inserted,
                System.currentTimeMillis() - startedAt
        );
    }
}