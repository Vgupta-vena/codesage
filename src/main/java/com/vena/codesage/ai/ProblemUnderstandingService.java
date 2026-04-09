package com.vena.codesage.ai;

import com.vena.codesage.dto.ConceptMatch;
import com.vena.codesage.dto.ProblemUnderstandingResult;
import com.vena.codesage.dto.WorkflowHint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProblemUnderstandingService {

    public ProblemUnderstandingResult understand(String rawText) {
        String normalized = normalize(rawText);

        List<ConceptMatch> concepts = detectConcepts(normalized);
        List<WorkflowHint> workflows = inferWorkflows(normalized, concepts);
        List<String> domainTerms = extractDomainTerms(normalized);
        List<String> technicalTerms = extractTechnicalTerms(normalized);

        return new ProblemUnderstandingResult(
                rawText,
                normalized,
                concepts,
                workflows,
                List.of(),
                List.of(),
                domainTerms,
                technicalTerms,
                Map.of()
        );
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private List<ConceptMatch> detectConcepts(String normalized) {
        List<ConceptMatch> concepts = new ArrayList<>();

        if (normalized.contains("callback")) {
            concepts.add(new ConceptMatch(
                    "Callback Processing",
                    List.of("callback"),
                    80
            ));
        }

        if (normalized.contains("duplicate")) {
            concepts.add(new ConceptMatch(
                    "Duplication Handling",
                    List.of("duplicate"),
                    75
            ));
        }

        if (normalized.contains("retry")) {
            concepts.add(new ConceptMatch(
                    "Retry Handling",
                    List.of("retry"),
                    70
            ));
        }

        if (normalized.contains("datamodel")) {
            concepts.add(new ConceptMatch(
                    "Datamodel Management",
                    List.of("datamodel"),
                    78
            ));
        }

        return concepts;
    }

    private List<WorkflowHint> inferWorkflows(String normalized, List<ConceptMatch> concepts) {
        List<WorkflowHint> workflows = new ArrayList<>();

        boolean callback = containsConcept(concepts, "Callback Processing");
        boolean duplicate = containsConcept(concepts, "Duplication Handling");
        boolean datamodel = containsConcept(concepts, "Datamodel Management");

        if (callback && duplicate && datamodel) {
            workflows.add(new WorkflowHint(
                    "Callback Datamodel Flow",
                    List.of(
                            "Receive callback",
                            "Validate replay or idempotency",
                            "Load or resolve datamodel",
                            "Duplicate or update datamodel",
                            "Persist changes"
                    ),
                    List.of("callback", "duplicate", "datamodel"),
                    90
            ));
        } else if (callback) {
            workflows.add(new WorkflowHint(
                    "Callback Processing Flow",
                    List.of(
                            "Receive callback",
                            "Validate callback",
                            "Trigger service processing",
                            "Persist resulting state"
                    ),
                    List.of("callback"),
                    70
            ));
        }

        return workflows;
    }

    private List<String> extractDomainTerms(String normalized) {
        List<String> terms = new ArrayList<>();

        addIfPresent(normalized, terms, "datamodel");
        addIfPresent(normalized, terms, "template");
        addIfPresent(normalized, terms, "process");
        addIfPresent(normalized, terms, "export");
        addIfPresent(normalized, terms, "file");

        return terms;
    }

    private List<String> extractTechnicalTerms(String normalized) {
        List<String> terms = new ArrayList<>();

        addIfPresent(normalized, terms, "callback");
        addIfPresent(normalized, terms, "retry");
        addIfPresent(normalized, terms, "duplicate");
        addIfPresent(normalized, terms, "timeout");
        addIfPresent(normalized, terms, "poll");

        return terms;
    }

    private void addIfPresent(String normalized, List<String> terms, String token) {
        if (normalized.contains(token)) {
            terms.add(token);
        }
    }

    private boolean containsConcept(List<ConceptMatch> concepts, String concept) {
        return concepts.stream().anyMatch(c -> c.concept().equals(concept));
    }
}