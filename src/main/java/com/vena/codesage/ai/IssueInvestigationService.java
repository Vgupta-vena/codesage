package com.vena.codesage.ai;

import com.vena.codesage.dto.*;
import com.vena.codesage.graph.service.CodebaseKnowledgeService;
import com.vena.codesage.graph.service.ReverseTraversalService;
import com.vena.codesage.integration.jira.JiraIssue;
import com.vena.codesage.integration.jira.JiraIssueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IssueInvestigationService {

    private static final Logger log = LoggerFactory.getLogger(IssueInvestigationService.class);

    private final CodebaseKnowledgeService codebaseKnowledgeService;
    private final ReverseTraversalService reverseTraversalService;
    private final JiraIssueService jiraIssueService;

    public IssueInvestigationService(CodebaseKnowledgeService codebaseKnowledgeService,
                                     ReverseTraversalService reverseTraversalService,
                                     JiraIssueService jiraIssueService) {
        this.codebaseKnowledgeService = codebaseKnowledgeService;
        this.reverseTraversalService = reverseTraversalService;
        this.jiraIssueService = jiraIssueService;
    }

    public IssueAnalysisResponseDto investigate(String projectKey, String problemStatement) {
        return investigateInternal(projectKey, problemStatement, null);
    }

    public IssueAnalysisResponseDto investigateIssue(String projectKey, String issueKey) {
        JiraIssue issue = jiraIssueService.getIssue(issueKey);
        if (issue == null) {
            return IssueAnalysisResponseDto.failure(
                    issueKey,
                    "Jira issue could not be loaded.",
                    List.of("Verify that the Jira key exists and integration credentials are correct."),
                    List.of(
                            new SuggestedChangeDto(
                                    "Retry after validating Jira connectivity and permissions.",
                                    List.of(),
                                    List.of("The Jira issue could not be loaded, so investigation could not start from the issue seed.")
                            )
                    )
            );
        }

        return investigateInternal(projectKey, issue.problemStatement(), issue);
    }

    private IssueAnalysisResponseDto investigateInternal(String projectKey,
                                                         String problemStatement,
                                                         JiraIssue jiraIssue) {
        long startNanos = System.nanoTime();

        KnowledgeResponseDto knowledge = codebaseKnowledgeService.query(
                projectKey,
                problemStatement,
                10,
                false,
                KnowledgeMode.AUTO,
                true,
                null
        );

        TraceResponseDto trace = buildTrace(projectKey, knowledge);

        List<KnowledgeResultItemDto> evidence = mergeEvidence(jiraIssue, knowledge);
        List<String> hypotheses = buildHypotheses(problemStatement, jiraIssue, knowledge, trace);
        List<CandidateChangeTargetDto> changeTargets = extractChangeTargets(knowledge, trace);
        List<SuggestedChangeDto> suggestedChanges = buildSuggestedChanges(
                problemStatement,
                jiraIssue,
                knowledge,
                trace,
                changeTargets
        );

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("Issue investigation complete projectKey={} problem={} evidenceCount={} elapsedMs={}",
                projectKey, problemStatement, evidence.size(), elapsedMillis);

        return new IssueAnalysisResponseDto(
                jiraIssue != null ? jiraIssue.key() : problemStatement,
                buildSummary(jiraIssue, knowledge, trace, hypotheses),
                evidence,
                hypotheses,
                suggestedChanges,
                changeTargets,
                trace == null ? "No trace available" : trace.summary()
        );
    }

    private TraceResponseDto buildTrace(String projectKey, KnowledgeResponseDto knowledge) {
        if (knowledge == null || knowledge.results().isEmpty()) {
            return null;
        }

        String seed = knowledge.results().getFirst().title();
        if (seed == null || seed.isBlank()) {
            return null;
        }

        try {
            return reverseTraversalService.trace(projectKey, seed, TraceDirection.BOTH, 3, 20);
        } catch (Exception ex) {
            log.debug("Trace build failed projectKey={} seed={}", projectKey, seed, ex);
            return null;
        }
    }

    private List<KnowledgeResultItemDto> mergeEvidence(JiraIssue jiraIssue, KnowledgeResponseDto knowledge) {
        List<KnowledgeResultItemDto> merged = new ArrayList<>();

        if (jiraIssue != null) {
            merged.add(toJiraEvidence(jiraIssue));
        }

        merged.addAll(knowledge.results());
        return merged;
    }

    private KnowledgeResultItemDto toJiraEvidence(JiraIssue issue) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", issue.status());
        metadata.put("priority", issue.priority());
        metadata.put("assignee", issue.assignee());
        metadata.put("url", issue.webUrl());

        return KnowledgeResultItemDto.external(
                "TICKET",
                KnowledgeSourceType.JIRA,
                issue.key(),
                issue.key() + ": " + issue.summary(),
                issue.issueType() + " | " + issue.status(),
                issue.webUrl(),
                issue.description(),
                95,
                List.of(),
                metadata
        );
    }

    private List<String> buildHypotheses(String problemStatement,
                                         JiraIssue jiraIssue,
                                         KnowledgeResponseDto knowledge,
                                         TraceResponseDto trace) {
        List<String> hypotheses = new ArrayList<>();

        if (jiraIssue != null) {
            hypotheses.add("The Jira issue description should be treated as the primary problem statement and compared against current implementation behavior.");
        }

        boolean hasConfluenceEvidence = knowledge.results().stream()
                .anyMatch(item -> item.sourceType() == KnowledgeSourceType.CONFLUENCE);

        boolean hasConfluenceCodeSignals = knowledge.results().stream()
                .filter(item -> item.sourceType() == KnowledgeSourceType.CONFLUENCE)
                .map(KnowledgeResultItemDto::metadata)
                .filter(metadata -> metadata != null)
                .anyMatch(metadata -> metadata.containsKey("classNames") || metadata.containsKey("endpoints"));

        if (hasConfluenceEvidence) {
            hypotheses.add("Relevant business or architecture documentation exists and should be compared with implementation flow.");
        }

        if (hasConfluenceCodeSignals) {
            hypotheses.add("Documentation references implementation-level signals, which makes documentation-to-code mismatch analysis more reliable.");
        }

        if (trace != null && !trace.reachableEndpoints().isEmpty()) {
            hypotheses.add("The problem appears reachable from one or more exposed endpoints, so entry-point behavior may be contributing.");
        }

        if (trace != null && trace.downstream().size() > 5) {
            hypotheses.add("The affected flow has broad downstream spread, so side effects or missing guards may be part of the issue.");
        }

        String normalizedProblem = problemStatement == null ? "" : problemStatement.toLowerCase();
        if (normalizedProblem.contains("duplicate")) {
            hypotheses.add("The issue may involve missing idempotency, retry duplication, or callback reprocessing.");
        }
        if (normalizedProblem.contains("timeout") || normalizedProblem.contains("slow")) {
            hypotheses.add("The problem may involve a synchronous boundary or downstream dependency causing latency amplification.");
        }
        if (normalizedProblem.contains("null") || normalizedProblem.contains("npe")) {
            hypotheses.add("The issue may be caused by missing null guards or incomplete input/state validation along the traced flow.");
        }

        if (hypotheses.isEmpty()) {
            hypotheses.add("The top-ranked code and document evidence should be reviewed together to identify behavior and intent mismatches.");
        }

        return hypotheses;
    }

    private List<SuggestedChangeDto> buildSuggestedChanges(String problemStatement,
                                                           JiraIssue jiraIssue,
                                                           KnowledgeResponseDto knowledge,
                                                           TraceResponseDto trace,
                                                           List<CandidateChangeTargetDto> candidateTargets) {
        List<SuggestedChangeDto> changes = new ArrayList<>();

        String normalizedProblem = problemStatement == null ? "" : problemStatement.toLowerCase();

        List<String> topTargets = candidateTargets.stream()
                .limit(3)
                .map(CandidateChangeTargetDto::target)
                .toList();

        boolean hasConfluenceCodeSignals = knowledge.results().stream()
                .filter(item -> item.sourceType() == KnowledgeSourceType.CONFLUENCE)
                .map(KnowledgeResultItemDto::metadata)
                .filter(metadata -> metadata != null)
                .anyMatch(metadata -> metadata.containsKey("classNames") || metadata.containsKey("endpoints"));

        boolean hasReachableEndpoints = trace != null && !trace.reachableEndpoints().isEmpty();

        if (normalizedProblem.contains("duplicate")) {
            changes.add(new SuggestedChangeDto(
                    "Add or verify idempotency protection before persistence writes or external side effects.",
                    topTargets,
                    List.of(
                            "The problem statement indicates duplicate execution risk.",
                            "Candidate targets are connected to the traced flow.",
                            "This type of issue commonly appears when replayed callbacks or retries re-run the same business action."
                    )
            ));

            changes.add(new SuggestedChangeDto(
                    "Review callback and retry handling so repeated delivery does not execute the same capture or state transition twice.",
                    topTargets,
                    List.of(
                            "Duplicate-related wording suggests retry or replay behavior.",
                            hasReachableEndpoints
                                    ? "The flow is reachable from one or more endpoints."
                                    : "The matched evidence suggests an externally triggered flow.",
                            hasConfluenceCodeSignals
                                    ? "Confluence documentation references implementation-level signals for this flow."
                                    : "Relevant implementation evidence exists for the affected path."
                    )
            ));
        }

        if (normalizedProblem.contains("timeout") || normalizedProblem.contains("slow")) {
            changes.add(new SuggestedChangeDto(
                    "Inspect synchronous downstream calls and move slow or retry-heavy operations behind safer asynchronous coordination where appropriate.",
                    topTargets,
                    List.of(
                            "The problem statement suggests latency or blocking behavior.",
                            trace != null && !trace.downstream().isEmpty()
                                    ? "The trace shows downstream execution spread that may amplify latency."
                                    : "Relevant code evidence may involve downstream dependencies."
                    )
            ));
        }

        if (normalizedProblem.contains("null") || normalizedProblem.contains("npe")) {
            changes.add(new SuggestedChangeDto(
                    "Add validation and null-safety checks at the earliest boundary where incomplete input or state can enter the flow.",
                    topTargets,
                    List.of(
                            "The problem statement indicates a null-handling failure.",
                            hasReachableEndpoints
                                    ? "The trace includes reachable endpoints, so boundary validation matters."
                                    : "The matched code path should be checked for missing defensive validation."
                    )
            ));
        }

        if (hasReachableEndpoints) {
            changes.add(new SuggestedChangeDto(
                    "Keep business decisions out of endpoint handlers and validate controller-to-service boundaries in the affected flow.",
                    topTargets,
                    List.of(
                            "Reachable endpoints were identified in the trace.",
                            "Shifting decision logic inward often reduces repeated-entry and boundary-handling issues."
                    )
            ));
        }

        if (hasConfluenceCodeSignals) {
            changes.add(new SuggestedChangeDto(
                    "Compare the documented flow with the current implementation and align any mismatch before finalizing code changes.",
                    topTargets,
                    List.of(
                            "Confluence evidence references classes or endpoints tied to the implementation.",
                            "Documentation-to-code mismatch is a strong source of defects in behavior-heavy flows."
                    )
            ));
        }

        if (jiraIssue != null) {
            changes.add(new SuggestedChangeDto(
                    "Update the Jira issue with traced impact, likely root-cause area, and proposed change direction after validation.",
                    topTargets,
                    List.of(
                            "The investigation was seeded from Jira.",
                            "Capturing impact and likely fix direction improves follow-up execution."
                    )
            ));
        }

        if (changes.isEmpty()) {
            changes.add(new SuggestedChangeDto(
                    "Start with the highest-confidence target and validate whether its current behavior matches the intended business flow.",
                    topTargets,
                    List.of(
                            "No specialized problem pattern was detected.",
                            "Top-ranked evidence still provides a reasonable starting point for investigation."
                    )
            ));
        }

        return deduplicateSuggestedChanges(changes);
    }

    private List<SuggestedChangeDto> deduplicateSuggestedChanges(List<SuggestedChangeDto> changes) {
        Map<String, SuggestedChangeDto> deduped = new LinkedHashMap<>();

        for (SuggestedChangeDto change : changes) {
            deduped.putIfAbsent(change.suggestion(), change);
        }

        return new ArrayList<>(deduped.values());
    }

    private String buildSummary(JiraIssue jiraIssue,
                                KnowledgeResponseDto knowledge,
                                TraceResponseDto trace,
                                List<String> hypotheses) {
        StringBuilder sb = new StringBuilder();

        if (jiraIssue != null) {
            sb.append("Jira issue ").append(jiraIssue.key()).append(" was used as the investigation seed. ");
        }

        sb.append("Found ").append(knowledge.resultCount()).append(" relevant evidence item(s)");
        if (knowledge.sourcesUsed() != null && !knowledge.sourcesUsed().isEmpty()) {
            sb.append(" across ").append(knowledge.sourcesUsed());
        }
        sb.append(". ");

        if (trace != null) {
            sb.append("A supporting execution trace was also identified. ");
        }

        if (!hypotheses.isEmpty()) {
            sb.append("Primary hypothesis: ").append(hypotheses.getFirst());
        }

        return sb.toString();
    }

    private List<CandidateChangeTargetDto> extractChangeTargets(KnowledgeResponseDto knowledge,
                                                                TraceResponseDto trace) {

        Map<String, TargetScore> scores = new LinkedHashMap<>();

        if (trace != null) {
            trace.upstream().forEach(node ->
                    bump(scores, node.qualifiedName(), 5, "Appears in upstream trace"));
            trace.downstream().forEach(node ->
                    bump(scores, node.qualifiedName(), 5, "Appears in downstream trace"));
            trace.reachableEndpoints().forEach(ep ->
                    bump(scores, ep.subtitle(), 6, "Appears as reachable endpoint"));
        }

        for (var item : knowledge.results()) {
            if (item.sourceType() == KnowledgeSourceType.CODE) {
                bump(scores, item.title(), 4, "Matched in code evidence");
            }
        }

        for (var item : knowledge.results()) {
            if (item.sourceType() == KnowledgeSourceType.CONFLUENCE && item.metadata() != null) {

                Object classNames = item.metadata().get("classNames");
                if (classNames instanceof List<?> list) {
                    for (Object c : list) {
                        bump(scores, String.valueOf(c), 6, "Referenced in Confluence documentation");
                    }
                }

                Object endpoints = item.metadata().get("endpoints");
                if (endpoints instanceof List<?> list) {
                    for (Object ep : list) {
                        bump(scores, String.valueOf(ep), 7, "Endpoint referenced in Confluence documentation");
                    }
                }
            }
        }

        return scores.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue().score(), left.getValue().score()))
                .limit(6)
                .map(entry -> new CandidateChangeTargetDto(
                        entry.getKey(),
                        entry.getValue().score(),
                        entry.getValue().reasons().stream().distinct().toList()
                ))
                .toList();
    }

    private void bump(Map<String, TargetScore> scores, String key, int weight, String reason) {
        if (key == null || key.isBlank()) {
            return;
        }

        TargetScore current = scores.get(key);
        if (current == null) {
            scores.put(key, new TargetScore(weight, new java.util.ArrayList<>(List.of(reason))));
            return;
        }

        current.reasons().add(reason);
        scores.put(key, new TargetScore(current.score() + weight, current.reasons()));
    }

    private record TargetScore(int score, List<String> reasons) {
    }

    private void bump(Map<String, Integer> scores, String key, int weight) {
        if (key == null || key.isBlank()) return;

        scores.merge(key, weight, Integer::sum);
    }
}