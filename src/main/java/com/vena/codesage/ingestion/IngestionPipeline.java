package com.vena.codesage.ingestion;

import com.vena.codesage.dto.CompleteScanRequest;
import com.vena.codesage.dto.FailScanRequest;
import com.vena.codesage.dto.IngestionReportDto;
import com.vena.codesage.dto.IngestionResultDto;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.service.ScanManagerService;
import com.vena.codesage.ingestion.jobs.ApiEndpointsIngestionJob;
import com.vena.codesage.ingestion.jobs.CallEdgesIngestionJob;
import com.vena.codesage.ingestion.jobs.PersistenceFlowIngestionJob;
import com.vena.codesage.ingestion.jobs.SemanticEnrichmentJob;
import com.vena.codesage.ingestion.jobs.SymbolsIngestionJob;
import com.vena.codesage.ingestion.jobs.TouchpointsIngestionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionPipeline.class);

    private final SymbolsIngestionJob symbolsJob;
    private final CallEdgesIngestionJob callEdgesJob;
    private final ApiEndpointsIngestionJob endpointsJob;
    private final TouchpointsIngestionJob touchpointsJob;
    private final PersistenceFlowIngestionJob persistenceFlowJob;
    private final SemanticEnrichmentJob semanticEnrichmentJob;
    private final ScanManagerService scanManagerService;
    private final GraphMaterializer materializer;

    public IngestionPipeline(SymbolsIngestionJob symbolsJob,
                             CallEdgesIngestionJob callEdgesJob,
                             ApiEndpointsIngestionJob endpointsJob,
                             TouchpointsIngestionJob touchpointsJob,
                             PersistenceFlowIngestionJob persistenceFlowJob,
                             SemanticEnrichmentJob semanticEnrichmentJob,
                             ScanManagerService scanManagerService,
                             GraphMaterializer materializer) {
        this.symbolsJob = symbolsJob;
        this.callEdgesJob = callEdgesJob;
        this.endpointsJob = endpointsJob;
        this.touchpointsJob = touchpointsJob;
        this.persistenceFlowJob = persistenceFlowJob;
        this.semanticEnrichmentJob = semanticEnrichmentJob;
        this.scanManagerService = scanManagerService;
        this.materializer = materializer;
    }

    public IngestionReportDto runFull(IngestionRequest request, boolean activateAfterSuccess) {
        long startedAt = System.currentTimeMillis();

        LOGGER.info(
                "Starting full ingestion scanRunId={} projectKey={} baseDir={} activateAfterSuccess={}",
                request.scanRunId(),
                request.projectKey(),
                request.baseDir(),
                activateAfterSuccess
        );

        try {
            List<IngestionResult> results = new ArrayList<>();

            LOGGER.info("Running symbols ingestion scanRunId={}", request.scanRunId());
            results.add(symbolsJob.run(request));

            LOGGER.info("Running call edges ingestion scanRunId={}", request.scanRunId());
            results.add(callEdgesJob.run(request));

            LOGGER.info("Running api endpoints ingestion scanRunId={}", request.scanRunId());
            results.add(endpointsJob.run(request));

            LOGGER.info("Running touchpoints ingestion scanRunId={}", request.scanRunId());
            results.add(touchpointsJob.run(request));

            LOGGER.info("Running persistence flow ingestion scanRunId={}", request.scanRunId());
            results.add(persistenceFlowJob.run(request));

            LOGGER.info("Materializing symbols scanRunId={}", request.scanRunId());
            materializer.materializeSymbols(request.scanRunId());

            LOGGER.info("Materializing call edges scanRunId={}", request.scanRunId());
            materializer.materializeCallEdges(request.scanRunId());

            LOGGER.info("Materializing endpoints scanRunId={}", request.scanRunId());
            materializer.materializeEndpoints(request.scanRunId());

            LOGGER.info("Materializing touchpoints scanRunId={}", request.scanRunId());
            materializer.materializeTouchpoints(request.scanRunId());

            LOGGER.info("Materializing flow edges scanRunId={}", request.scanRunId());
            materializer.materializeFlowEdges(request.scanRunId());

            LOGGER.info("Running semantic enrichment scanRunId={}", request.scanRunId());
            results.add(semanticEnrichmentJob.run(request));

            LOGGER.info("Completing scan scanRunId={}", request.scanRunId());
            ScanRun completed = scanManagerService.completeScan(
                    new CompleteScanRequest(
                            request.scanRunId(),
                            activateAfterSuccess,
                            null
                    )
            );

            List<IngestionResultDto> resultDtos = results.stream()
                    .map(r -> new IngestionResultDto(r.jobName(), r.recordsProcessed()))
                    .toList();

            long elapsedMs = System.currentTimeMillis() - startedAt;

            LOGGER.info(
                    "Full ingestion completed scanRunId={} projectKey={} status={} active={} elapsedMs={}",
                    completed.getId(),
                    completed.getProjectKey(),
                    completed.getStatus(),
                    completed.isActive(),
                    elapsedMs
            );

            return new IngestionReportDto(
                    completed.getId(),
                    completed.getProjectKey(),
                    completed.getStatus(),
                    completed.isActive(),
                    resultDtos
            );
        } catch (Exception e) {
            LOGGER.error(
                    "Full ingestion failed scanRunId={} projectKey={} error={}",
                    request.scanRunId(),
                    request.projectKey(),
                    e.getMessage(),
                    e
            );

            scanManagerService.failScan(
                    new FailScanRequest(
                            request.scanRunId(),
                            "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"
                    )
            );

            throw e;
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}