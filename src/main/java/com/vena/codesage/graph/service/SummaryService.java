package com.vena.codesage.graph.service;

import com.vena.codesage.graph.model.CallEdge;
import com.vena.codesage.graph.model.CodeEntity;
import com.vena.codesage.graph.model.EndpointMapping;
import com.vena.codesage.graph.model.FlowEdge;
import com.vena.codesage.graph.model.Touchpoint;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SummaryService {

    private static final int LIST_LIMIT = 5;

    public String buildSeedSummary(CodeEntity entity, SummaryIndex index) {
        String qualifiedName = entity.getQualifiedName();

        List<CallEdge> incomingCalls = index.incomingCallsByCallee().getOrDefault(qualifiedName, List.of());
        List<CallEdge> outgoingCalls = index.outgoingCallsByCaller().getOrDefault(qualifiedName, List.of());
        List<Touchpoint> touchpoints = index.touchpointsByCaller().getOrDefault(qualifiedName, List.of());
        List<FlowEdge> outgoingFlows = index.outgoingFlowsBySource().getOrDefault(qualifiedName, List.of());
        List<FlowEdge> incomingFlows = index.incomingFlowsBySink().getOrDefault(qualifiedName, List.of());

        List<EndpointMapping> endpoints = resolveEndpoints(entity, index);

        StringBuilder summary = new StringBuilder();

        appendSentence(summary, describeEntity(entity));
        appendSentence(summary, describeLocation(entity));
        appendSentence(summary, describeCallers(incomingCalls));
        appendSentence(summary, describeCallees(outgoingCalls));
        appendSentence(summary, describeEndpoints(endpoints));
        appendSentence(summary, describeTouchpoints(touchpoints));
        appendSentence(summary, describeOutgoingFlows(outgoingFlows));
        appendSentence(summary, describeIncomingFlows(incomingFlows));

        String result = summary.toString().trim();
        if (result.isBlank()) {
            return fallbackSummary(entity);
        }

        return result;
    }

    private List<EndpointMapping> resolveEndpoints(CodeEntity entity, SummaryIndex index) {
        if (isMethodLike(entity)) {
            return index.endpointsByMethod().getOrDefault(entity.getQualifiedName(), List.of());
        }

        if (isTypeLike(entity)) {
            String prefix = entity.getQualifiedName() + ".";
            return index.endpointsByMethod().entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .flatMap(entry -> entry.getValue().stream())
                    .toList();
        }

        return List.of();
    }

    private String describeEntity(CodeEntity entity) {
        String entityName = firstNonBlank(entity.getQualifiedName(), entity.getSimpleName(), "unknown");
        String entityType = normalizeEntityType(entity.getEntityType());

        StringBuilder line = new StringBuilder();
        line.append("Entity ").append(entityName).append(" is a ").append(entityType);

        if (hasText(entity.getDeclaringType())
                && !entityName.equals(entity.getDeclaringType())
                && !entityName.startsWith(entity.getDeclaringType() + ".")) {
            line.append(" declared in ").append(entity.getDeclaringType());
        }

        if (hasText(entity.getPackageName())) {
            line.append(" in package ").append(entity.getPackageName());
        }

        if (hasText(entity.getSignature())) {
            line.append(" with signature ").append(entity.getSignature());
        }

        return line.toString();
    }

    private String describeLocation(CodeEntity entity) {
        if (!hasText(entity.getFilePath())) {
            return null;
        }
        return "Source path: " + entity.getFilePath();
    }

    private String describeCallers(List<CallEdge> incomingCalls) {
        Set<String> callers = incomingCalls.stream()
                .map(CallEdge::getCallerQualifiedName)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (callers.isEmpty()) {
            return "No incoming callers were recorded in the current scan";
        }

        return "Direct callers include " + joinLimited(callers, LIST_LIMIT);
    }

    private String describeCallees(List<CallEdge> outgoingCalls) {
        Set<String> callees = outgoingCalls.stream()
                .map(CallEdge::getCalleeQualifiedName)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (callees.isEmpty()) {
            return "No direct outgoing callees were recorded in the current scan";
        }

        return "Direct callees include " + joinLimited(callees, LIST_LIMIT);
    }

    private String describeEndpoints(List<EndpointMapping> endpoints) {
        if (endpoints.isEmpty()) {
            return null;
        }

        Set<String> endpointDescriptions = endpoints.stream()
                .map(this::formatEndpoint)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (endpointDescriptions.isEmpty()) {
            return null;
        }

        return "Associated HTTP endpoints include " + joinLimited(endpointDescriptions, LIST_LIMIT);
    }

    private String describeTouchpoints(List<Touchpoint> touchpoints) {
        if (touchpoints.isEmpty()) {
            return null;
        }

        Set<String> touchpointDescriptions = touchpoints.stream()
                .map(tp -> {
                    String category = hasText(tp.getCategory()) ? tp.getCategory().toLowerCase(Locale.ROOT) : "unknown";
                    String target = firstNonBlank(tp.getTargetQualifiedName(), "unknown target");
                    return category + " -> " + target;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return "External or framework touchpoints include " + joinLimited(touchpointDescriptions, LIST_LIMIT);
    }

    private String describeOutgoingFlows(List<FlowEdge> outgoingFlows) {
        if (outgoingFlows.isEmpty()) {
            return null;
        }

        Set<String> sinks = outgoingFlows.stream()
                .map(flow -> {
                    String sink = firstNonBlank(flow.getSinkMethodQualifiedName(), "unknown sink");
                    if (hasText(flow.getParameterName())) {
                        return sink + " via parameter " + flow.getParameterName();
                    }
                    return sink;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return "Data or persistence flows from this entity reach " + joinLimited(sinks, LIST_LIMIT);
    }

    private String describeIncomingFlows(List<FlowEdge> incomingFlows) {
        if (incomingFlows.isEmpty()) {
            return null;
        }

        Set<String> sources = incomingFlows.stream()
                .map(flow -> {
                    String source = firstNonBlank(flow.getSourceMethodQualifiedName(), "unknown source");
                    if (hasText(flow.getParameterName())) {
                        return source + " via parameter " + flow.getParameterName();
                    }
                    return source;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return "Data or persistence flows into this entity from " + joinLimited(sources, LIST_LIMIT);
    }

    private String formatEndpoint(EndpointMapping endpoint) {
        String method = firstNonBlank(endpoint.getHttpMethod(), "HTTP");
        String path = buildPath(endpoint.getClassPath(), endpoint.getMethodPath());
        if (!hasText(path)) {
            path = "unresolved path";
        }
        return method + " " + path;
    }

    private String buildPath(String classPath, String methodPath) {
        String left = trimSlashes(classPath);
        String right = trimSlashes(methodPath);

        if (!hasText(left) && !hasText(right)) {
            return "";
        }
        if (!hasText(left)) {
            return "/" + right;
        }
        if (!hasText(right)) {
            return "/" + left;
        }
        return "/" + left + "/" + right;
    }

    private String trimSlashes(String value) {
        if (!hasText(value)) {
            return "";
        }
        String result = value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean isMethodLike(CodeEntity entity) {
        return hasText(entity.getEntityType())
                && entity.getEntityType().toUpperCase(Locale.ROOT).contains("METHOD");
    }

    private boolean isTypeLike(CodeEntity entity) {
        if (!hasText(entity.getEntityType())) {
            return false;
        }

        String normalized = entity.getEntityType().toUpperCase(Locale.ROOT);
        return normalized.contains("CLASS")
                || normalized.contains("INTERFACE")
                || normalized.contains("ENUM")
                || normalized.contains("TYPE")
                || normalized.contains("CONTROLLER")
                || normalized.contains("SERVICE")
                || normalized.contains("REPOSITORY");
    }

    private String normalizeEntityType(String entityType) {
        if (!hasText(entityType)) {
            return "code entity";
        }
        return entityType.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String joinLimited(Set<String> values, int limit) {
        List<String> items = values.stream()
                .filter(this::hasText)
                .limit(limit)
                .toList();

        if (items.isEmpty()) {
            return "";
        }

        String joined = String.join(", ", items);
        if (values.size() > limit) {
            return joined + ", and " + (values.size() - limit) + " more";
        }
        return joined;
    }

    private void appendSentence(StringBuilder summary, String sentence) {
        if (!hasText(sentence)) {
            return;
        }

        String normalized = sentence.trim();
        if (!normalized.endsWith(".")) {
            normalized = normalized + ".";
        }

        if (summary.length() > 0) {
            summary.append(' ');
        }
        summary.append(normalized);
    }

    private String fallbackSummary(CodeEntity entity) {
        return "Entity " + firstNonBlank(entity.getQualifiedName(), entity.getSimpleName(), "unknown")
                + " is a " + normalizeEntityType(entity.getEntityType())
                + (hasText(entity.getFilePath()) ? " defined at " + entity.getFilePath() : "")
                + ".";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record SummaryIndex(
            Map<String, List<CallEdge>> incomingCallsByCallee,
            Map<String, List<CallEdge>> outgoingCallsByCaller,
            Map<String, List<EndpointMapping>> endpointsByMethod,
            Map<String, List<Touchpoint>> touchpointsByCaller,
            Map<String, List<FlowEdge>> outgoingFlowsBySource,
            Map<String, List<FlowEdge>> incomingFlowsBySink
    ) {
    }
}