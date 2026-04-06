package com.vena.codesage.graph.repo;

import com.vena.codesage.graph.model.Touchpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TouchpointRepository extends JpaRepository<Touchpoint, Long> {

    List<Touchpoint> findByScanRunIdAndCallerQualifiedName(Long scanRunId, String callerQualifiedName);

    List<Touchpoint> findByScanRunIdAndCategory(Long scanRunId, String category);

    List<Touchpoint> findByScanRunId(Long scanRunId);
}