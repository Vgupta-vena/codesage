package com.vena.codesage.graph.service;

import com.vena.codesage.dto.BlastRadiusDto;
import com.vena.codesage.dto.CallPathDto;
import com.vena.codesage.dto.ExecutionGraphDto;
import com.vena.codesage.dto.ExecutionPathDto;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.repo.CallEdgeRepository;
import com.vena.codesage.graph.repo.EndpointMappingRepository;
import com.vena.codesage.graph.repo.ScanRunRepository;
import com.vena.codesage.graph.repo.TouchpointRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CallGraphService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final ScanRunRepository scanRunRepository;
    private final CallEdgeRepository callEdgeRepository;
    private final EndpointMappingRepository endpointMappingRepository;
    private final TouchpointRepository touchpointRepository;

    public CallGraphService(ScanRunRepository scanRunRepository,
                            CallEdgeRepository callEdgeRepository,
                            EndpointMappingRepository endpointMappingRepository,
                            TouchpointRepository touchpointRepository) {
        this.scanRunRepository = scanRunRepository;
        this.callEdgeRepository = callEdgeRepository;
        this.endpointMappingRepository = endpointMappingRepository;
        this.touchpointRepository = touchpointRepository;
    }

    public List<String> findDirectCallers(String projectKey,
                                          String methodQualifiedName,
                                          boolean excludeTests,
                                          boolean excludeAnonymous,
                                          Integer limit) {
        Long scanRunId = getActiveScanRunId(projectKey);

        return callEdgeRepository.findByScanRunIdAndCalleeQualifiedName(scanRunId, methodQualifiedName)
                .stream()
                .map(e -> e.getCallerQualifiedName())
                .distinct()
                .filter(name -> matchesFilters(name, excludeTests, excludeAnonymous))
                .limit(normalizeLimit(limit))
                .toList();
    }

    public List<String> findDirectCallees(String projectKey,
                                          String methodQualifiedName,
                                          boolean excludeTests,
                                          boolean excludeAnonymous,
                                          Integer limit) {
        Long scanRunId = getActiveScanRunId(projectKey);

        return callEdgeRepository.findByScanRunIdAndCallerQualifiedName(scanRunId, methodQualifiedName)
                .stream()
                .map(e -> e.getCalleeQualifiedName())
                .distinct()
                .filter(name -> matchesFilters(name, excludeTests, excludeAnonymous))
                .limit(normalizeLimit(limit))
                .toList();
    }

    public List<String> findEndpointsReachingMethod(String projectKey,
                                                    String methodQualifiedName,
                                                    boolean excludeTests,
                                                    boolean excludeAnonymous,
                                                    Integer limit) {
        Long scanRunId = getActiveScanRunId(projectKey);
        int effectiveLimit = normalizeLimit(limit);

        Set<String> visited = new LinkedHashSet<>();
        Set<String> endpoints = new LinkedHashSet<>();

        Queue<String> queue = new ArrayDeque<>();
        queue.add(methodQualifiedName);

        while (!queue.isEmpty() && endpoints.size() < effectiveLimit) {
            String current = queue.poll();

            List<String> directCallers = callEdgeRepository
                    .findByScanRunIdAndCalleeQualifiedName(scanRunId, current)
                    .stream()
                    .map(e -> e.getCallerQualifiedName())
                    .distinct()
                    .toList();

            for (String caller : directCallers) {
                if (!matchesFilters(caller, excludeTests, excludeAnonymous)) {
                    continue;
                }

                if (!visited.add(caller)) {
                    continue;
                }

                endpointMappingRepository.findByScanRunIdAndMethodQualifiedName(scanRunId, caller)
                        .stream()
                        .map(this::formatEndpoint)
                        .forEach(endpoints::add);

                queue.add(caller);

                if (endpoints.size() >= effectiveLimit) {
                    break;
                }
            }
        }

        return endpoints.stream().limit(effectiveLimit).toList();
    }

    public BlastRadiusDto computeBlastRadius(String projectKey,
                                             String targetMethod,
                                             int depth,
                                             boolean excludeTests,
                                             boolean excludeAnonymous,
                                             Integer limit) {
        Long scanRunId = getActiveScanRunId(projectKey);
        int effectiveLimit = normalizeLimit(limit);

        Set<String> visitedDown = new LinkedHashSet<>();
        Set<String> visitedUp = new LinkedHashSet<>();

        bfsDown(scanRunId, targetMethod, depth, visitedDown, excludeTests, excludeAnonymous, effectiveLimit);
        bfsUp(scanRunId, targetMethod, depth, visitedUp, excludeTests, excludeAnonymous, effectiveLimit);

        List<String> callees = new ArrayList<>(visitedDown).stream().limit(effectiveLimit).toList();
        List<String> callers = new ArrayList<>(visitedUp).stream().limit(effectiveLimit).toList();

        List<String> impactedEndpoints = visitedUp.stream()
                .flatMap(q -> endpointMappingRepository.findByScanRunIdAndMethodQualifiedName(scanRunId, q).stream())
                .map(this::formatEndpoint)
                .distinct()
                .limit(effectiveLimit)
                .toList();

        List<String> directTouchpoints = touchpointRepository
                .findByScanRunIdAndCallerQualifiedName(scanRunId, targetMethod)
                .stream()
                .map(tp -> tp.getCategory() + " -> " + tp.getTargetQualifiedName())
                .distinct()
                .limit(effectiveLimit)
                .toList();

        List<String> reachableTouchpoints = findReachableTouchpoints(
                scanRunId,
                visitedDown,
                effectiveLimit
        );

        List<String> externalLikeCallees = callees.stream()
                .filter(this::isExternalLike)
                .limit(effectiveLimit)
                .toList();

        List<String> persistenceLikeCallees = callees.stream()
                .filter(this::isPersistenceLike)
                .limit(effectiveLimit)
                .toList();

        return new BlastRadiusDto(
                targetMethod,
                depth,
                callers,
                callees,
                impactedEndpoints,
                directTouchpoints,
                reachableTouchpoints,
                externalLikeCallees,
                persistenceLikeCallees
        );
    }

    private void bfsDown(Long scanRunId,
                         String start,
                         int depth,
                         Set<String> visited,
                         boolean excludeTests,
                         boolean excludeAnonymous,
                         int limit) {
        Queue<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
        queue.add(Map.entry(start, 0));

        while (!queue.isEmpty() && visited.size() < limit) {
            Map.Entry<String, Integer> current = queue.poll();
            if (current.getValue() >= depth) {
                continue;
            }

            List<String> directCallees = callEdgeRepository
                    .findByScanRunIdAndCallerQualifiedName(scanRunId, current.getKey())
                    .stream()
                    .map(e -> e.getCalleeQualifiedName())
                    .distinct()
                    .filter(name -> matchesFilters(name, excludeTests, excludeAnonymous))
                    .toList();

            for (String callee : directCallees) {
                if (visited.size() >= limit) {
                    break;
                }
                if (visited.add(callee)) {
                    queue.add(Map.entry(callee, current.getValue() + 1));
                }
            }
        }
    }

    private void bfsUp(Long scanRunId,
                       String start,
                       int depth,
                       Set<String> visited,
                       boolean excludeTests,
                       boolean excludeAnonymous,
                       int limit) {
        Queue<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
        queue.add(Map.entry(start, 0));

        while (!queue.isEmpty() && visited.size() < limit) {
            Map.Entry<String, Integer> current = queue.poll();
            if (current.getValue() >= depth) {
                continue;
            }

            List<String> directCallers = callEdgeRepository
                    .findByScanRunIdAndCalleeQualifiedName(scanRunId, current.getKey())
                    .stream()
                    .map(e -> e.getCallerQualifiedName())
                    .distinct()
                    .filter(name -> matchesFilters(name, excludeTests, excludeAnonymous))
                    .toList();

            for (String caller : directCallers) {
                if (visited.size() >= limit) {
                    break;
                }
                if (visited.add(caller)) {
                    queue.add(Map.entry(caller, current.getValue() + 1));
                }
            }
        }
    }

    private boolean matchesFilters(String qualifiedName,
                                   boolean excludeTests,
                                   boolean excludeAnonymous) {
        if (excludeTests && isTestLike(qualifiedName)) {
            return false;
        }
        if (excludeAnonymous && isAnonymousLike(qualifiedName)) {
            return false;
        }
        return true;
    }

    private boolean isTestLike(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith("test")
                || lower.contains(".test.")
                || lower.contains("test.");
    }

    private boolean isAnonymousLike(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("<anonymous")
                || lower.contains("lambda$")
                || lower.contains("$$lambda$");
    }

    private boolean isExternalLike(String value) {
        if (value == null) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);

        return lower.contains("clientservice")
                || lower.contains("client")
                || lower.contains("resttemplate")
                || lower.contains("webclient")
                || lower.contains("feign")
                || lower.contains("httpclient")
                || lower.contains("auth0")
                || lower.contains("external")
                || lower.contains("integration")
                || lower.contains("connector");
    }

    private boolean isPersistenceLike(String value) {
        if (value == null) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);

        return lower.contains("repository")
                || lower.contains("dao")
                || lower.contains("entitymanager")
                || lower.contains("jdbctemplate")
                || lower.contains("crudrepository")
                || lower.contains("pagingandsortingrepository")
                || lower.endsWith(".save")
                || lower.endsWith(".update")
                || lower.endsWith(".delete")
                || lower.endsWith(".insert")
                || lower.endsWith(".persist")
                || lower.endsWith(".merge");
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Long getActiveScanRunId(String projectKey) {
        ScanRun scanRun = scanRunRepository
                .findFirstByProjectKeyAndIsActiveTrueOrderByStartedAtDesc(projectKey)
                .orElseThrow(() -> new IllegalStateException("No active scan found for project: " + projectKey));
        return scanRun.getId();
    }

    private String formatEndpoint(com.vena.codesage.graph.model.EndpointMapping endpoint) {
        String httpMethod = endpoint.getHttpMethod() == null ? "" : endpoint.getHttpMethod();
        String classPath = endpoint.getClassPath() == null ? "" : endpoint.getClassPath();
        String methodPath = endpoint.getMethodPath() == null ? "" : endpoint.getMethodPath();
        String qualifiedMethod = endpoint.getMethodQualifiedName() == null ? "" : endpoint.getMethodQualifiedName();

        return (httpMethod + " " + classPath + methodPath).trim() + " -> " + qualifiedMethod;
    }

    private List<String> findReachableTouchpoints(Long scanRunId,
                                                  Set<String> reachableMethods,
                                                  int limit) {
        List<String> allTouchpoints = touchpointRepository.findAll().stream()
                .filter(tp -> tp.getScanRunId().equals(scanRunId))
                .map(tp -> tp.getCategory() + "||" + safe(tp.getCallerQualifiedName()) + "||" + safe(tp.getTargetQualifiedName()))
                .toList();

        Set<String> matches = new LinkedHashSet<>();

        for (String method : reachableMethods) {
            String normalizedMethod = normalizeMethodKey(method);

            for (String row : allTouchpoints) {
                String[] parts = row.split("\\|\\|", -1);
                String category = parts[0];
                String caller = parts[1];
                String target = parts[2];

                if (methodMatchesTouchpointCaller(method, normalizedMethod, caller)) {
                    matches.add(category + " -> " + target + " [" + caller + "]");
                    if (matches.size() >= limit) {
                        return matches.stream().toList();
                    }
                }
            }
        }

        return matches.stream().toList();
    }

    private boolean methodMatchesTouchpointCaller(String method,
                                                  String normalizedMethod,
                                                  String touchpointCaller) {
        if (touchpointCaller == null || touchpointCaller.isBlank()) {
            return false;
        }

        if (method.equals(touchpointCaller)) {
            return true;
        }

        String normalizedTouchpoint = normalizeMethodKey(touchpointCaller);
        if (normalizedMethod.equals(normalizedTouchpoint)) {
            return true;
        }

        String methodSimple = extractMethodName(method);
        String tpSimple = extractMethodName(touchpointCaller);

        String methodClass = extractDeclaringType(method);
        String tpClass = extractDeclaringType(touchpointCaller);

        return !methodSimple.isBlank()
                && methodSimple.equals(tpSimple)
                && !methodClass.isBlank()
                && methodClass.equals(tpClass);
    }

    public List<CallPathDto> findPathsToTouchpoints(String projectKey,
                                                    String methodQualifiedName,
                                                    int maxDepth,
                                                    boolean excludeTests,
                                                    boolean excludeAnonymous,
                                                    Integer limit) {
        Long scanRunId = getActiveScanRunId(projectKey);
        int effectiveLimit = normalizeLimit(limit);

        List<com.vena.codesage.graph.model.Touchpoint> touchpoints = touchpointRepository.findByScanRunId(scanRunId);
        if (touchpoints.isEmpty()) {
            return List.of();
        }

        List<String> targets = touchpoints.stream()
                .map(tp -> tp.getCallerQualifiedName())
                .filter(q -> q != null && !q.isBlank())
                .distinct()
                .toList();

        List<CallPathDto> results = new ArrayList<>();

        for (String target : targets) {
            List<String> path = findShortestPath(
                    scanRunId,
                    methodQualifiedName,
                    target,
                    maxDepth,
                    excludeTests,
                    excludeAnonymous
            );

            if (!path.isEmpty()) {
                String category = touchpoints.stream()
                        .filter(tp -> target.equals(tp.getCallerQualifiedName()))
                        .map(tp -> tp.getCategory())
                        .distinct()
                        .findFirst()
                        .orElse("UNKNOWN");

                results.add(new CallPathDto(
                        methodQualifiedName,
                        target,
                        path.size() - 1,
                        path,
                        category
                ));

                if (results.size() >= effectiveLimit) {
                    break;
                }
            }
        }

        return results;
    }

    private List<String> findShortestPath(Long scanRunId,
                                          String start,
                                          String target,
                                          int maxDepth,
                                          boolean excludeTests,
                                          boolean excludeAnonymous) {
        if (start.equals(target)) {
            return List.of(start);
        }

        Queue<List<String>> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();

        queue.add(List.of(start));
        visited.add(start);

        while (!queue.isEmpty()) {
            List<String> currentPath = queue.poll();
            String current = currentPath.get(currentPath.size() - 1);

            if (currentPath.size() - 1 >= maxDepth) {
                continue;
            }

            List<String> nextNodes = callEdgeRepository
                    .findByScanRunIdAndCallerQualifiedName(scanRunId, current)
                    .stream()
                    .map(e -> e.getCalleeQualifiedName())
                    .distinct()
                    .filter(name -> matchesFilters(name, excludeTests, excludeAnonymous))
                    .toList();

            for (String next : nextNodes) {
                if (!visited.add(next)) {
                    continue;
                }

                List<String> nextPath = new ArrayList<>(currentPath);
                nextPath.add(next);

                if (next.equals(target)) {
                    return nextPath;
                }

                queue.add(nextPath);
            }
        }

        return List.of();
    }

    public ExecutionGraphDto computeExecutionPaths(
            String projectKey,
            String rootMethod,
            int maxDepth,
            boolean excludeTests,
            boolean excludeAnonymous
    ) {
        if (rootMethod == null || rootMethod.isBlank()) {
            return new ExecutionGraphDto(rootMethod, List.of(), List.of());
        }

        int effectiveDepth = Math.max(1, maxDepth);

        List<ExecutionPathDto> resultPaths = new ArrayList<>();
        Deque<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(rootMethod));

        // IMPORTANT: track every explored node, not just terminal-path nodes
        Set<String> exploredNodes = new LinkedHashSet<>();
        exploredNodes.add(rootMethod);

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);
            int depth = path.size() - 1;

            if (depth > effectiveDepth) {
                continue;
            }

            String category = classifyTerminal(current);
            if (category != null && isTrueTerminal(current)) {
                resultPaths.add(new ExecutionPathDto(
                        category,
                        depth,
                        List.copyOf(path)
                ));
            }

            if (depth == effectiveDepth) {
                continue;
            }

            if (isInfrastructureNoise(current)) {
                continue;
            }

            List<String> callees = findDirectCallees(
                    projectKey,
                    current,
                    excludeTests,
                    excludeAnonymous,
                    null
            );

            for (String callee : callees) {
                if (callee == null || callee.isBlank()) {
                    continue;
                }

                // path-level cycle prevention only
                if (path.contains(callee)) {
                    continue;
                }

                exploredNodes.add(callee);

                List<String> nextPath = new ArrayList<>(path.size() + 1);
                nextPath.addAll(path);
                nextPath.add(callee);
                queue.add(nextPath);
            }
        }

        List<ExecutionPathDto> dedup = deduplicatePaths(resultPaths);

        // AUGMENT using all explored nodes, not just terminal-path nodes
        List<ExecutionPathDto> augmented = new ArrayList<>(dedup);

        List<String> reachableTouchpoints = findReachableTouchpoints(
                getActiveScanRunId(projectKey),
                exploredNodes,
                200
        );

        for (String tp : reachableTouchpoints) {
            // expected format: "CATEGORY -> target [caller]"
            int arrowIdx = tp.indexOf(" -> ");
            int openIdx = tp.lastIndexOf('[');
            int closeIdx = tp.lastIndexOf(']');

            if (arrowIdx < 0 || openIdx < 0 || closeIdx <= openIdx) {
                continue;
            }

            String category = tp.substring(0, arrowIdx).trim();
            String target = tp.substring(arrowIdx + 4, openIdx).trim();
            String caller = tp.substring(openIdx + 1, closeIdx).trim();

            // find the shortest explored prefix path that reaches caller
            List<String> prefix = findShortestPath(
                    getActiveScanRunId(projectKey),
                    rootMethod,
                    caller,
                    effectiveDepth,
                    excludeTests,
                    excludeAnonymous
            );

            if (prefix.isEmpty()) {
                continue;
            }

            List<String> newPath = new ArrayList<>(prefix.size() + 1);
            newPath.addAll(prefix);
            newPath.add(target);

            augmented.add(new ExecutionPathDto(
                    category,
                    newPath.size() - 1,
                    List.copyOf(newPath)
            ));
        }

        List<ExecutionPathDto> finalPaths = deduplicatePaths(augmented);

        Comparator<ExecutionPathDto> comparator = Comparator
                .comparingInt(ExecutionPathDto::depth)
                .thenComparing(ExecutionPathDto::category, Comparator.nullsLast(String::compareTo))
                .thenComparing(p -> String.join("->", p.nodes()));

        finalPaths.sort(comparator);

        List<ExecutionPathDto> collapsed = collapseCommonPrefixes(finalPaths);
        collapsed.sort(comparator);

        return new ExecutionGraphDto(rootMethod, finalPaths, collapsed);
    }

    private boolean isTrueTerminal(String method) {
        String lower = method.toLowerCase(Locale.ROOT);

        return lower.contains("dao")
                || lower.contains("repository")
                || lower.endsWith(".save")
                || lower.endsWith(".update")
                || lower.endsWith(".insert")
                || lower.endsWith(".delete")
                || lower.contains("client")
                || lower.contains("external");
    }

    private String normalizeMethodKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private String extractMethodName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int idx = value.lastIndexOf('.');
        return idx >= 0 ? value.substring(idx + 1).toLowerCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT);
    }

    private String extractDeclaringType(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int idx = value.lastIndexOf('.');
        return idx >= 0 ? value.substring(0, idx).toLowerCase(Locale.ROOT) : "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String classifyTerminal(String method) {
        String lower = method.toLowerCase(Locale.ROOT);

        // Persistence
        if (lower.contains("repository")
                || lower.contains("dao")
                || lower.contains("entitymanager")
                || lower.endsWith(".save")
                || lower.endsWith(".update")
                || lower.endsWith(".insert")
                || lower.endsWith(".delete")
                || lower.endsWith(".persist")
                || lower.endsWith(".merge")) {
            return "PERSISTENCE";
        }

        // External systems
        if (lower.contains("client")
                || lower.contains("resttemplate")
                || lower.contains("webclient")
                || lower.contains("feign")
                || lower.contains("httpclient")
                || lower.contains("external")
                || lower.contains("integration")) {
            return "EXTERNAL";
        }

        // Cache
        if (lower.contains("cache")
                || lower.contains("redis")
                || lower.contains("memcached")) {
            return "CACHE";
        }

        // Messaging
        if (lower.contains("kafka")
                || lower.contains("producer")
                || lower.contains("consumer")
                || lower.contains("queue")
                || lower.contains("publisher")) {
            return "MESSAGING";
        }

        return null; // not terminal
    }

    private boolean isInfrastructureNoise(String method) {
        String lower = method.toLowerCase(Locale.ROOT);

        return lower.contains("hibernate")
                || lower.contains("entitymanagerfactory")
                || lower.contains("metamodel")
                || lower.contains("proxy")
                || lower.contains("reflection");
    }

    private List<ExecutionPathDto> deduplicatePaths(List<ExecutionPathDto> paths) {
        Map<String, ExecutionPathDto> unique = new LinkedHashMap<>();

        for (ExecutionPathDto path : paths) {
            String key = String.join("->", path.nodes());
            unique.putIfAbsent(key, path);
        }

        return new ArrayList<>(unique.values());
    }

    private List<ExecutionPathDto> collapseCommonPrefixes(List<ExecutionPathDto> paths) {
        Map<String, List<ExecutionPathDto>> grouped = new LinkedHashMap<>();

        for (ExecutionPathDto path : paths) {
            if (path.nodes().size() < 2) continue;

            String prefix = String.join("->",
                    path.nodes().subList(0, Math.min(3, path.nodes().size()))
            );

            grouped.computeIfAbsent(prefix, k -> new ArrayList<>()).add(path);
        }

        List<ExecutionPathDto> result = new ArrayList<>();

        for (List<ExecutionPathDto> group : grouped.values()) {
            result.add(group.get(0)); // pick representative
        }

        return result;
    }

}
