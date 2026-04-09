package com.vena.codesage.graph.service.knowledge;

import com.vena.codesage.dto.KnowledgeMode;
import com.vena.codesage.dto.KnowledgeResultItemDto;
import com.vena.codesage.dto.SemanticSearchResultDto;
import com.vena.codesage.dto.TraceDirection;
import com.vena.codesage.graph.service.ReverseTraversalService;
import com.vena.codesage.graph.service.SemanticDocumentBuilderService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SemanticKnowledgeProvider implements KnowledgeProvider {

    private final SemanticDocumentBuilderService semanticDocumentBuilderService;
    private final ReverseTraversalService reverseTraversalService;

    public SemanticKnowledgeProvider(SemanticDocumentBuilderService semanticDocumentBuilderService,
                                     ReverseTraversalService reverseTraversalService) {
        this.semanticDocumentBuilderService = semanticDocumentBuilderService;
        this.reverseTraversalService = reverseTraversalService;
    }

    @Override
    public KnowledgeMode mode() {
        return KnowledgeMode.SEMANTIC;
    }

    @Override
    public List<KnowledgeResultItemDto> search(KnowledgeQuery query) {
        return semanticDocumentBuilderService.searchActiveScan(
                        query.projectKey(),
                        query.query(),
                        query.limit() * 4,
                        true,
                        true,
                        query.entityType(),
                        query.debug()
                )
                .stream()
                .map(item -> toSemanticItem(query.projectKey(), item))
                .toList();
    }

    private KnowledgeResultItemDto toSemanticItem(String projectKey, SemanticSearchResultDto semantic) {
        return KnowledgeResultItemDto.semantic(
                semantic.entityQualifiedName(),
                semantic.entityType() + " | " + semantic.docType(),
                semantic.filePath(),
                semantic.preview(),
                semantic.score(),
                topUpstreamHighlights(projectKey, semantic.entityQualifiedName()),
                Map.of(
                        "entityType", semantic.entityType(),
                        "docType", semantic.docType()
                )
        );
    }

    private List<String> topUpstreamHighlights(String projectKey, String qualifiedName) {
        try {
            var trace = reverseTraversalService.trace(projectKey, qualifiedName, TraceDirection.UPSTREAM, 2, 10);
            return trace.upstream().stream()
                    .map(node -> node.qualifiedName())
                    .limit(3)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
