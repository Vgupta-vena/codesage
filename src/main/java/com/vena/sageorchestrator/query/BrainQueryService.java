package com.vena.sageorchestrator.query;

import com.vena.sageorchestrator.api.dto.AnswerResponse;
import com.vena.sageorchestrator.api.dto.BrainQueryRequest;
import com.vena.sageorchestrator.codesage.CodeSageFacade;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BrainQueryService {

    private final CodeSageFacade codeSageFacade;

    public BrainQueryService(CodeSageFacade codeSageFacade) {
        this.codeSageFacade = codeSageFacade;
    }

    public AnswerResponse query(BrainQueryRequest request) {
        String question = request.question().trim();

        if (question.toLowerCase().startsWith("what endpoint is ")) {
            String symbol = question
                    .replaceFirst("(?i)^what endpoint is\\s+", "")
                    .replaceFirst("(?i)\\s+mapped to\\??$", "")
                    .trim();

            var mapping = codeSageFacade.endpointMapping(request.projectKey(), symbol);

            return new AnswerResponse(
                    "Resolved endpoint mapping for " + symbol,
                    "HIGH",
                    List.of(new AnswerResponse.MatchedEntity("CODE_SYMBOL", symbol)),
                    mapping.routes().stream()
                            .map(route -> new AnswerResponse.EvidenceItem(
                                    "codesage",
                                    "endpoint_mapping",
                                    route.httpMethod() + " " + route.path(),
                                    mapping.filePath()
                            ))
                            .toList(),
                    List.of()
            );
        }

        return new AnswerResponse(
                "Intent not implemented yet.",
                "LOW",
                List.of(),
                List.of(),
                List.of("Only direct endpoint mapping intent is wired in this first skeleton.")
        );
    }
}
