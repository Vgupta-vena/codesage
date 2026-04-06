package com.vena.codesage.graph.service;

import com.vena.codesage.dto.BlastRadiusDto;
import org.springframework.stereotype.Service;

@Service
public class ImpactAnalysisService {

    private final CallGraphService callGraphService;

    public ImpactAnalysisService(CallGraphService callGraphService) {
        this.callGraphService = callGraphService;
    }

    public BlastRadiusDto blastRadius(String projectKey,
                                      String methodQualifiedName,
                                      int depth,
                                      boolean excludeTests,
                                      boolean excludeAnonymous,
                                      Integer limit) {
        return callGraphService.computeBlastRadius(
                projectKey,
                methodQualifiedName,
                depth,
                excludeTests,
                excludeAnonymous,
                limit
        );
    }
}