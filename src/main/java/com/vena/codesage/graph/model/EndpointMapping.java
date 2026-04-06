package com.vena.codesage.graph.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "endpoint_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scan_run_id", "endpoint_key"})
)
public class EndpointMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;

    @Column(name = "endpoint_key", nullable = false, length = 128)
    private String endpointKey;

    @Column(name = "http_method", length = 32)
    private String httpMethod;

    @Column(name = "class_path")
    private String classPath;

    @Column(name = "method_path")
    private String methodPath;

    @Column(name = "method_qname", nullable = false, length = 2000)
    private String methodQualifiedName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public EndpointMapping() {
    }

    public EndpointMapping(Long id,
                           Long scanRunId,
                           String endpointKey,
                           String httpMethod,
                           String classPath,
                           String methodPath,
                           String methodQualifiedName,
                           String filePath,
                           boolean isActive) {
        this.id = id;
        this.scanRunId = scanRunId;
        this.endpointKey = endpointKey;
        this.httpMethod = httpMethod;
        this.classPath = classPath;
        this.methodPath = methodPath;
        this.methodQualifiedName = methodQualifiedName;
        this.filePath = filePath;
        this.isActive = isActive;
    }

    public static EndpointMappingBuilder builder() {
        return new EndpointMappingBuilder();
    }

    public Long getId() {
        return id;
    }

    public Long getScanRunId() {
        return scanRunId;
    }

    public String getEndpointKey() {
        return endpointKey;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getClassPath() {
        return classPath;
    }

    public String getMethodPath() {
        return methodPath;
    }

    public String getMethodQualifiedName() {
        return methodQualifiedName;
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

    public void setEndpointKey(String endpointKey) {
        this.endpointKey = endpointKey;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public void setClassPath(String classPath) {
        this.classPath = classPath;
    }

    public void setMethodPath(String methodPath) {
        this.methodPath = methodPath;
    }

    public void setMethodQualifiedName(String methodQualifiedName) {
        this.methodQualifiedName = methodQualifiedName;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public static class EndpointMappingBuilder {
        private Long id;
        private Long scanRunId;
        private String endpointKey;
        private String httpMethod;
        private String classPath;
        private String methodPath;
        private String methodQualifiedName;
        private String filePath;
        private boolean isActive = true;

        EndpointMappingBuilder() {
        }

        public EndpointMappingBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public EndpointMappingBuilder scanRunId(Long scanRunId) {
            this.scanRunId = scanRunId;
            return this;
        }

        public EndpointMappingBuilder endpointKey(String endpointKey) {
            this.endpointKey = endpointKey;
            return this;
        }

        public EndpointMappingBuilder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public EndpointMappingBuilder classPath(String classPath) {
            this.classPath = classPath;
            return this;
        }

        public EndpointMappingBuilder methodPath(String methodPath) {
            this.methodPath = methodPath;
            return this;
        }

        public EndpointMappingBuilder methodQualifiedName(String methodQualifiedName) {
            this.methodQualifiedName = methodQualifiedName;
            return this;
        }

        public EndpointMappingBuilder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public EndpointMappingBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public EndpointMapping build() {
            return new EndpointMapping(
                    this.id,
                    this.scanRunId,
                    this.endpointKey,
                    this.httpMethod,
                    this.classPath,
                    this.methodPath,
                    this.methodQualifiedName,
                    this.filePath,
                    this.isActive
            );
        }

        @Override
        public String toString() {
            return "EndpointMapping.EndpointMappingBuilder(" +
                    "id=" + this.id +
                    ", scanRunId=" + this.scanRunId +
                    ", endpointKey=" + this.endpointKey +
                    ", httpMethod=" + this.httpMethod +
                    ", classPath=" + this.classPath +
                    ", methodPath=" + this.methodPath +
                    ", methodQualifiedName=" + this.methodQualifiedName +
                    ", filePath=" + this.filePath +
                    ", isActive=" + this.isActive +
                    ")";
        }
    }
}