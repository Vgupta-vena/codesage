package com.vena.codesage.graph.repo;

import com.vena.codesage.graph.model.FlowEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlowEdgeRepository extends JpaRepository<FlowEdge, Long> {

    List<FlowEdge> findByScanRunIdAndSourceMethodQualifiedName(Long scanRunId, String sourceMethodQualifiedName);
    List<FlowEdge> findByScanRunId(Long scanRunId);
    List<FlowEdge> findByScanRunIdAndSinkMethodQualifiedName(Long scanRunId, String sinkMethodQualifiedName);
}