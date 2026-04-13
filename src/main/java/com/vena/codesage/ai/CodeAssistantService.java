package com.vena.codesage.ai;

import com.vena.codesage.ai.prompt.SystemPrompts;
import com.vena.codesage.dto.KnowledgeMode;
import com.vena.codesage.dto.KnowledgeResponseDto;
import com.vena.codesage.dto.TraceDirection;
import com.vena.codesage.dto.TraceResponseDto;
import com.vena.codesage.graph.service.CodebaseKnowledgeService;
import com.vena.codesage.graph.service.ReverseTraversalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodeAssistantService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeAssistantService.class);

    private final ChatClient chatClient;
    private final CodeIntelligenceTools tools;
    private final CodebaseKnowledgeService codebaseKnowledgeService;
    private final ReverseTraversalService reverseTraversalService;

    public CodeAssistantService(ChatClient chatClient,
                                CodeIntelligenceTools tools,
                                CodebaseKnowledgeService codebaseKnowledgeService,
                                ReverseTraversalService reverseTraversalService) {
        this.chatClient = chatClient;
        this.tools = tools;
        this.codebaseKnowledgeService = codebaseKnowledgeService;
        this.reverseTraversalService = reverseTraversalService;
    }

    private TraceResponseDto buildBestEffortTrace(String projectKey, KnowledgeResponseDto knowledge) {
        if (knowledge == null || knowledge.results().isEmpty()) {
            return null;
        }

        String seedQualifiedName = knowledge.results().getFirst().title();
        if (seedQualifiedName == null || seedQualifiedName.isBlank()) {
            return null;
        }

        try {
            return reverseTraversalService.trace(
                    projectKey,
                    seedQualifiedName,
                    TraceDirection.BOTH,
                    2,
                    15
            );
        } catch (Exception ex) {
            LOGGER.debug(
                    "Best-effort trace failed projectKey={} seed={} error={}",
                    projectKey,
                    seedQualifiedName,
                    ex.getMessage(),
                    ex
            );
            return null;
        }
    }

    private String buildFallbackAnswer(String question,
                                       KnowledgeResponseDto knowledge,
                                       TraceResponseDto trace,
                                       Exception error) {
        StringBuilder sb = new StringBuilder();

        sb.append("I could not reach the LLM backend right now, so here is a direct project knowledge response.\n\n");
        sb.append("Question: ").append(nullSafe(question)).append("\n");
        sb.append("Mode used: ").append(knowledge.mode()).append("\n");
        sb.append("Summary: ").append(nullSafe(knowledge.summary())).append("\n");
        sb.append("Result count: ").append(knowledge.resultCount()).append("\n");

        if (knowledge.sourcesUsed() != null && !knowledge.sourcesUsed().isEmpty()) {
            sb.append("Sources used: ").append(String.join(", ", knowledge.sourcesUsed())).append("\n");
        }

        if (knowledge.elapsedMs() != null) {
            sb.append("Knowledge retrieval time: ").append(knowledge.elapsedMs()).append(" ms\n");
        }

        sb.append("\n");

        if (!knowledge.results().isEmpty()) {
            sb.append("Top results:\n");
            for (var item : knowledge.results()) {
                sb.append("- [").append(item.sourceType()).append("/").append(item.resultType()).append("] ")
                        .append(nullSafe(item.title())).append("\n");

                if (hasText(item.subtitle())) {
                    sb.append("  ").append(item.subtitle()).append("\n");
                }

                if (hasText(item.location())) {
                    sb.append("  location: ").append(item.location()).append("\n");
                }

                if (hasText(item.snippet())) {
                    sb.append("  snippet: ").append(item.snippet()).append("\n");
                }

                if (item.highlights() != null && !item.highlights().isEmpty()) {
                    sb.append("  highlights: ").append(String.join(", ", item.highlights())).append("\n");
                }

                if (item.metadata() != null && !item.metadata().isEmpty()) {
                    sb.append("  metadata: ").append(item.metadata()).append("\n");
                }

                if (item.metadata() != null && !item.metadata().isEmpty()) {
                    Object classNames = item.metadata().get("classNames");
                    Object endpoints = item.metadata().get("endpoints");
                    Object url = item.metadata().get("url");

                    if (classNames instanceof java.util.List<?> list && !list.isEmpty()) {
                        sb.append("  referenced classes: ").append(list).append('\n');
                    }

                    if (endpoints instanceof java.util.List<?> list && !list.isEmpty()) {
                        sb.append("  referenced endpoints: ").append(list).append('\n');
                    }

                    if (url instanceof String link && !link.isBlank()) {
                        sb.append("  source url: ").append(link).append('\n');
                    }
                }
            }
        }

        if (trace != null) {
            sb.append("\nTrace summary: ").append(nullSafe(trace.summary())).append("\n");

            if (!trace.upstream().isEmpty()) {
                sb.append("Upstream:\n");
                trace.upstream().stream().limit(5).forEach(node ->
                        sb.append("- ").append(node.qualifiedName()).append("\n"));
            }

            if (!trace.downstream().isEmpty()) {
                sb.append("Downstream:\n");
                trace.downstream().stream().limit(5).forEach(node ->
                        sb.append("- ").append(node.qualifiedName()).append("\n"));
            }

            if (!trace.reachableEndpoints().isEmpty()) {
                sb.append("Reachable endpoints:\n");
                trace.reachableEndpoints().stream().limit(5).forEach(item ->
                        sb.append("- ").append(item.subtitle()).append("\n"));
            }
        }

        sb.append("\nLLM backend error: ").append(error.getClass().getSimpleName());
        return sb.toString();
    }

    private String formatKnowledge(KnowledgeResponseDto knowledge) {
        StringBuilder sb = new StringBuilder();

        sb.append("Mode: ").append(knowledge.mode()).append('\n');
        sb.append("Summary: ").append(nullSafe(knowledge.summary())).append('\n');
        sb.append("Result count: ").append(knowledge.resultCount()).append('\n');

        if (knowledge.sourcesUsed() != null && !knowledge.sourcesUsed().isEmpty()) {
            sb.append("Sources used: ").append(String.join(", ", knowledge.sourcesUsed())).append('\n');
        }

        if (knowledge.elapsedMs() != null) {
            sb.append("Retrieval time: ").append(knowledge.elapsedMs()).append(" ms").append('\n');
        }

        for (var item : knowledge.results()) {
            sb.append("- [")
                    .append(item.sourceType())
                    .append("/")
                    .append(item.resultType())
                    .append("] ")
                    .append(nullSafe(item.title()))
                    .append('\n');

            if (hasText(item.subtitle())) {
                sb.append("  ").append(item.subtitle()).append('\n');
            }

            if (hasText(item.location())) {
                sb.append("  location: ").append(item.location()).append('\n');
            }

            if (hasText(item.snippet())) {
                sb.append("  snippet: ").append(item.snippet()).append('\n');
            }

            if (item.highlights() != null && !item.highlights().isEmpty()) {
                sb.append("  highlights: ").append(String.join(", ", item.highlights())).append('\n');
            }

            if (item.metadata() != null && !item.metadata().isEmpty()) {
                sb.append("  metadata: ").append(item.metadata()).append('\n');
            }

            if (item.metadata() != null && !item.metadata().isEmpty()) {
                Object classNames = item.metadata().get("classNames");
                Object endpoints = item.metadata().get("endpoints");
                Object url = item.metadata().get("url");

                if (classNames instanceof java.util.List<?> list && !list.isEmpty()) {
                    sb.append("  referenced classes: ").append(list).append('\n');
                }

                if (endpoints instanceof java.util.List<?> list && !list.isEmpty()) {
                    sb.append("  referenced endpoints: ").append(list).append('\n');
                }

                if (url instanceof String link && !link.isBlank()) {
                    sb.append("  source url: ").append(link).append('\n');
                }
            }
        }

        return sb.toString();
    }

    private String formatTrace(TraceResponseDto trace) {
        if (trace == null) {
            return "No trace available";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(nullSafe(trace.summary())).append('\n');

        if (!trace.upstream().isEmpty()) {
            sb.append("Upstream:\n");
            trace.upstream().stream().limit(6).forEach(node ->
                    sb.append("- ").append(node.qualifiedName()).append('\n'));
        }

        if (!trace.downstream().isEmpty()) {
            sb.append("Downstream:\n");
            trace.downstream().stream().limit(6).forEach(node ->
                    sb.append("- ").append(node.qualifiedName()).append('\n'));
        }

        if (!trace.reachableEndpoints().isEmpty()) {
            sb.append("Reachable endpoints:\n");
            trace.reachableEndpoints().stream().limit(6).forEach(item ->
                    sb.append("- ").append(item.subtitle()).append('\n'));
        }

        return sb.toString();
    }

    private boolean isProblemQuery(String question) {
        if (question == null) return false;

        String q = question.toLowerCase();

        return q.contains("bug")
                || q.contains("issue")
                || q.contains("error")
                || q.contains("fix")
                || q.contains("failure")
                || q.contains("not working")
                || q.contains("duplicate")
                || q.contains("unexpected")
                || q.contains("problem")
                || q.contains("incident")
                || q.contains("root cause");
    }

    private boolean looksLikeJiraIssueKey(String question) {
        if (question == null) {
            return false;
        }
        return question.matches(".*\\b[A-Z][A-Z0-9]+-\\d+\\b.*");
    }

    private String extractJiraIssueKey(String question) {
        if (question == null) {
            return null;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b([A-Z][A-Z0-9]+-\\d+)\\b")
                .matcher(question);

        return matcher.find() ? matcher.group(1) : null;
    }

    private String formatBullets(java.util.List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- none";
        }
        return items.stream()
                .map(item -> "- " + item)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String formatCandidateTargets(java.util.List<com.vena.codesage.dto.CandidateChangeTargetDto> targets) {
        if (targets == null || targets.isEmpty()) {
            return "- none";
        }

        return targets.stream()
                .map(target -> """
                    - %s (score=%d)
                      reasons: %s
                    """.formatted(
                        target.target(),
                        target.score(),
                        String.join("; ", target.reasons())
                ))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String formatSuggestedChanges(java.util.List<com.vena.codesage.dto.SuggestedChangeDto> changes) {
        if (changes == null || changes.isEmpty()) {
            return "- none";
        }

        return changes.stream()
                .map(change -> """
                    - %s
                      related targets: %s
                      reasons: %s
                    """.formatted(
                        change.suggestion(),
                        change.relatedTargets() == null || change.relatedTargets().isEmpty()
                                ? "none"
                                : String.join(", ", change.relatedTargets()),
                        change.reasons() == null || change.reasons().isEmpty()
                                ? "none"
                                : String.join("; ", change.reasons())
                ))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private List<String> prependDash(List<String> items) {
        return items.stream().map(s -> "- " + s).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}