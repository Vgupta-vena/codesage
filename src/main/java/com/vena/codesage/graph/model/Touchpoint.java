package com.vena.codesage.graph.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "touchpoint",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scan_run_id", "touchpoint_key"})
)
public class Touchpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;

    @Column(name = "touchpoint_key", nullable = false, length = 128)
    private String touchpointKey;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "caller_qname", nullable = false, length = 2000)
    private String callerQualifiedName;

    @Column(name = "target_qname", length = 2000)
    private String targetQualifiedName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public Touchpoint() {
    }

    public Touchpoint(Long id,
                      Long scanRunId,
                      String touchpointKey,
                      String category,
                      String callerQualifiedName,
                      String targetQualifiedName,
                      String filePath,
                      boolean isActive) {
        this.id = id;
        this.scanRunId = scanRunId;
        this.touchpointKey = touchpointKey;
        this.category = category;
        this.callerQualifiedName = callerQualifiedName;
        this.targetQualifiedName = targetQualifiedName;
        this.filePath = filePath;
        this.isActive = isActive;
    }

    public static TouchpointBuilder builder() {
        return new TouchpointBuilder();
    }

    public Long getId() {
        return id;
    }

    public Long getScanRunId() {
        return scanRunId;
    }

    public String getTouchpointKey() {
        return touchpointKey;
    }

    public String getCategory() {
        return category;
    }

    public String getCallerQualifiedName() {
        return callerQualifiedName;
    }

    public String getTargetQualifiedName() {
        return targetQualifiedName;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setScanRunId(Long scanRunId) {
        this.scanRunId = scanRunId;
    }

    public void setTouchpointKey(String touchpointKey) {
        this.touchpointKey = touchpointKey;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setCallerQualifiedName(String callerQualifiedName) {
        this.callerQualifiedName = callerQualifiedName;
    }

    public void setTargetQualifiedName(String targetQualifiedName) {
        this.targetQualifiedName = targetQualifiedName;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public static class TouchpointBuilder {
        private Long id;
        private Long scanRunId;
        private String touchpointKey;
        private String category;
        private String callerQualifiedName;
        private String targetQualifiedName;
        private String filePath;
        private boolean isActive = true;

        TouchpointBuilder() {
        }

        public TouchpointBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public TouchpointBuilder scanRunId(Long scanRunId) {
            this.scanRunId = scanRunId;
            return this;
        }

        public TouchpointBuilder touchpointKey(String touchpointKey) {
            this.touchpointKey = touchpointKey;
            return this;
        }

        public TouchpointBuilder category(String category) {
            this.category = category;
            return this;
        }

        public TouchpointBuilder callerQualifiedName(String callerQualifiedName) {
            this.callerQualifiedName = callerQualifiedName;
            return this;
        }

        public TouchpointBuilder targetQualifiedName(String targetQualifiedName) {
            this.targetQualifiedName = targetQualifiedName;
            return this;
        }

        public TouchpointBuilder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public TouchpointBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Touchpoint build() {
            return new Touchpoint(
                    this.id,
                    this.scanRunId,
                    this.touchpointKey,
                    this.category,
                    this.callerQualifiedName,
                    this.targetQualifiedName,
                    this.filePath,
                    this.isActive
            );
        }

        @Override
        public String toString() {
            return "Touchpoint.TouchpointBuilder(" +
                    "id=" + this.id +
                    ", scanRunId=" + this.scanRunId +
                    ", touchpointKey=" + this.touchpointKey +
                    ", category=" + this.category +
                    ", callerQualifiedName=" + this.callerQualifiedName +
                    ", targetQualifiedName=" + this.targetQualifiedName +
                    ", filePath=" + this.filePath +
                    ", isActive=" + this.isActive +
                    ")";
        }
    }
}