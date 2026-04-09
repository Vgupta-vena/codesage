package com.vena.codesage.graph.service.knowledge;

import com.vena.codesage.dto.EndpointSearchResultDto;
import com.vena.codesage.dto.KnowledgeMode;
import com.vena.codesage.dto.KnowledgeResultItemDto;
import com.vena.codesage.graph.service.EndpointSearchService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EndpointKnowledgeProvider implements KnowledgeProvider {

    private final EndpointSearchService endpointSearchService;

    public EndpointKnowledgeProvider(EndpointSearchService endpointSearchService) {
        this.endpointSearchService = endpointSearchService;
    }

    @Override
    public KnowledgeMode mode() {
        return KnowledgeMode.ENDPOINT;
    }

    @Override
    public List<KnowledgeResultItemDto> search(KnowledgeQuery query) {
        return endpointSearchService.search(query.projectKey(), query.query(), query.limit() * 10, false)
                .stream()
                .map(this::toEndpointItem)
                .toList();
    }

    private KnowledgeResultItemDto toEndpointItem(EndpointSearchResultDto endpoint) {
        return KnowledgeResultItemDto.endpoint(
                endpoint.methodQualifiedName(),
                endpoint.httpMethod() + " " + endpoint.path(),
                endpoint.filePath(),
                endpoint.unresolvedPath() ? "Endpoint mapping with unresolved path" : "Resolved endpoint mapping",
                endpoint.score(),
                List.of()
        );
    }
}
