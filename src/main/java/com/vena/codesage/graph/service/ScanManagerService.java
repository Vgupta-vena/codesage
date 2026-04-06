package com.vena.codesage.graph.service;

import com.vena.codesage.dto.CompleteScanRequest;
import com.vena.codesage.dto.FailScanRequest;
import com.vena.codesage.dto.StartScanRequest;
import com.vena.codesage.dto.StartScanResponse;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.model.ScanStatus;
import com.vena.codesage.graph.repo.ScanRunRepository;
import com.vena.codesage.ingestion.IngestionPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScanManagerService {

    private final ScanRunRepository scanRunRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanManagerService.class);

    public ScanManagerService(ScanRunRepository scanRunRepository) {
        this.scanRunRepository = scanRunRepository;
    }

    @Transactional
    public StartScanResponse startScan(StartScanRequest request) {
        LOGGER.info("Starting scan for request: {}", request);
        validateStartRequest(request);

        ScanRun scanRun = ScanRun.builder()
                .projectKey(request.projectKey())
                .sourceType(defaultIfBlank(request.sourceType(), "CODEQL_DB"))
                .sourceRevision(emptyToNull(request.sourceRevision()))
                .status(ScanStatus.RUNNING.name())
                .isActive(false)
                .startedAt(LocalDateTime.now())
                .metadataJson(emptyToNull(request.metadataJson()))
                .build();

        ScanRun saved = scanRunRepository.save(scanRun);

        return new StartScanResponse(
                saved.getId(),
                saved.getProjectKey(),
                saved.getSourceType(),
                saved.getSourceRevision(),
                saved.getStatus(),
                saved.isActive(),
                saved.getStartedAt()
        );
    }

    @Transactional
    public ScanRun completeScan(CompleteScanRequest request) {
        ScanRun scanRun = scanRunRepository.findById(request.scanRunId())
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + request.scanRunId()));

        scanRun.setStatus(ScanStatus.SUCCESS.name());
        scanRun.setCompletedAt(LocalDateTime.now());

        if (request.metadataJson() != null && !request.metadataJson().isBlank()) {
            scanRun.setMetadataJson(request.metadataJson());
        }

        if (request.activate()) {
            deactivateExistingActiveScans(scanRun.getProjectKey(), scanRun.getId());
            scanRun.setActive(true);
        }

        return scanRunRepository.save(scanRun);
    }

    @Transactional
    public ScanRun failScan(FailScanRequest request) {
        ScanRun scanRun = scanRunRepository.findById(request.scanRunId())
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + request.scanRunId()));

        scanRun.setStatus(ScanStatus.FAILED.name());
        scanRun.setCompletedAt(LocalDateTime.now());

        if (request.metadataJson() != null && !request.metadataJson().isBlank()) {
            scanRun.setMetadataJson(request.metadataJson());
        }

        scanRun.setActive(false);

        return scanRunRepository.save(scanRun);
    }

    public ScanRun getActiveScan(String projectKey) {
        return scanRunRepository.findFirstByProjectKeyAndIsActiveTrueOrderByStartedAtDesc(projectKey)
                .orElseThrow(() -> new IllegalStateException("No active scan found for project: " + projectKey));
    }

    public List<ScanRun> listScans(String projectKey) {
        return scanRunRepository.findByProjectKeyOrderByStartedAtDesc(projectKey);
    }

    @Transactional
    public ScanRun activateScan(Long scanRunId) {
        ScanRun scanRun = scanRunRepository.findById(scanRunId)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + scanRunId));

        if (!ScanStatus.SUCCESS.name().equals(scanRun.getStatus())) {
            throw new IllegalStateException("Only SUCCESS scans can be activated. Scan " + scanRunId + " is " + scanRun.getStatus());
        }

        deactivateExistingActiveScans(scanRun.getProjectKey(), scanRun.getId());
        scanRun.setActive(true);
        return scanRunRepository.save(scanRun);
    }

    @Transactional
    public void supersedeScan(Long scanRunId) {
        ScanRun scanRun = scanRunRepository.findById(scanRunId)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + scanRunId));

        scanRun.setStatus(ScanStatus.SUPERSEDED.name());
        scanRun.setActive(false);
        scanRunRepository.save(scanRun);
    }

    private void deactivateExistingActiveScans(String projectKey, Long exceptScanId) {
        List<ScanRun> activeScans = scanRunRepository.findByProjectKeyAndIsActiveTrue(projectKey);
        for (ScanRun existing : activeScans) {
            if (!existing.getId().equals(exceptScanId)) {
                existing.setActive(false);
                if (ScanStatus.SUCCESS.name().equals(existing.getStatus())) {
                    existing.setStatus(ScanStatus.SUPERSEDED.name());
                }
                scanRunRepository.save(existing);
            }
        }
    }

    private void validateStartRequest(StartScanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        if (request.projectKey() == null || request.projectKey().isBlank()) {
            throw new IllegalArgumentException("projectKey must not be blank");
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}