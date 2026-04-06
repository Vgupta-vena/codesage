package com.vena.codesage.graph.repo;

import com.vena.codesage.graph.model.SemanticDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SemanticDocumentRepository extends JpaRepository<SemanticDocument, Long> {

    List<SemanticDocument> findByScanRunIdOrderByEntityQualifiedNameAsc(Long scanRunId);

    Optional<SemanticDocument> findFirstByScanRunIdAndEntityQualifiedName(Long scanRunId, String entityQualifiedName);

    Optional<SemanticDocument> findFirstByScanRunIdAndDocKey(Long scanRunId, String docKey);

    List<SemanticDocument> findByScanRunId(Long scanRunId);

    void deleteByScanRunId(Long scanRunId);
}