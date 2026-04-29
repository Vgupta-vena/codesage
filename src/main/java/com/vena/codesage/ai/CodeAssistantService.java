package com.vena.codesage.ai;

import com.vena.codesage.ai.prompt.SystemPrompts;
import com.vena.codesage.ai.spi.AnswerGenerationGateway;
import com.vena.codesage.config.AiConfig.CodeSageAiProperties;
import com.vena.codesage.dto.BlastRadiusCountsDto;
import com.vena.codesage.dto.BlastRadiusResponse;
import com.vena.codesage.dto.EndpointMappingResponse;
import com.vena.codesage.dto.EndpointRouteDto;
import com.vena.codesage.dto.EvidenceItemDto;
import com.vena.codesage.dto.ExplainResponse;
import com.vena.codesage.dto.ReachableEndpointsResponse;
import com.vena.codesage.dto.AskRequest;
import com.vena.codesage.dto.AskResponse;
import com.vena.codesage.dto.KnowledgeMode;
import com.vena.codesage.dto.KnowledgeResponseDto;
import com.vena.codesage.dto.KnowledgeResultItemDto;
import com.vena.codesage.graph.model.EndpointMapping;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.repo.EndpointMappingRepository;
import com.vena.codesage.graph.service.CallGraphService;
import com.vena.codesage.graph.service.CodebaseKnowledgeService;
import com.vena.codesage.graph.service.ScanManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CodeAssistantService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeAssistantService.class);

    private static final Pattern DIRECT_ENDPOINT_PATTERN = Pattern.compile(
            "(?i)^\\s*what\\s+endpoints?\\s+(?:is|are)\\s+(.+?)\\s+mapped\\s+to\\s*\\?\\s*$"
    );
    private static final Pattern REACHABLE_ENDPOINTS_PATTERN = Pattern.compile(
            "(?i)^\\s*what\\s+endpoints?\\s+(?:call|reach|invoke)\\s+(.+?)\\s*\\?\\s*$"
    );

    private static final Pattern EXPLAIN_IMPLEMENTATION_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:explain|summarize)\\s+(?:the\\s+)?implementation\\s+of\\s+(.+?)\\s*\\??\\s*$"
    );

    private static final Pattern BLAST_RADIUS_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:what\\s+is\\s+the\\s+|show\\s+)?blast\\s+radius\\s+(?:of|for)\\s+(.+?)\\s*\\??\\s*$"
    );

    private final Optional<AnswerGenerationGateway> answerGenerationGateway;
    private final CodebaseKnowledgeService codebaseKnowledgeService;
    private final CodeSageAiProperties aiProperties;
    private final CallGraphService callGraphService;
    private final EndpointMappingRepository endpointMappingRepository;
    private final ScanManagerService scanManagerService;

    public AskResponse answerDirectEndpointMappingApi(String projectKey, String symbol) {
        return answerDirectEndpointMapping(projectKey, symbol);
    }

    public AskResponse answerEndpointsReachingApi(String projectKey, String symbol) {
        return answerEndpointsReaching(projectKey, symbol);
    }

    public AskResponse explainImplementationApi(String projectKey, String symbol) {
        return explainImplementation(projectKey, symbol);
    }

    public AskResponse blastRadiusApi(String projectKey, String symbol) {
        return blastRadius(projectKey, symbol);
    }

    public CodeAssistantService(Optional<AnswerGenerationGateway> answerGenerationGateway,
                                CodebaseKnowledgeService codebaseKnowledgeService,
                                CodeSageAiProperties aiProperties,
                                CallGraphService callGraphService,
                                EndpointMappingRepository endpointMappingRepository,
                                ScanManagerService scanManagerService) {
        this.answerGenerationGateway = answerGenerationGateway;
        this.codebaseKnowledgeService = codebaseKnowledgeService;
        this.aiProperties = aiProperties;
        this.callGraphService = callGraphService;
        this.endpointMappingRepository = endpointMappingRepository;
        this.scanManagerService = scanManagerService;
    }

    public EndpointMappingResponse endpointMappingDetails(String projectKey, String symbol) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);
        List<EndpointMapping> mappings = endpointMappingRepository
                .findByScanRunIdAndMethodQualifiedName(activeScan.getId(), symbol);

        String filePath = mappings.stream()
                .map(EndpointMapping::getFilePath)
                .filter(path -> path != null && !path.isBlank())
                .findFirst()
                .orElse("unknown");

        return new EndpointMappingResponse(
                projectKey,
                symbol,
                filePath,
                toRouteDtos(mappings)
        );
    }

    public ReachableEndpointsResponse reachableEndpointsDetails(String projectKey, String symbol) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);
        List<EndpointMapping> directMappings = endpointMappingRepository
                .findByScanRunIdAndMethodQualifiedName(activeScan.getId(), symbol);
        List<String> reachableEndpoints = safeTop(
                callGraphService.findEndpointsReachingMethod(projectKey, symbol, true, true, 20),
                20
        );

        String filePath = directMappings.stream()
                .map(EndpointMapping::getFilePath)
                .filter(path -> path != null && !path.isBlank())
                .findFirst()
                .orElse("unknown");

        String message = directMappings.isEmpty() && reachableEndpoints.isEmpty()
                ? "No graph-reachable endpoints were found for this symbol."
                : directMappings.isEmpty()
                ? null
                : reachableEndpoints.isEmpty()
                ? "No other graph-reachable endpoints were found calling this method."
                : null;

        return new ReachableEndpointsResponse(
                projectKey,
                symbol,
                !directMappings.isEmpty(),
                toRouteDtos(directMappings),
                reachableEndpoints,
                message,
                filePath
        );
    }

    public ExplainResponse explainImplementationDetails(String projectKey, String symbol) {
        ExplainDetails details = collectExplainDetails(projectKey, symbol);

        return new ExplainResponse(
                projectKey,
                symbol,
                details.filePath(),
                details.directRoutes(),
                details.directCallers(),
                details.directCallees(),
                details.evidence(),
                details.summary()
        );
    }

    public BlastRadiusResponse blastRadiusDetails(String projectKey, String symbol) {
        BlastRadiusDetails details = collectBlastRadiusDetails(projectKey, symbol);

        return new BlastRadiusResponse(
                projectKey,
                symbol,
                details.filePath(),
                details.isEndpointHandler(),
                details.directRoutes(),
                details.directCallers(),
                details.directCallees(),
                details.reachableEndpoints(),
                new BlastRadiusCountsDto(
                        details.directCallers().size(),
                        details.directCallees().size(),
                        details.reachableEndpoints().size(),
                        details.directRoutes().size()
                ),
                details.evidence()
        );
    }

    public AskResponse ask(AskRequest request) {
        String question = request.question() == null ? "" : request.question().trim();
        if (question.isBlank()) {
            return new AskResponse("Question must not be blank.");
        }

        Matcher directEndpointMatcher = DIRECT_ENDPOINT_PATTERN.matcher(question);
        if (directEndpointMatcher.matches()) {
            return answerDirectEndpointMapping(request.projectKey(), directEndpointMatcher.group(1).trim());
        }

        Matcher reachableEndpointsMatcher = REACHABLE_ENDPOINTS_PATTERN.matcher(question);
        if (reachableEndpointsMatcher.matches()) {
            return answerEndpointsReaching(request.projectKey(), reachableEndpointsMatcher.group(1).trim());
        }

        Matcher explainImplementationMatcher = EXPLAIN_IMPLEMENTATION_PATTERN.matcher(question);
        if (explainImplementationMatcher.matches()) {
            return explainImplementation(request.projectKey(), explainImplementationMatcher.group(1).trim());
        }

        Matcher blastRadiusMatcher = BLAST_RADIUS_PATTERN.matcher(question);
        if (blastRadiusMatcher.matches()) {
            return blastRadius(request.projectKey(), blastRadiusMatcher.group(1).trim());
        }

        KnowledgeResponseDto knowledge = codebaseKnowledgeService.query(
                request.projectKey(),
                question,
                aiProperties.maxKnowledgeResults(),
                aiProperties.includeDebugEvidence(),
                KnowledgeMode.AUTO,
                true,
                null
        );

        if (knowledge.results().isEmpty()) {
            return new AskResponse("I could not find matching code intelligence evidence for project '%s'. Try a more specific class, method, or endpoint query.".formatted(request.projectKey()));
        }

        String fallbackAnswer = buildDeterministicAnswer(knowledge);
        if (!aiProperties.enabled() || answerGenerationGateway.isEmpty()) {
            return new AskResponse(fallbackAnswer);
        }

        try {
            String prompt = buildUserPrompt(request, knowledge);
            String generated = answerGenerationGateway.get().generateAnswer(SystemPrompts.CODE_ASSISTANT, prompt);
            if (generated == null || generated.isBlank()) {
                return new AskResponse(fallbackAnswer);
            }
            return new AskResponse(generated.trim());
        } catch (RuntimeException ex) {
            LOGGER.warn("Falling back to deterministic answer for project={} because AI generation failed: {}",
                    request.projectKey(), ex.getMessage());
            return new AskResponse(fallbackAnswer);
        }
    }

    private AskResponse answerDirectEndpointMapping(String projectKey, String methodQualifiedName) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);
        List<EndpointMapping> mappings = endpointMappingRepository
                .findByScanRunIdAndMethodQualifiedName(activeScan.getId(), methodQualifiedName);

        if (mappings.isEmpty()) {
            return symbolNotFoundResponse(projectKey, methodQualifiedName);
        }

        List<String> formattedRoutes = buildDistinctRoutes(mappings);
        String filePath = mappings.stream()
                .map(EndpointMapping::getFilePath)
                .filter(path -> path != null && !path.isBlank())
                .findFirst()
                .orElse("unknown");

        StringBuilder answer = new StringBuilder();
        answer.append("Direct endpoint mapping");
        answer.append(formattedRoutes.size() == 1 ? "" : "s");
        answer.append(" for ").append(methodQualifiedName).append(":\n\n");

        for (String route : formattedRoutes) {
            answer.append("- ").append(route).append("\n");
        }

        answer.append("\nfile: ").append(filePath);
        return new AskResponse(answer.toString().trim());
    }

    private ExplainDetails collectExplainDetails(String projectKey, String symbol) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);

        List<EndpointMapping> directMappings = endpointMappingRepository
                .findByScanRunIdAndMethodQualifiedName(activeScan.getId(), symbol);

        KnowledgeResponseDto knowledge = codebaseKnowledgeService.query(
                projectKey,
                symbol,
                Math.min(aiProperties.maxKnowledgeResults(), 8),
                aiProperties.includeDebugEvidence(),
                KnowledgeMode.AUTO,
                true,
                null
        );

        List<String> callers = safeTop(
                callGraphService.findDirectCallers(projectKey, symbol, true, true, 8),
                8
        );
        List<String> callees = safeTop(
                callGraphService.findDirectCallees(projectKey, symbol, true, true, 8),
                8
        );

        String filePath = directMappings.stream()
                .map(EndpointMapping::getFilePath)
                .filter(path -> path != null && !path.isBlank())
                .findFirst()
                .orElseGet(() -> knowledge.results().stream()
                        .map(KnowledgeResultItemDto::location)
                        .filter(path -> path != null && !path.isBlank())
                        .findFirst()
                        .orElse("unknown"));

        String summary = explainImplementation(projectKey, symbol).answer();

        return new ExplainDetails(
                filePath,
                toRouteDtos(directMappings),
                callers,
                callees,
                toEvidenceDtos(knowledge.results(), 3),
                summary
        );
    }

    private BlastRadiusDetails collectBlastRadiusDetails(String projectKey, String symbol) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);

        List<EndpointMapping> directMappings = endpointMappingRepository
                .findByScanRunIdAndMethodQualifiedName(activeScan.getId(), symbol);

        List<String> reachableEndpoints = safeTop(
                callGraphService.findEndpointsReachingMethod(projectKey, symbol, true, true, 20),
                20
        );

        List<String> directCallers = safeTop(
                callGraphService.findDirectCallers(projectKey, symbol, true, true, 10),
                10
        );

        List<String> directCallees = safeTop(
                callGraphService.findDirectCallees(projectKey, symbol, true, true, 10),
                10
        );

        KnowledgeResponseDto knowledge = codebaseKnowledgeService.query(
                projectKey,
                symbol,
                Math.min(aiProperties.maxKnowledgeResults(), 6),
                aiProperties.includeDebugEvidence(),
                KnowledgeMode.AUTO,
                true,
                null
        );

        String filePath = directMappings.stream()
                .map(EndpointMapping::getFilePath)
                .filter(path -> path != null && !path.isBlank())
                .findFirst()
                .orElseGet(() -> knowledge.results().stream()
                        .map(KnowledgeResultItemDto::location)
                        .filter(path -> path != null && !path.isBlank())
                        .findFirst()
                        .orElse("unknown"));

        return new BlastRadiusDetails(
                filePath,
                !directMappings.isEmpty(),
                toRouteDtos(directMappings),
                directCallers,
                directCallees,
                reachableEndpoints,
                toEvidenceDtos(knowledge.results(), 3)
        );
    }

    private AskResponse answerEndpointsReaching(String projectKey, String methodQualifiedName) {
        List<String> endpoints = callGraphService.findEndpointsReachingMethod(
                projectKey,
                methodQualifiedName,
                true,
                true,
                20
        );

        if (!endpoints.isEmpty()) {
            StringBuilder answer = new StringBuilder();
            answer.append("Graph-reachable endpoints for ").append(methodQualifiedName).append(":\n\n");
            for (String endpoint : endpoints) {
                answer.append("- ").append(endpoint).append("\n");
            }
            return new AskResponse(answer.toString().trim());
        }

        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);
        List<EndpointMapping> directMappings = endpointMappingRepository
                .findByScanRunIdAndMethodQualifiedName(activeScan.getId(), methodQualifiedName);

        if (!directMappings.isEmpty()) {
            List<String> formattedRoutes = buildDistinctRoutes(directMappings);
            String filePath = directMappings.stream()
                    .map(EndpointMapping::getFilePath)
                    .filter(path -> path != null && !path.isBlank())
                    .findFirst()
                    .orElse("unknown");

            StringBuilder answer = new StringBuilder();
            answer.append(methodQualifiedName)
                    .append(" is itself an endpoint handler.\n\n")
                    .append("Direct endpoint mapping");
            answer.append(formattedRoutes.size() == 1 ? "" : "s");
            answer.append(":\n");

            for (String route : formattedRoutes) {
                answer.append("- ").append(route).append("\n");
            }

            answer.append("\nNo other graph-reachable endpoints were found calling this method.")
                    .append("\n\nfile: ").append(filePath);

            return new AskResponse(answer.toString().trim());
        }

        return symbolNotFoundResponse(projectKey, methodQualifiedName);
    }

    private String buildUserPrompt(AskRequest request, KnowledgeResponseDto knowledge) {
        return """
                Project: %s
                Question: %s

                Evidence summary: %s

                Top evidence:
                %s

                Write a concise answer grounded only in the evidence above.
                Include qualified names and paths when present.
                If evidence is partial, say so clearly.
                """.formatted(
                request.projectKey(),
                request.question(),
                knowledge.summary(),
                formatEvidence(knowledge.results())
        );
    }

    private String buildDeterministicAnswer(KnowledgeResponseDto knowledge) {
        String header = "Found %d relevant code intelligence result(s). %s".formatted(
                knowledge.results().size(),
                knowledge.summary()
        );
        return header + "\n\n" + formatEvidence(knowledge.results());
    }

    private String formatEvidence(List<KnowledgeResultItemDto> items) {
        return items.stream()
                .limit(aiProperties.maxKnowledgeResults())
                .map(this::formatItem)
                .collect(Collectors.joining("\n\n"));
    }

    private String formatItem(KnowledgeResultItemDto item) {
        StringBuilder builder = new StringBuilder();
        builder.append("- [")
                .append(item.sourceType())
                .append("] ")
                .append(nullSafe(item.title()));

        if (item.subtitle() != null && !item.subtitle().isBlank()) {
            builder.append("\n  qualifiedName: ").append(item.subtitle());
        }
        if (item.location() != null && !item.location().isBlank()) {
            builder.append("\n  path: ").append(item.location());
        }
        if (item.snippet() != null && !item.snippet().isBlank()) {
            builder.append("\n  snippet: ").append(item.snippet());
        }
        if (item.metadata() != null && !item.metadata().isEmpty()) {
            builder.append("\n  metadata: ").append(item.metadata());
        }
        return builder.toString();
    }

    private List<EndpointRouteDto> toRouteDtos(List<EndpointMapping> mappings) {
        Set<EndpointRouteDto> fullRoutes = new LinkedHashSet<>();
        List<EndpointRouteDto> partialRoutes = new ArrayList<>();

        for (EndpointMapping mapping : mappings) {
            String httpMethod = safe(mapping.getHttpMethod()).toUpperCase(Locale.ROOT);
            String path = buildPath(mapping.getClassPath(), mapping.getMethodPath());

            if ("/".equals(path)) {
                continue;
            }

            EndpointRouteDto dto = new EndpointRouteDto(httpMethod, path);

            if (hasBothPathParts(mapping)) {
                fullRoutes.add(dto);
            } else {
                partialRoutes.add(dto);
            }
        }

        if (!fullRoutes.isEmpty()) {
            return List.copyOf(fullRoutes);
        }

        return partialRoutes.stream().distinct().toList();
    }

    private List<EvidenceItemDto> toEvidenceDtos(List<KnowledgeResultItemDto> items, int limit) {
        return items.stream()
                .limit(limit)
                .map(item -> new EvidenceItemDto(
                        nullSafe(item.title()),
                        item.location(),
                        item.snippet()
                ))
                .toList();
    }

    private List<String> buildDistinctRoutes(List<EndpointMapping> mappings) {
        Set<String> routes = new LinkedHashSet<>();
        List<String> partialRoutes = new ArrayList<>();

        for (EndpointMapping mapping : mappings) {
            String route = formatRoute(mapping);
            if (route.endsWith(" /")) {
                continue;
            }
            if (hasBothPathParts(mapping)) {
                routes.add(route);
            } else {
                partialRoutes.add(route);
            }
        }

        if (!routes.isEmpty()) {
            return List.copyOf(routes);
        }

        routes.addAll(partialRoutes);
        return List.copyOf(routes);
    }

    private AskResponse symbolNotFoundResponse(String projectKey, String methodQualifiedName) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);

        String methodFragment = extractLookupFragment(methodQualifiedName);
        String declaringTypeFragment = extractDeclaringTypeFragment(methodQualifiedName);

        List<EndpointMapping> candidates = new ArrayList<>();

        candidates.addAll(endpointMappingRepository
                .findTop10ByScanRunIdAndMethodQualifiedNameContainingIgnoreCase(activeScan.getId(), methodQualifiedName));

        if (candidates.isEmpty() && !methodFragment.isBlank()) {
            candidates.addAll(endpointMappingRepository
                    .findTop10ByScanRunIdAndMethodQualifiedNameContainingIgnoreCase(activeScan.getId(), methodFragment));
        }

        if (candidates.isEmpty() && !declaringTypeFragment.isBlank()) {
            candidates.addAll(endpointMappingRepository
                    .findTop10ByScanRunIdAndMethodQualifiedNameContainingIgnoreCase(activeScan.getId(), declaringTypeFragment));
        }

        List<String> suggestions = candidates.stream()
                .map(EndpointMapping::getMethodQualifiedName)
                .filter(q -> q != null && !q.isBlank())
                .distinct()
                .limit(5)
                .toList();

        StringBuilder answer = new StringBuilder();
        answer.append("No exact symbol match found for ")
                .append(methodQualifiedName)
                .append(" in project ")
                .append(projectKey)
                .append(".");

        if (!suggestions.isEmpty()) {
            answer.append("\n\nClosest matches:\n");
            for (String suggestion : suggestions) {
                answer.append("- ").append(suggestion).append("\n");
            }
        }

        return new AskResponse(answer.toString().trim());
    }

    private String extractDeclaringTypeFragment(String methodQualifiedName) {
        if (methodQualifiedName == null || methodQualifiedName.isBlank()) {
            return "";
        }

        int lastDot = methodQualifiedName.lastIndexOf('.');
        if (lastDot <= 0) {
            return "";
        }

        return methodQualifiedName.substring(0, lastDot);
    }

    private String extractLookupFragment(String methodQualifiedName) {
        if (methodQualifiedName == null || methodQualifiedName.isBlank()) {
            return "";
        }

        int lastDot = methodQualifiedName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < methodQualifiedName.length() - 1) {
            return methodQualifiedName.substring(lastDot + 1);
        }
        return methodQualifiedName;
    }

    private AskResponse explainImplementation(String projectKey, String symbol) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);

        List<EndpointMapping> directMappings = endpointMappingRepository
                .findByScanRunIdAndMethodQualifiedName(activeScan.getId(), symbol);

        KnowledgeResponseDto knowledge = codebaseKnowledgeService.query(
                projectKey,
                symbol,
                Math.min(aiProperties.maxKnowledgeResults(), 8),
                aiProperties.includeDebugEvidence(),
                KnowledgeMode.AUTO,
                true,
                null
        );

        // Replace these with your exact CallGraphService methods if names differ.
        List<String> callers = safeTop(
                callGraphService.findDirectCallers(projectKey, symbol, true, true, 8),
                8
        );
        List<String> callees = safeTop(
                callGraphService.findDirectCallees(projectKey, symbol, true, true, 8),
                8
        );
        boolean hasDirectEvidence = !directMappings.isEmpty()
                || !callers.isEmpty()
                || !callees.isEmpty()
                || !knowledge.results().isEmpty();

        if (!hasDirectEvidence) {
            return symbolNotFoundResponse(projectKey, symbol);
        }

        String filePath = directMappings.stream()
                .map(EndpointMapping::getFilePath)
                .filter(path -> path != null && !path.isBlank())
                .findFirst()
                .orElseGet(() -> knowledge.results().stream()
                        .map(KnowledgeResultItemDto::location)
                        .filter(path -> path != null && !path.isBlank())
                        .findFirst()
                        .orElse("unknown"));

        StringBuilder answer = new StringBuilder();
        answer.append("Implementation summary for ").append(symbol).append("\n\n");

        answer.append("Location:\n");
        answer.append("- ").append(filePath).append("\n");

        if (!directMappings.isEmpty()) {
            List<String> formattedRoutes = buildDistinctRoutes(directMappings);
            answer.append("\nDirect endpoint mapping");
            answer.append(formattedRoutes.size() == 1 ? "" : "s");
            answer.append(":\n");
            for (String route : formattedRoutes) {
                answer.append("- ").append(route).append("\n");
            }
        }

        if (!callers.isEmpty()) {
            answer.append("\nDirect callers:\n");
            for (String caller : callers) {
                answer.append("- ").append(caller).append("\n");
            }
        }

        if (!callees.isEmpty()) {
            answer.append("\nDirect callees:\n");
            for (String callee : callees) {
                answer.append("- ").append(callee).append("\n");
            }
        }

        if (!knowledge.results().isEmpty()) {
            answer.append("\nRelevant evidence:\n");
            knowledge.results().stream()
                    .limit(3)
                    .forEach(item -> {
                        answer.append("- ").append(nullSafe(item.title()));
                        if (item.location() != null && !item.location().isBlank()) {
                            answer.append(" (").append(item.location()).append(")");
                        }
                        if (item.snippet() != null && !item.snippet().isBlank()) {
                            answer.append("\n  ").append(item.snippet());
                        }
                        answer.append("\n");
                    });
        }

        String deterministic = answer.toString().trim();

        if (!aiProperties.enabled() || answerGenerationGateway.isEmpty()) {
            return new AskResponse(deterministic);
        }

        try {
            String prompt = """
                Project: %s
                Symbol: %s

                Deterministic evidence:
                %s

                Rewrite this as a concise implementation explanation.
                Stay strictly grounded in the evidence.
                Do not invent behavior that is not supported.
                """.formatted(projectKey, symbol, deterministic);

            String generated = answerGenerationGateway.get()
                    .generateAnswer(SystemPrompts.CODE_ASSISTANT, prompt);

            if (generated == null || generated.isBlank()) {
                return new AskResponse(deterministic);
            }
            return new AskResponse(generated.trim());
        } catch (RuntimeException ex) {
            LOGGER.warn("Falling back to deterministic implementation summary for project={} symbol={} because AI generation failed: {}",
                    projectKey, symbol, ex.getMessage());
            return new AskResponse(deterministic);
        }
    }

    private AskResponse blastRadius(String projectKey, String symbol) {
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);

        List<EndpointMapping> directMappings = endpointMappingRepository
                .findByScanRunIdAndMethodQualifiedName(activeScan.getId(), symbol);

        List<String> reachableEndpoints = safeTop(
                callGraphService.findEndpointsReachingMethod(projectKey, symbol, true, true, 20),
                20
        );

        List<String> directCallers = safeTop(
                callGraphService.findDirectCallers(projectKey, symbol, true, true, 10),
                10
        );

        List<String> directCallees = safeTop(
                callGraphService.findDirectCallees(projectKey, symbol, true, true, 10),
                10
        );

        KnowledgeResponseDto knowledge = codebaseKnowledgeService.query(
                projectKey,
                symbol,
                Math.min(aiProperties.maxKnowledgeResults(), 6),
                aiProperties.includeDebugEvidence(),
                KnowledgeMode.AUTO,
                true,
                null
        );

        boolean hasEvidence = !directMappings.isEmpty()
                || !reachableEndpoints.isEmpty()
                || !directCallers.isEmpty()
                || !directCallees.isEmpty()
                || !knowledge.results().isEmpty();

        if (!hasEvidence) {
            return symbolNotFoundResponse(projectKey, symbol);
        }

        String filePath = directMappings.stream()
                .map(EndpointMapping::getFilePath)
                .filter(path -> path != null && !path.isBlank())
                .findFirst()
                .orElseGet(() -> knowledge.results().stream()
                        .map(KnowledgeResultItemDto::location)
                        .filter(path -> path != null && !path.isBlank())
                        .findFirst()
                        .orElse("unknown"));

        StringBuilder answer = new StringBuilder();
        answer.append("Blast radius for ").append(symbol).append("\n\n");

        answer.append("Location:\n");
        answer.append("- ").append(filePath).append("\n");

        if (!directMappings.isEmpty()) {
            List<String> formattedRoutes = buildDistinctRoutes(directMappings);
            answer.append("\nThis method is itself an endpoint handler.\n");
            answer.append("Direct endpoint mapping");
            answer.append(formattedRoutes.size() == 1 ? "" : "s");
            answer.append(":\n");
            for (String route : formattedRoutes) {
                answer.append("- ").append(route).append("\n");
            }
        }

        if (!reachableEndpoints.isEmpty()) {
            answer.append("\nGraph-reachable endpoints:\n");
            for (String endpoint : reachableEndpoints) {
                answer.append("- ").append(endpoint).append("\n");
            }
        }

        if (!directCallers.isEmpty()) {
            answer.append("\nDirect callers:\n");
            for (String caller : directCallers) {
                answer.append("- ").append(caller).append("\n");
            }
        }

        if (!directCallees.isEmpty()) {
            answer.append("\nDirect callees:\n");
            for (String callee : directCallees) {
                answer.append("- ").append(callee).append("\n");
            }
        }

        answer.append("\nImpact summary:\n");
        answer.append("- Direct callers: ").append(directCallers.size()).append("\n");
        answer.append("- Direct callees: ").append(directCallees.size()).append("\n");
        answer.append("- Graph-reachable endpoints: ").append(reachableEndpoints.size()).append("\n");
        answer.append("- Direct endpoint mappings: ").append(directMappings.isEmpty() ? 0 : buildDistinctRoutes(directMappings).size()).append("\n");

        if (!knowledge.results().isEmpty()) {
            answer.append("\nRelevant evidence:\n");
            knowledge.results().stream()
                    .limit(3)
                    .forEach(item -> {
                        answer.append("- ").append(nullSafe(item.title()));
                        if (item.location() != null && !item.location().isBlank()) {
                            answer.append(" (").append(item.location()).append(")");
                        }
                        answer.append("\n");
                    });
        }

        return new AskResponse(answer.toString().trim());
    }

    private List<String> safeTop(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .limit(limit)
                .toList();
    }

    private String formatRoute(EndpointMapping mapping) {
        String httpMethod = safe(mapping.getHttpMethod()).toUpperCase(Locale.ROOT);
        String path = buildPath(mapping.getClassPath(), mapping.getMethodPath());
        return httpMethod + " " + path;
    }

    private boolean hasBothPathParts(EndpointMapping mapping) {
        return hasNormalizedPath(mapping.getClassPath()) && hasNormalizedPath(mapping.getMethodPath());
    }

    private boolean hasNormalizedPath(String value) {
        return !trimSlashes(value).isBlank();
    }

    private String buildPath(String classPath, String methodPath) {
        String left = trimSlashes(classPath);
        String right = trimSlashes(methodPath);

        if (left.isBlank() && right.isBlank()) {
            return "/";
        }
        if (left.isBlank()) {
            return "/" + right;
        }
        if (right.isBlank()) {
            return "/" + left;
        }
        return "/" + left + "/" + right;
    }

    private String trimSlashes(String value) {
        if (value == null || value.isBlank()) {
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

    private String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "Untitled result" : value;
    }

    private record ExplainDetails(
            String filePath,
            List<EndpointRouteDto> directRoutes,
            List<String> directCallers,
            List<String> directCallees,
            List<EvidenceItemDto> evidence,
            String summary
    ) {}

    private record BlastRadiusDetails(
            String filePath,
            boolean isEndpointHandler,
            List<EndpointRouteDto> directRoutes,
            List<String> directCallers,
            List<String> directCallees,
            List<String> reachableEndpoints,
            List<EvidenceItemDto> evidence
    ) {}
}