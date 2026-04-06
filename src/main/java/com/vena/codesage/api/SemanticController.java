package com.vena.codesage.api;

import com.vena.codesage.dto.IngestionResultDto;
import com.vena.codesage.dto.SemanticBuildResponse;
import com.vena.codesage.dto.SemanticDocumentDto;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.service.ScanManagerService;
import com.vena.codesage.graph.service.SemanticDocumentBuilderService;
import com.vena.codesage.ingestion.IngestionRequest;
import com.vena.codesage.ingestion.IngestionResult;
import com.vena.codesage.ingestion.jobs.SemanticEnrichmentJob;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/semantic")
public class SemanticController {

    private final SemanticDocumentBuilderService semanticDocumentBuilderService;
    private final ScanManagerService scanManagerService;
    private final SemanticEnrichmentJob semanticEnrichmentJob;

    public SemanticController(SemanticDocumentBuilderService semanticDocumentBuilderService,
                              ScanManagerService scanManagerService,
                              SemanticEnrichmentJob semanticEnrichmentJob) {
        this.semanticDocumentBuilderService = semanticDocumentBuilderService;
        this.scanManagerService = scanManagerService;
        this.semanticEnrichmentJob = semanticEnrichmentJob;
    }

    @PostMapping("/build")
    public SemanticBuildResponse build(@RequestParam String projectKey,
                                       @RequestParam(defaultValue = "false") boolean enrichFirst) {
        if (enrichFirst) {
            ScanRun activeScan = scanManagerService.getActiveScan(projectKey);
            semanticEnrichmentJob.run(new IngestionRequest(activeScan.getId(), projectKey, null));
        }

        return semanticDocumentBuilderService.buildForActiveScan(projectKey);
    }

    @PostMapping("/enrich")
    public IngestionResultDto enrich(@RequestParam String projectKey) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);
        IngestionResult result = semanticEnrichmentJob.run(
                new IngestionRequest(activeScan.getId(), projectKey, null)
        );
        return new IngestionResultDto(result.jobName(), result.recordsProcessed());
    }

    @GetMapping("/document")
    public SemanticDocumentDto document(@RequestParam String projectKey,
                                        @RequestParam String qualifiedName) {
        return semanticDocumentBuilderService.findForActiveScan(projectKey, qualifiedName);
    }
}