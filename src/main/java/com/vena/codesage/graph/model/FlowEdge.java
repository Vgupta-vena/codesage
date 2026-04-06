package com.vena.codesage.graph.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "flow_edge",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scan_run_id", "flow_key"})
)
public class FlowEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_run_id", nullable = false)
    private Long scanRunId;

    @Column(name = "flow_key", nullable = false, length = 128)
    private String flowKey;

    @Column(name = "source_method_qname", nullable = false, length = 2000)
    private String sourceMethodQualifiedName;

    @Column(name = "parameter_name")
    private String parameterName;

    @Column(name = "sink_method_qname", nullable = false, length = 2000)
    private String sinkMethodQualifiedName;

    @Column(name = "source_declaring_type")
    private String sourceDeclaringType;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public FlowEdge() {
    }

    public FlowEdge(Long id,
                    Long scanRunId,
                    String flowKey,
                    String sourceMethodQualifiedName,
                    String parameterName,
                    String sinkMethodQualifiedName,
                    String sourceDeclaringType,
                    boolean isActive) {
        this.id = id;
        this.scanRunId = scanRunId;
        this.flowKey = flowKey;
        this.sourceMethodQualifiedName = sourceMethodQualifiedName;
        this.parameterName = parameterName;
        this.sinkMethodQualifiedName = sinkMethodQualifiedName;
        this.sourceDeclaringType = sourceDeclaringType;
        this.isActive = isActive;
    }

    public static FlowEdgeBuilder builder() {
        return new FlowEdgeBuilder();
    }

    public Long getId() {
        return id;
    }

    public Long getScanRunId() {
        return scanRunId;
    }

    public String getFlowKey() {
        return flowKey;
    }

    public String getSourceMethodQualifiedName() {
        return sourceMethodQualifiedName;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getSinkMethodQualifiedName() {
        return sinkMethodQualifiedName;
    }

    public String getSourceDeclaringType() {
        return sourceDeclaringType;
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

    public void setFlowKey(String flowKey) {
        this.flowKey = flowKey;
    }

    public void setSourceMethodQualifiedName(String sourceMethodQualifiedName) {
        this.sourceMethodQualifiedName = sourceMethodQualifiedName;
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public void setSinkMethodQualifiedName(String sinkMethodQualifiedName) {
        this.sinkMethodQualifiedName = sinkMethodQualifiedName;
    }

    public void setSourceDeclaringType(String sourceDeclaringType) {
        this.sourceDeclaringType = sourceDeclaringType;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public static class FlowEdgeBuilder {
        private Long id;
        private Long scanRunId;
        private String flowKey;
        private String sourceMethodQualifiedName;
        private String parameterName;
        private String sinkMethodQualifiedName;
        private String sourceDeclaringType;
        private boolean isActive = true;

        FlowEdgeBuilder() {
        }

        public FlowEdgeBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public FlowEdgeBuilder scanRunId(Long scanRunId) {
            this.scanRunId = scanRunId;
            return this;
        }

        public FlowEdgeBuilder flowKey(String flowKey) {
            this.flowKey = flowKey;
            return this;
        }

        public FlowEdgeBuilder sourceMethodQualifiedName(String sourceMethodQualifiedName) {
            this.sourceMethodQualifiedName = sourceMethodQualifiedName;
            return this;
        }

        public FlowEdgeBuilder parameterName(String parameterName) {
            this.parameterName = parameterName;
            return this;
        }

        public FlowEdgeBuilder sinkMethodQualifiedName(String sinkMethodQualifiedName) {
            this.sinkMethodQualifiedName = sinkMethodQualifiedName;
            return this;
        }

        public FlowEdgeBuilder sourceDeclaringType(String sourceDeclaringType) {
            this.sourceDeclaringType = sourceDeclaringType;
            return this;
        }

        public FlowEdgeBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public FlowEdge build() {
            return new FlowEdge(
                    this.id,
                    this.scanRunId,
                    this.flowKey,
                    this.sourceMethodQualifiedName,
                    this.parameterName,
                    this.sinkMethodQualifiedName,
                    this.sourceDeclaringType,
                    this.isActive
            );
        }

        @Override
        public String toString() {
            return "FlowEdge.FlowEdgeBuilder(" +
                    "id=" + this.id +
                    ", scanRunId=" + this.scanRunId +
                    ", flowKey=" + this.flowKey +
                    ", sourceMethodQualifiedName=" + this.sourceMethodQualifiedName +
                    ", parameterName=" + this.parameterName +
                    ", sinkMethodQualifiedName=" + this.sinkMethodQualifiedName +
                    ", sourceDeclaringType=" + this.sourceDeclaringType +
                    ", isActive=" + this.isActive +
                    ")";
        }
    }
}