package com.vena.codesage.graph.service;

import com.vena.codesage.dto.KnowledgeResultItemDto;
import com.vena.codesage.dto.TraceDirection;
import com.vena.codesage.dto.TraceNodeDto;
import com.vena.codesage.dto.TraceResponseDto;
import com.vena.codesage.graph.model.CallEdge;
import com.vena.codesage.graph.model.CodeEntity;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.repo.CallEdgeRepository;
import com.vena.codesage.graph.repo.CodeEntityRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReverseTraversalService {

    private final ScanManagerService scanManagerService;
    private final CallEdgeRepository callEdgeRepository;
    private final CodeEntityRepository codeEntityRepository;
    private final EndpointSearchService endpointSearchService;

    public ReverseTraversalService(ScanManagerService scanManagerService,
                                   CallEdgeRepository callEdgeRepository,
                                   CodeEntityRepository codeEntityRepository,
                                   EndpointSearchService endpointSearchService) {
        this.scanManagerService = scanManagerService;
        this.callEdgeRepository = callEdgeRepository;
        this.codeEntityRepository = codeEntityRepository;
        this.endpointSearchService = endpointSearchService;
    }

    public TraceResponseDto trace(String projectKey,
                                  String seedQualifiedName,
                                  TraceDirection direction,
                                  int depth,
                                  int limit) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);
        Long scanRunId = activeScan.getId();

        int effectiveDepth = Math.max(1, depth);
        int effectiveLimit = Math.max(1, limit);

        Map<String, CodeEntity> entitiesByQualifiedName = preloadEntities(scanRunId);
        Map<String, List<String>> callersByCallee = new HashMap<>();
        Map<String, List<String>> calleesByCaller = new HashMap<>();
        preloadCallMaps(scanRunId, callersByCallee, calleesByCaller);

        List<TraceNodeDto> upstream = List.of();
        List<TraceNodeDto> downstream = List.of();

        if (direction == TraceDirection.UPSTREAM || direction == TraceDirection.BOTH) {
            upstream = bfs(
                    seedQualifiedName,
                    effectiveDepth,
                    effectiveLimit,
                    callersByCallee,
                    entitiesByQualifiedName,
                    "CALLER"
            );
        }

        if (direction == TraceDirection.DOWNSTREAM || direction == TraceDirection.BOTH) {
            downstream = bfs(
                    seedQualifiedName,
                    effectiveDepth,
                    effectiveLimit,
                    calleesByCaller,
                    entitiesByQualifiedName,
                    "CALLEE"
            );
        }

        List<KnowledgeResultItemDto> reachableEndpoints = endpointSearchService.search(
                        projectKey,
                        seedQualifiedName,
                        effectiveLimit,
                        false
                ).stream()
                .map(endpoint -> KnowledgeResultItemDto.endpoint(
                        endpoint.methodQualifiedName(),
                        endpoint.httpMethod() + " " + endpoint.path(),
                        endpoint.filePath(),
                        endpoint.unresolvedPath() ? "Endpoint mapping with unresolved path" : "Resolved endpoint mapping",
                        endpoint.score(),
                        List.of()
                ))
                .toList();

        String summary = buildSummary(seedQualifiedName, direction, upstream, downstream, reachableEndpoints);

        return new TraceResponseDto(
                projectKey,
                seedQualifiedName,
                direction,
                effectiveDepth,
                upstream,
                downstream,
                reachableEndpoints,
                summary
        );
    }

    private Map<String, CodeEntity> preloadEntities(Long scanRunId) {
        Map<String, CodeEntity> map = new HashMap<>();
        for (CodeEntity entity : codeEntityRepository.findByScanRunId(scanRunId)) {
            map.put(entity.getQualifiedName(), entity);
        }
        return map;
    }

    private void preloadCallMaps(Long scanRunId,
                                 Map<String, List<String>> callersByCallee,
                                 Map<String, List<String>> calleesByCaller) {
        for (CallEdge edge : callEdgeRepository.findByScanRunId(scanRunId)) {
            callersByCallee
                    .computeIfAbsent(edge.getCalleeQualifiedName(), k -> new ArrayList<>())
                    .add(edge.getCallerQualifiedName());

            calleesByCaller
                    .computeIfAbsent(edge.getCallerQualifiedName(), k -> new ArrayList<>())
                    .add(edge.getCalleeQualifiedName());
        }
    }

    private List<TraceNodeDto> bfs(String seedQualifiedName,
                                   int maxDepth,
                                   int limit,
                                   Map<String, List<String>> adjacency,
                                   Map<String, CodeEntity> entitiesByQualifiedName,
                                   String relation) {
        List<TraceNodeDto> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<NodeDepth> queue = new ArrayDeque<>();

        visited.add(seedQualifiedName);
        queue.add(new NodeDepth(seedQualifiedName, 0));

        while (!queue.isEmpty() && result.size() < limit) {
            NodeDepth current = queue.poll();

            if (current.depth() >= maxDepth) {
                continue;
            }

            List<String> nextNodes = adjacency.getOrDefault(current.qualifiedName(), List.of());
            for (String next : nextNodes) {
                if (!visited.add(next)) {
                    continue;
                }

                CodeEntity entity = entitiesByQualifiedName.get(next);
                result.add(new TraceNodeDto(
                        next,
                        entity != null ? entity.getEntityType() : "UNKNOWN",
                        entity != null ? entity.getFilePath() : "",
                        current.depth() + 1,
                        relation
                ));

                if (result.size() >= limit) {
                    break;
                }

                queue.add(new NodeDepth(next, current.depth() + 1));
            }
        }

        result.sort(Comparator
                .comparingInt(TraceNodeDto::depth)
                .thenComparing(TraceNodeDto::qualifiedName));

        return result;
    }

    private String buildSummary(String seedQualifiedName,
                                TraceDirection direction,
                                List<TraceNodeDto> upstream,
                                List<TraceNodeDto> downstream,
                                List<KnowledgeResultItemDto> endpoints) {
        return "Trace for " + seedQualifiedName
                + " direction=" + direction
                + " upstream=" + upstream.size()
                + " downstream=" + downstream.size()
                + " endpoints=" + endpoints.size();
    }

    private record NodeDepth(String qualifiedName, int depth) {
    }
}