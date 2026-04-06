package com.vena.codesage.api;

import com.vena.codesage.dto.CompleteScanRequest;
import com.vena.codesage.dto.FailScanRequest;
import com.vena.codesage.dto.IngestionReportDto;
import com.vena.codesage.dto.StartScanRequest;
import com.vena.codesage.dto.StartScanResponse;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.service.ScanManagerService;
import com.vena.codesage.ingestion.IngestionPipeline;
import com.vena.codesage.ingestion.IngestionRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final ScanManagerService scanManagerService;
    private final IngestionPipeline ingestionPipeline;

    public IngestionController(ScanManagerService scanManagerService,
                               IngestionPipeline ingestionPipeline) {
        this.scanManagerService = scanManagerService;
        this.ingestionPipeline = ingestionPipeline;
    }

    @PostMapping("/start-scan")
    public StartScanResponse startScan(@RequestBody StartScanRequest request) {
        return scanManagerService.startScan(request);
    }

    @PostMapping("/run-full")
    public IngestionReportDto runFull(@RequestParam Long scanRunId,
                                      @RequestParam String projectKey,
                                      @RequestParam(defaultValue = "./data") String baseDir,
                                      @RequestParam(defaultValue = "true") boolean activateAfterSuccess) {
        return ingestionPipeline.runFull(
                new IngestionRequest(scanRunId, projectKey, baseDir),
                activateAfterSuccess
        );
    }

    @PostMapping("/complete-scan")
    public ScanRun completeScan(@RequestBody CompleteScanRequest request) {
        return scanManagerService.completeScan(request);
    }

    @PostMapping("/fail-scan")
    public ScanRun failScan(@RequestBody FailScanRequest request) {
        return scanManagerService.failScan(request);
    }

    @PostMapping("/activate/{scanRunId}")
    public ScanRun activateScan(@PathVariable Long scanRunId) {
        return scanManagerService.activateScan(scanRunId);
    }

    @GetMapping("/active")
    public ScanRun activeScan(@RequestParam String projectKey) {
        return scanManagerService.getActiveScan(projectKey);
    }

    @GetMapping("/scans")
    public List<ScanRun> scans(@RequestParam String projectKey) {
        return scanManagerService.listScans(projectKey);
    }
}