package com.vena.codesage.graph.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "code_entity",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scan_run_id", "entity_key"})
)
public class CodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;

    @Column(name = "entity_key", nullable = false, length = 128)
    private String entityKey;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "declaring_type")
    private String declaringType;

    @Column(name = "simple_name")
    private String simpleName;

    @Column(name = "qualified_name", nullable = false, length = 2000)
    private String qualifiedName;

    @Column(name = "signature")
    private String signature;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public CodeEntity() {
    }

    public CodeEntity(Long id,
                      Long scanRunId,
                      String entityKey,
                      String entityType,
                      String packageName,
                      String declaringType,
                      String simpleName,
                      String qualifiedName,
                      String signature,
                      String filePath,
                      String summary,
                      String contentHash,
                      boolean isActive) {
        this.id = id;
        this.scanRunId = scanRunId;
        this.entityKey = entityKey;
        this.entityType = entityType;
        this.packageName = packageName;
        this.declaringType = declaringType;
        this.simpleName = simpleName;
        this.qualifiedName = qualifiedName;
        this.signature = signature;
        this.filePath = filePath;
        this.summary = summary;
        this.contentHash = contentHash;
        this.isActive = isActive;
    }

    public static CodeEntityBuilder builder() {
        return new CodeEntityBuilder();
    }

    public Long getId() {
        return id;
    }

    public Long getScanRunId() {
        return scanRunId;
    }

    public String getEntityKey() {
        return entityKey;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getDeclaringType() {
        return declaringType;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getSignature() {
        return signature;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSummary() {
        return summary;
    }

    public String getContentHash() {
        return contentHash;
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

    public void setEntityKey(String entityKey) {
        this.entityKey = entityKey;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setDeclaringType(String declaringType) {
        this.declaringType = declaringType;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public static class CodeEntityBuilder {
        private Long id;
        private Long scanRunId;
        private String entityKey;
        private String entityType;
        private String packageName;
        private String declaringType;
        private String simpleName;
        private String qualifiedName;
        private String signature;
        private String filePath;
        private String summary;
        private String contentHash;
        private boolean isActive = true;

        CodeEntityBuilder() {
        }

        public CodeEntityBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CodeEntityBuilder scanRunId(Long scanRunId) {
            this.scanRunId = scanRunId;
            return this;
        }

        public CodeEntityBuilder entityKey(String entityKey) {
            this.entityKey = entityKey;
            return this;
        }

        public CodeEntityBuilder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public CodeEntityBuilder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public CodeEntityBuilder declaringType(String declaringType) {
            this.declaringType = declaringType;
            return this;
        }

        public CodeEntityBuilder simpleName(String simpleName) {
            this.simpleName = simpleName;
            return this;
        }

        public CodeEntityBuilder qualifiedName(String qualifiedName) {
            this.qualifiedName = qualifiedName;
            return this;
        }

        public CodeEntityBuilder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public CodeEntityBuilder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public CodeEntityBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public CodeEntityBuilder contentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public CodeEntityBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public CodeEntity build() {
            return new CodeEntity(
                    this.id,
                    this.scanRunId,
                    this.entityKey,
                    this.entityType,
                    this.packageName,
                    this.declaringType,
                    this.simpleName,
                    this.qualifiedName,
                    this.signature,
                    this.filePath,
                    this.summary,
                    this.contentHash,
                    this.isActive
            );
        }

        @Override
        public String toString() {
            return "CodeEntity.CodeEntityBuilder(" +
                    "id=" + this.id +
                    ", scanRunId=" + this.scanRunId +
                    ", entityKey=" + this.entityKey +
                    ", entityType=" + this.entityType +
                    ", packageName=" + this.packageName +
                    ", declaringType=" + this.declaringType +
                    ", simpleName=" + this.simpleName +
                    ", qualifiedName=" + this.qualifiedName +
                    ", signature=" + this.signature +
                    ", filePath=" + this.filePath +
                    ", summary=" + this.summary +
                    ", contentHash=" + this.contentHash +
                    ", isActive=" + this.isActive +
                    ")";
        }
    }
}