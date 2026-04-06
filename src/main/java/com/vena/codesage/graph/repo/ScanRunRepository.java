package com.vena.codesage.graph.repo;

import com.vena.codesage.graph.model.ScanRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScanRunRepository extends JpaRepository<ScanRun, Long> {

    Optional<ScanRun> findFirstByProjectKeyAndIsActiveTrueOrderByStartedAtDesc(String projectKey);

    List<ScanRun> findByProjectKeyOrderByStartedAtDesc(String projectKey);

    List<ScanRun> findByProjectKeyAndIsActiveTrue(String projectKey);
}