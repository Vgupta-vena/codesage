package com.vena.codesage.graph.repo;

import com.vena.codesage.graph.model.EndpointMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EndpointMappingRepository extends JpaRepository<EndpointMapping, Long> {

    List<EndpointMapping> findByScanRunIdAndMethodQualifiedName(Long scanRunId, String methodQualifiedName);

    List<EndpointMapping> findByScanRunIdAndMethodQualifiedNameStartingWith(Long scanRunId, String prefix);

    List<EndpointMapping> findByScanRunId(Long scanRunId);
}