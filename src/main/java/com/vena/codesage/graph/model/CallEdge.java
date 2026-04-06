package com.vena.codesage.graph.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "call_edge",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scan_run_id", "edge_key"})
)
public class CallEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;

    @Column(name = "edge_key", nullable = false, length = 128)
    private String edgeKey;

    @Column(name = "caller_entity_key", nullable = false, length = 128)
    private String callerEntityKey;

    @Column(name = "callee_entity_key", nullable = false, length = 128)
    private String calleeEntityKey;

    @Column(name = "caller_qname", nullable = false, length = 2000)
    private String callerQualifiedName;

    @Column(name = "callee_qname", nullable = false, length = 2000)
    private String calleeQualifiedName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "edge_type", nullable = false, length = 64)
    private String edgeType;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public CallEdge() {
    }

    public CallEdge(Long id,
                    Long scanRunId,
                    String edgeKey,
                    String callerEntityKey,
                    String calleeEntityKey,
                    String callerQualifiedName,
                    String calleeQualifiedName,
                    String filePath,
                    Integer lineNumber,
                    String edgeType,
                    boolean isActive) {
        this.id = id;
        this.scanRunId = scanRunId;
        this.edgeKey = edgeKey;
        this.callerEntityKey = callerEntityKey;
        this.calleeEntityKey = calleeEntityKey;
        this.callerQualifiedName = callerQualifiedName;
        this.calleeQualifiedName = calleeQualifiedName;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.edgeType = edgeType;
        this.isActive = isActive;
    }

    public static CallEdgeBuilder builder() {
        return new CallEdgeBuilder();
    }

    public Long getId() {
        return id;
    }

    public Long getScanRunId() {
        return scanRunId;
    }

    public String getEdgeKey() {
        return edgeKey;
    }

    public String getCallerEntityKey() {
        return callerEntityKey;
    }

    public String getCalleeEntityKey() {
        return calleeEntityKey;
    }

    public String getCallerQualifiedName() {
        return callerQualifiedName;
    }

    public String getCalleeQualifiedName() {
        return calleeQualifiedName;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public String getEdgeType() {
        return edgeType;
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

    public void setEdgeKey(String edgeKey) {
        this.edgeKey = edgeKey;
    }

    public void setCallerEntityKey(String callerEntityKey) {
        this.callerEntityKey = callerEntityKey;
    }

    public void setCalleeEntityKey(String calleeEntityKey) {
        this.calleeEntityKey = calleeEntityKey;
    }

    public void setCallerQualifiedName(String callerQualifiedName) {
        this.callerQualifiedName = callerQualifiedName;
    }

    public void setCalleeQualifiedName(String calleeQualifiedName) {
        this.calleeQualifiedName = calleeQualifiedName;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public void setEdgeType(String edgeType) {
        this.edgeType = edgeType;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public static class CallEdgeBuilder {
        private Long id;
        private Long scanRunId;
        private String edgeKey;
        private String callerEntityKey;
        private String calleeEntityKey;
        private String callerQualifiedName;
        private String calleeQualifiedName;
        private String filePath;
        private Integer lineNumber;
        private String edgeType;
        private boolean isActive = true;

        CallEdgeBuilder() {
        }

        public CallEdgeBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CallEdgeBuilder scanRunId(Long scanRunId) {
            this.scanRunId = scanRunId;
            return this;
        }

        public CallEdgeBuilder edgeKey(String edgeKey) {
            this.edgeKey = edgeKey;
            return this;
        }

        public CallEdgeBuilder callerEntityKey(String callerEntityKey) {
            this.callerEntityKey = callerEntityKey;
            return this;
        }

        public CallEdgeBuilder calleeEntityKey(String calleeEntityKey) {
            this.calleeEntityKey = calleeEntityKey;
            return this;
        }

        public CallEdgeBuilder callerQualifiedName(String callerQualifiedName) {
            this.callerQualifiedName = callerQualifiedName;
            return this;
        }

        public CallEdgeBuilder calleeQualifiedName(String calleeQualifiedName) {
            this.calleeQualifiedName = calleeQualifiedName;
            return this;
        }

        public CallEdgeBuilder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public CallEdgeBuilder lineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public CallEdgeBuilder edgeType(String edgeType) {
            this.edgeType = edgeType;
            return this;
        }

        public CallEdgeBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public CallEdge build() {
            return new CallEdge(
                    this.id,
                    this.scanRunId,
                    this.edgeKey,
                    this.callerEntityKey,
                    this.calleeEntityKey,
                    this.callerQualifiedName,
                    this.calleeQualifiedName,
                    this.filePath,
                    this.lineNumber,
                    this.edgeType,
                    this.isActive
            );
        }

        @Override
        public String toString() {
            return "CallEdge.CallEdgeBuilder(" +
                    "id=" + this.id +
                    ", scanRunId=" + this.scanRunId +
                    ", edgeKey=" + this.edgeKey +
                    ", callerEntityKey=" + this.callerEntityKey +
                    ", calleeEntityKey=" + this.calleeEntityKey +
                    ", callerQualifiedName=" + this.callerQualifiedName +
                    ", calleeQualifiedName=" + this.calleeQualifiedName +
                    ", filePath=" + this.filePath +
                    ", lineNumber=" + this.lineNumber +
                    ", edgeType=" + this.edgeType +
                    ", isActive=" + this.isActive +
                    ")";
        }
    }
}