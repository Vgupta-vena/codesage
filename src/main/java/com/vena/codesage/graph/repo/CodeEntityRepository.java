package com.vena.codesage.graph.repo;

import com.vena.codesage.graph.model.CodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodeEntityRepository extends JpaRepository<CodeEntity, Long> {

    Optional<CodeEntity> findFirstByScanRunIdAndQualifiedName(Long scanRunId, String qualifiedName);

    Optional<CodeEntity> findFirstByScanRunIdAndEntityKey(Long scanRunId, String entityKey);

    List<CodeEntity> findByScanRunIdAndDeclaringType(Long scanRunId, String declaringType);

    List<CodeEntity> findByScanRunIdAndQualifiedNameContainingIgnoreCase(Long scanRunId, String text);

    List<CodeEntity> findByScanRunIdAndEntityType(Long scanRunId, String entityType);

    List<CodeEntity> findByScanRunId(Long scanRunId);
}