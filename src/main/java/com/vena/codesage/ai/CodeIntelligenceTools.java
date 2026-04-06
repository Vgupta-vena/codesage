package com.vena.codesage.ai;

import com.vena.codesage.dto.BlastRadiusDto;
import com.vena.codesage.dto.EntitySummaryDto;
import com.vena.codesage.dto.KnowledgeMode;
import com.vena.codesage.dto.KnowledgeResponseDto;
import com.vena.codesage.graph.service.CodeEntityService;
import com.vena.codesage.graph.service.CodebaseKnowledgeService;
import com.vena.codesage.graph.service.ImpactAnalysisService;
import com.vena.codesage.graph.service.ReverseTraversalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CodeIntelligenceTools {

    private final CodebaseKnowledgeService codebaseKnowledgeService;
    private final CodeEntityService codeEntityService;
    private final ImpactAnalysisService impactAnalysisService;
    private final ReverseTraversalService reverseTraversalService;

    public CodeIntelligenceTools(CodebaseKnowledgeService codebaseKnowledgeService,
                                 CodeEntityService codeEntityService,
                                 ImpactAnalysisService impactAnalysisService, ReverseTraversalService reverseTraversalService) {
        this.codebaseKnowledgeService = codebaseKnowledgeService;
        this.codeEntityService = codeEntityService;
        this.impactAnalysisService = impactAnalysisService;
        this.reverseTraversalService = reverseTraversalService;
    }

    @Tool(description = "Query the codebase knowledge layer for entities, endpoints, or semantic matches")
    public KnowledgeResponseDto queryProject(String projectKey, String query) {
        return codebaseKnowledgeService.query(
                projectKey,
                query,
                8,
                false,
                KnowledgeMode.AUTO,
                true,
                null
        );
    }

    @Tool(description = "Describe a class or method using graph metadata for a given project")
    public EntitySummaryDto describeEntity(String projectKey, String qualifiedName) {
        return codeEntityService.describeEntity(
                projectKey,
                qualifiedName,
                true,
                true,
                50
        );
    }

    @Tool(description = "Compute blast radius for a method")
    public BlastRadiusDto blastRadius(String projectKey,
                                      String qualifiedMethod,
                                      int depth,
                                      boolean excludeTests,
                                      boolean excludeAnonymous,
                                      int limit) {
        return impactAnalysisService.blastRadius(
                projectKey,
                qualifiedMethod,
                depth,
                excludeTests,
                excludeAnonymous,
                limit
        );
    }

    @Tool(description = "Trace upstream and downstream callers, callees, and reachable endpoints for a code entity")
    public com.vena.codesage.dto.TraceResponseDto traceEntity(String projectKey,
                                                              String qualifiedName,
                                                              String direction,
                                                              int depth,
                                                              int limit) {
        com.vena.codesage.dto.TraceDirection traceDirection =
                com.vena.codesage.dto.TraceDirection.valueOf(direction.toUpperCase());

        return reverseTraversalService.trace(projectKey, qualifiedName, traceDirection, depth, limit);
    }
}