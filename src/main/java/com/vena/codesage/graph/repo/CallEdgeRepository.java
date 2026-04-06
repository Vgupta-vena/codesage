package com.vena.codesage.graph.repo;

import com.vena.codesage.graph.model.CallEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallEdgeRepository extends JpaRepository<CallEdge, Long> {

    List<CallEdge> findByScanRunIdAndCallerQualifiedName(Long scanRunId, String callerQualifiedName);

    List<CallEdge> findByScanRunIdAndCalleeQualifiedName(Long scanRunId, String calleeQualifiedName);

    List<CallEdge> findByScanRunId(Long scanRunId);
}