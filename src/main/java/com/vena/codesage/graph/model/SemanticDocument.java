package com.vena.codesage.graph.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "semantic_document",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scan_run_id", "doc_key"})
)
public class SemanticDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;

    @Column(name = "doc_key", nullable = false, length = 128)
    private String docKey;

    @Column(name = "entity_qualified_name", nullable = false, columnDefinition = "text")
    private String entityQualifiedName;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "doc_type", nullable = false, length = 64)
    private String docType;

    @Column(name = "file_path", columnDefinition = "text")
    private String filePath;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public SemanticDocument() {
    }

    public SemanticDocument(Long id,
                            Long scanRunId,
                            String docKey,
                            String entityQualifiedName,
                            String entityType,
                            String docType,
                            String filePath,
                            String content,
                            String contentHash,
                            boolean isActive) {
        this.id = id;
        this.scanRunId = scanRunId;
        this.docKey = docKey;
        this.entityQualifiedName = entityQualifiedName;
        this.entityType = entityType;
        this.docType = docType;
        this.filePath = filePath;
        this.content = content;
        this.contentHash = contentHash;
        this.isActive = isActive;
    }

    public static SemanticDocumentBuilder builder() {
        return new SemanticDocumentBuilder();
    }

    public Long getId() {
        return id;
    }

    public Long getScanRunId() {
        return scanRunId;
    }

    public String getDocKey() {
        return docKey;
    }

    public String getEntityQualifiedName() {
        return entityQualifiedName;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getDocType() {
        return docType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getContent() {
        return content;
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

    public void setDocKey(String docKey) {
        this.docKey = docKey;
    }

    public void setEntityQualifiedName(String entityQualifiedName) {
        this.entityQualifiedName = entityQualifiedName;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public static class SemanticDocumentBuilder {
        private Long id;
        private Long scanRunId;
        private String docKey;
        private String entityQualifiedName;
        private String entityType;
        private String docType;
        private String filePath;
        private String content;
        private String contentHash;
        private boolean isActive = true;

        public SemanticDocumentBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public SemanticDocumentBuilder scanRunId(Long scanRunId) {
            this.scanRunId = scanRunId;
            return this;
        }

        public SemanticDocumentBuilder docKey(String docKey) {
            this.docKey = docKey;
            return this;
        }

        public SemanticDocumentBuilder entityQualifiedName(String entityQualifiedName) {
            this.entityQualifiedName = entityQualifiedName;
            return this;
        }

        public SemanticDocumentBuilder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public SemanticDocumentBuilder docType(String docType) {
            this.docType = docType;
            return this;
        }

        public SemanticDocumentBuilder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public SemanticDocumentBuilder content(String content) {
            this.content = content;
            return this;
        }

        public SemanticDocumentBuilder contentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public SemanticDocumentBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public SemanticDocument build() {
            return new SemanticDocument(
                    id,
                    scanRunId,
                    docKey,
                    entityQualifiedName,
                    entityType,
                    docType,
                    filePath,
                    content,
                    contentHash,
                    isActive
            );
        }
    }
}