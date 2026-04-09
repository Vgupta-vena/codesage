package com.vena.codesage.graph.service;

import com.vena.codesage.graph.model.CallEdge;
import com.vena.codesage.graph.model.CodeEntity;
import com.vena.codesage.graph.model.EndpointMapping;
import com.vena.codesage.graph.model.SemanticDocument;
import com.vena.codesage.graph.model.Touchpoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class SemanticBuildSupport {

    private static final int DEFAULT_SECTION_LIMIT = 20;
    private static final int TRANSITIVE_ENDPOINT_DEPTH = 3;
    private static final String GRAPH_SUMMARY_DOC_TYPE = "GRAPH_SUMMARY";

    GraphSnapshot buildSnapshot(List<CodeEntity> entities,
                                List<CallEdge> callEdges,
                                List<EndpointMapping> endpoints,
                                List<Touchpoint> touchpoints) {

        Map<String, LinkedHashSet<String>> callsByCaller = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> calledByByCallee = new LinkedHashMap<>();

        for (CallEdge edge : callEdges) {
            if (edge.getCallerQualifiedName() != null
                    && edge.getCalleeQualifiedName() != null
                    && isUsableQualifiedName(edge.getCallerQualifiedName(), null)
                    && isUsableQualifiedName(edge.getCalleeQualifiedName(), null)) {

                callsByCaller
                        .computeIfAbsent(edge.getCallerQualifiedName(), ignored -> new LinkedHashSet<>())
                        .add(edge.getCalleeQualifiedName());

                calledByByCallee
                        .computeIfAbsent(edge.getCalleeQualifiedName(), ignored -> new LinkedHashSet<>())
                        .add(edge.getCallerQualifiedName());
            }
        }

        Map<String, LinkedHashSet<String>> endpointsByMethod = new LinkedHashMap<>();
        for (EndpointMapping endpoint : endpoints) {
            if (endpoint.getMethodQualifiedName() != null
                    && isUsableQualifiedName(endpoint.getMethodQualifiedName(), endpoint.getFilePath())) {
                endpointsByMethod
                        .computeIfAbsent(endpoint.getMethodQualifiedName(), ignored -> new LinkedHashSet<>())
                        .add(formatEndpoint(endpoint));
            }
        }

        Map<String, LinkedHashSet<String>> touchpointsByCaller = new LinkedHashMap<>();
        for (Touchpoint touchpoint : touchpoints) {
            if (touchpoint.getCallerQualifiedName() != null
                    && isUsableQualifiedName(touchpoint.getCallerQualifiedName(), touchpoint.getFilePath())) {
                touchpointsByCaller
                        .computeIfAbsent(touchpoint.getCallerQualifiedName(), ignored -> new LinkedHashSet<>())
                        .add(touchpoint.getCategory() + " -> " + safe(touchpoint.getTargetQualifiedName()));
            }
        }

        Map<String, LinkedHashSet<String>> methodsByDeclaringType = new LinkedHashMap<>();
        for (CodeEntity entity : entities) {
            if (entity.getDeclaringType() != null
                    && !entity.getDeclaringType().isBlank()
                    && entity.getQualifiedName() != null
                    && !entity.getQualifiedName().isBlank()
                    && "METHOD".equalsIgnoreCase(entity.getEntityType())
                    && isUsableQualifiedName(entity.getQualifiedName(), entity.getFilePath())) {
                methodsByDeclaringType
                        .computeIfAbsent(entity.getDeclaringType(), ignored -> new LinkedHashSet<>())
                        .add(entity.getQualifiedName());
            }
        }

        return new GraphSnapshot(
                callsByCaller,
                calledByByCallee,
                endpointsByMethod,
                touchpointsByCaller,
                methodsByDeclaringType
        );
    }

    Optional<SemanticDocument> buildDocument(Long scanRunId, CodeEntity entity, GraphSnapshot snapshot) {
        if (!isUsableQualifiedName(entity.getQualifiedName(), entity.getFilePath())) {
            return Optional.empty();
        }

        String qualifiedName = entity.getQualifiedName();
        List<String> methods = limitedList(snapshot.methodsByDeclaringType().get(qualifiedName));

        SemanticSections sections = "METHOD".equalsIgnoreCase(entity.getEntityType())
                ? resolveMethodSections(qualifiedName, snapshot)
                : resolveTypeSections(methods, snapshot);

        if (methods.isEmpty() && sections.isEmpty()) {
            return Optional.empty();
        }

        List<String> externalLikeCallees = sections.calls().stream()
                .filter(this::isExternalLike)
                .limit(DEFAULT_SECTION_LIMIT)
                .toList();

        List<String> persistenceLikeCallees = sections.calls().stream()
                .filter(this::isPersistenceLike)
                .limit(DEFAULT_SECTION_LIMIT)
                .toList();

        String content = buildContent(
                entity,
                methods,
                sections.calls(),
                sections.calledBy(),
                sections.endpointsReaching(),
                sections.directTouchpoints(),
                externalLikeCallees,
                persistenceLikeCallees
        );

        String docKey = sha256(
                "SEMANTIC|"
                        + scanRunId + "|"
                        + safe(entity.getEntityType()) + "|"
                        + safe(entity.getQualifiedName()) + "|"
                        + safe(entity.getSignature()) + "|"
                        + safe(entity.getFilePath())
        );

        return Optional.of(SemanticDocument.builder()
                .scanRunId(scanRunId)
                .docKey(docKey)
                .entityQualifiedName(entity.getQualifiedName())
                .entityType(entity.getEntityType())
                .docType(GRAPH_SUMMARY_DOC_TYPE)
                .filePath(entity.getFilePath())
                .content(content)
                .contentHash(sha256(content))
                .isActive(true)
                .build());
    }

    private SemanticSections resolveMethodSections(String qualifiedName, GraphSnapshot snapshot) {
        List<String> calls = limitedList(snapshot.callsByCaller().get(qualifiedName));
        List<String> calledBy = limitedList(snapshot.calledByByCallee().get(qualifiedName));

        List<String> endpointsReaching = findTransitiveEndpoints(
                qualifiedName,
                snapshot.calledByByCallee(),
                snapshot.endpointsByMethod(),
                TRANSITIVE_ENDPOINT_DEPTH
        );
        if (endpointsReaching.isEmpty()) {
            endpointsReaching = limitedList(snapshot.endpointsByMethod().get(qualifiedName));
        }

        List<String> directTouchpoints = limitedList(snapshot.touchpointsByCaller().get(qualifiedName));
        return new SemanticSections(calls, calledBy, endpointsReaching, directTouchpoints);
    }

    private SemanticSections resolveTypeSections(List<String> methods, GraphSnapshot snapshot) {
        if (methods.isEmpty()) {
            return SemanticSections.empty();
        }
        return new SemanticSections(
                aggregateForMethods(methods, snapshot.callsByCaller()),
                aggregateForMethods(methods, snapshot.calledByByCallee()),
                aggregateForMethods(methods, snapshot.endpointsByMethod()),
                aggregateForMethods(methods, snapshot.touchpointsByCaller())
        );
    }

    private List<String> aggregateForMethods(List<String> methods,
                                             Map<String, LinkedHashSet<String>> source) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String method : methods) {
            LinkedHashSet<String> found = source.get(method);
            if (found != null) {
                values.addAll(found);
            }
            if (values.size() >= DEFAULT_SECTION_LIMIT) {
                break;
            }
        }
        return values.stream().limit(DEFAULT_SECTION_LIMIT).toList();
    }

    private String buildContent(CodeEntity entity,
                                List<String> methods,
                                List<String> calls,
                                List<String> calledBy,
                                List<String> endpointsReaching,
                                List<String> directTouchpoints,
                                List<String> externalLikeCallees,
                                List<String> persistenceLikeCallees) {
        StringBuilder sb = new StringBuilder();

        line(sb, "Qualified Name: " + safe(entity.getQualifiedName()));
        line(sb, "Entity Type: " + safe(entity.getEntityType()));
        line(sb, "Declaring Type: " + safe(entity.getDeclaringType()));
        line(sb, "Package: " + safe(entity.getPackageName()));
        line(sb, "Signature: " + safe(entity.getSignature()));
        line(sb, "File Path: " + safe(entity.getFilePath()));
        blank(sb);

        appendSection(sb, "Methods", methods);
        appendSection(sb, "Called By", calledBy);
        appendSection(sb, "Calls", calls);
        appendSection(sb, "Endpoints Reaching", endpointsReaching);
        appendSection(sb, "Direct Touchpoints", directTouchpoints);
        appendSection(sb, "External-Like Callees", externalLikeCallees);
        appendSection(sb, "Persistence-Like Callees", persistenceLikeCallees);

        line(sb, "Summary:");
        line(sb, buildNaturalSummary(
                entity,
                methods,
                calls,
                calledBy,
                endpointsReaching,
                directTouchpoints,
                externalLikeCallees,
                persistenceLikeCallees
        ));

        return sb.toString().trim();
    }

    private String buildNaturalSummary(CodeEntity entity,
                                       List<String> methods,
                                       List<String> calls,
                                       List<String> calledBy,
                                       List<String> endpointsReaching,
                                       List<String> directTouchpoints,
                                       List<String> externalLikeCallees,
                                       List<String> persistenceLikeCallees) {
        StringBuilder summary = new StringBuilder();

        summary.append(safe(entity.getQualifiedName()));
        summary.append(" is a ");
        summary.append(lowerOrUnknown(entity.getEntityType()));

        if (entity.getDeclaringType() != null && !entity.getDeclaringType().isBlank()) {
            summary.append(" declared in ");
            summary.append(entity.getDeclaringType());
        }

        if (!methods.isEmpty()) {
            summary.append(". It includes methods such as ");
            summary.append(joinPreview(methods, 4));
        }

        if (!calledBy.isEmpty()) {
            summary.append(". It is called by ");
            summary.append(joinPreview(calledBy, 3));
        }

        if (!endpointsReaching.isEmpty()) {
            summary.append(". It is reachable from endpoints such as ");
            summary.append(joinPreview(endpointsReaching, 3));
        }

        if (!calls.isEmpty()) {
            summary.append(". It directly calls ");
            summary.append(joinPreview(calls, 4));
        }

        if (!directTouchpoints.isEmpty()) {
            summary.append(". Direct touchpoints include ");
            summary.append(joinPreview(directTouchpoints, 3));
        }

        if (!externalLikeCallees.isEmpty()) {
            summary.append(". Likely external integration paths include ");
            summary.append(joinPreview(externalLikeCallees, 3));
        }

        if (!persistenceLikeCallees.isEmpty()) {
            summary.append(". Likely persistence-related paths include ");
            summary.append(joinPreview(persistenceLikeCallees, 3));
        }

        return summary.toString();
    }

    private List<String> limitedList(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().limit(DEFAULT_SECTION_LIMIT).toList();
    }

    private List<String> findTransitiveEndpoints(String startMethod,
                                                 Map<String, LinkedHashSet<String>> calledByByCallee,
                                                 Map<String, LinkedHashSet<String>> endpointsByMethod,
                                                 int maxDepth) {
        if (startMethod == null) {
            return List.of();
        }

        LinkedHashSet<String> visited = new LinkedHashSet<>();
        LinkedHashSet<String> endpoints = new LinkedHashSet<>();
        List<String> frontier = List.of(startMethod);

        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            List<String> next = new ArrayList<>();

            for (String current : frontier) {
                LinkedHashSet<String> callers = calledByByCallee.get(current);
                if (callers == null) {
                    continue;
                }

                for (String caller : callers) {
                    if (!visited.add(caller)) {
                        continue;
                    }

                    LinkedHashSet<String> currentEndpoints = endpointsByMethod.get(caller);
                    if (currentEndpoints != null) {
                        endpoints.addAll(currentEndpoints);
                    }

                    next.add(caller);

                    if (endpoints.size() >= DEFAULT_SECTION_LIMIT) {
                        return endpoints.stream().limit(DEFAULT_SECTION_LIMIT).toList();
                    }
                }
            }

            frontier = next;
        }

        return endpoints.stream().limit(DEFAULT_SECTION_LIMIT).toList();
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
                || lower.contains("namedparameterjdbc")
                || lower.endsWith(".save")
                || lower.endsWith(".delete")
                || lower.endsWith(".insert")
                || lower.endsWith(".persist")
                || lower.endsWith(".merge")
                || lower.endsWith(".update")
                || lower.endsWith(".findbyid")
                || lower.contains("genericdao");
    }

    private boolean isUsableQualifiedName(String qualifiedName, String filePath) {
        return !isTestLike(qualifiedName, filePath) && !isAnonymousLike(qualifiedName);
    }

    private boolean isTestLike(String qualifiedName, String filePath) {
        return containsTestMarker(qualifiedName) || containsTestMarker(filePath);
    }

    private boolean containsTestMarker(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("/src/test/")
                || lower.contains("\\src\\test\\")
                || lower.endsWith("test")
                || lower.contains(".test.")
                || lower.contains("test.");
    }

    private boolean isAnonymousLike(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return false;
        }
        String lower = qualifiedName.toLowerCase(Locale.ROOT);
        return lower.contains("<anonymous")
                || lower.contains("lambda$")
                || lower.contains("$$lambda$");
    }

    private String formatEndpoint(EndpointMapping endpoint) {
        String httpMethod = endpoint.getHttpMethod() == null ? "" : endpoint.getHttpMethod();
        String classPath = endpoint.getClassPath() == null ? "" : endpoint.getClassPath();
        String methodPath = endpoint.getMethodPath() == null ? "" : endpoint.getMethodPath();
        String qualifiedMethod = endpoint.getMethodQualifiedName() == null ? "" : endpoint.getMethodQualifiedName();
        return (httpMethod + " " + classPath + methodPath).trim() + " -> " + qualifiedMethod;
    }

    private void appendSection(StringBuilder sb, String title, List<String> items) {
        line(sb, title + ":");
        if (items == null || items.isEmpty()) {
            line(sb, "- none");
        } else {
            for (String item : items) {
                line(sb, "- " + safe(item));
            }
        }
        blank(sb);
    }

    private String joinPreview(List<String> items, int limit) {
        return items.stream().limit(limit).reduce((left, right) -> left + ", " + right).orElse("none");
    }

    private String lowerOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown entity" : value.toLowerCase(Locale.ROOT);
    }

    private void line(StringBuilder sb, String line) {
        sb.append(line).append(System.lineSeparator());
    }

    private void blank(StringBuilder sb) {
        sb.append(System.lineSeparator());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash semantic content", e);
        }
    }

    record GraphSnapshot(
            Map<String, LinkedHashSet<String>> callsByCaller,
            Map<String, LinkedHashSet<String>> calledByByCallee,
            Map<String, LinkedHashSet<String>> endpointsByMethod,
            Map<String, LinkedHashSet<String>> touchpointsByCaller,
            Map<String, LinkedHashSet<String>> methodsByDeclaringType
    ) {
    }

    private record SemanticSections(
            List<String> calls,
            List<String> calledBy,
            List<String> endpointsReaching,
            List<String> directTouchpoints
    ) {
        private static SemanticSections empty() {
            return new SemanticSections(List.of(), List.of(), List.of(), List.of());
        }

        private boolean isEmpty() {
            return calls.isEmpty()
                    && calledBy.isEmpty()
                    && endpointsReaching.isEmpty()
                    && directTouchpoints.isEmpty();
        }
    }
}
