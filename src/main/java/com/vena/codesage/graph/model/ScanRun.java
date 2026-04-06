package com.vena.codesage.graph.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scan_run")
public class ScanRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 200)
    private String projectKey;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_revision", length = 200)
    private String sourceRevision;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = false;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    public ScanRun() {
    }

    public ScanRun(Long id,
                   String projectKey,
                   String sourceType,
                   String sourceRevision,
                   String status,
                   boolean isActive,
                   LocalDateTime startedAt,
                   LocalDateTime completedAt,
                   String metadataJson) {
        this.id = id;
        this.projectKey = projectKey;
        this.sourceType = sourceType;
        this.sourceRevision = sourceRevision;
        this.status = status;
        this.isActive = isActive;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.metadataJson = metadataJson;
    }

    public static ScanRunBuilder builder() {
        return new ScanRunBuilder();
    }

    public Long getId() {
        return id;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceRevision() {
        return sourceRevision;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return isActive;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public void setSourceRevision(String sourceRevision) {
        this.sourceRevision = sourceRevision;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public static class ScanRunBuilder {
        private Long id;
        private String projectKey;
        private String sourceType;
        private String sourceRevision;
        private String status;
        private boolean isActive = false;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String metadataJson;

        ScanRunBuilder() {
        }

        public ScanRunBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ScanRunBuilder projectKey(String projectKey) {
            this.projectKey = projectKey;
            return this;
        }

        public ScanRunBuilder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public ScanRunBuilder sourceRevision(String sourceRevision) {
            this.sourceRevision = sourceRevision;
            return this;
        }

        public ScanRunBuilder status(String status) {
            this.status = status;
            return this;
        }

        public ScanRunBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public ScanRunBuilder startedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public ScanRunBuilder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public ScanRunBuilder metadataJson(String metadataJson) {
            this.metadataJson = metadataJson;
            return this;
        }

        public ScanRun build() {
            return new ScanRun(
                    this.id,
                    this.projectKey,
                    this.sourceType,
                    this.sourceRevision,
                    this.status,
                    this.isActive,
                    this.startedAt,
                    this.completedAt,
                    this.metadataJson
            );
        }

        @Override
        public String toString() {
            return "ScanRun.ScanRunBuilder(" +
                    "id=" + this.id +
                    ", projectKey=" + this.projectKey +
                    ", sourceType=" + this.sourceType +
                    ", sourceRevision=" + this.sourceRevision +
                    ", status=" + this.status +
                    ", isActive=" + this.isActive +
                    ", startedAt=" + this.startedAt +
                    ", completedAt=" + this.completedAt +
                    ", metadataJson=" + this.metadataJson +
                    ")";
        }
    }
}