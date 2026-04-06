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

    public String ask(String projectKey, String question) {
        KnowledgeResponseDto knowledge = codebaseKnowledgeService.query(
                projectKey,
                question,
                8,
                false,
                KnowledgeMode.AUTO,
                true,
                null
        );

        TraceResponseDto trace = buildBestEffortTrace(projectKey, knowledge);

        String userMessage = """
                Project Key: %s

                Retrieved Knowledge:
                %s

                Reverse/Forward Trace:
                %s

                Question:
                %s
                """.formatted(projectKey, formatKnowledge(knowledge), formatTrace(trace), question);

        try {
            return chatClient.prompt()
                    .system(SystemPrompts.CODE_ASSISTANT)
                    .user(userMessage)
                    .tools(tools)
                    .call()
                    .content();
        } catch (Exception e) {
            LOGGER.error("Chat ask failed for projectKey={} question={} error={}",
                    projectKey, question, e.getMessage(), e);

            return buildFallbackAnswer(question, knowledge, trace, e);
        }
    }

    private TraceResponseDto buildBestEffortTrace(String projectKey, KnowledgeResponseDto knowledge) {
        if (knowledge.results().isEmpty()) {
            return null;
        }

        String firstTitle = knowledge.results().get(0).title();

        try {
            return reverseTraversalService.trace(
                    projectKey,
                    firstTitle,
                    TraceDirection.BOTH,
                    2,
                    15
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildFallbackAnswer(String question,
                                       KnowledgeResponseDto knowledge,
                                       TraceResponseDto trace,
                                       Exception e) {
        StringBuilder sb = new StringBuilder();

        sb.append("I could not reach the LLM backend right now, so here is a direct project knowledge response.\n\n");
        sb.append("Question: ").append(question).append("\n");
        sb.append("Mode used: ").append(knowledge.modeUsed()).append("\n");
        sb.append("Summary: ").append(knowledge.summary()).append("\n\n");

        if (!knowledge.results().isEmpty()) {
            sb.append("Top results:\n");
            for (var item : knowledge.results()) {
                sb.append("- [").append(item.resultType()).append("] ").append(item.title()).append("\n");
                sb.append("  ").append(item.subtitle()).append("\n");

                if (item.location() != null && !item.location().isBlank()) {
                    sb.append("  file: ").append(item.location()).append("\n");
                }

                if (item.preview() != null && !item.preview().isBlank()) {
                    sb.append("  preview: ").append(item.preview()).append("\n");
                }

                if (item.highlights() != null && !item.highlights().isEmpty()) {
                    sb.append("  highlights: ").append(String.join(", ", item.highlights())).append("\n");
                }
            }
        }

        if (trace != null) {
            sb.append("\nTrace summary: ").append(trace.summary()).append("\n");
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
        }

        sb.append("\nLLM backend error: ").append(e.getClass().getSimpleName());
        return sb.toString();
    }

    private String formatKnowledge(KnowledgeResponseDto knowledge) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mode: ").append(knowledge.modeUsed()).append('\n');
        sb.append("Summary: ").append(knowledge.summary()).append('\n');

        for (var item : knowledge.results()) {
            sb.append("- [").append(item.resultType()).append("] ")
                    .append(item.title()).append('\n');
            sb.append("  ").append(item.subtitle()).append('\n');

            if (item.location() != null && !item.location().isBlank()) {
                sb.append("  file: ").append(item.location()).append('\n');
            }

            if (item.preview() != null && !item.preview().isBlank()) {
                sb.append("  preview: ").append(item.preview()).append('\n');
            }

            if (item.highlights() != null && !item.highlights().isEmpty()) {
                sb.append("  highlights: ").append(String.join(", ", item.highlights())).append('\n');
            }
        }

        return sb.toString();
    }

    private String formatTrace(TraceResponseDto trace) {
        if (trace == null) {
            return "No trace available";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(trace.summary()).append('\n');

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
}