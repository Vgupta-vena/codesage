package com.vena.sageorchestrator.codesage;

import com.vena.sageorchestrator.codesage.dto.EndpointMappingResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CodeSageClient implements CodeSageFacade {

    private final RestClient restClient;

    public CodeSageClient(RestClient.Builder builder, CodeSageProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public EndpointMappingResponse endpointMapping(String projectKey, String symbol) {
        String uri = UriComponentsBuilder
                .fromPath("/api/code/endpoint-mapping")
                .queryParam("projectKey", projectKey)
                .queryParam("symbol", symbol)
                .build()
                .toUriString();

        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(EndpointMappingResponse.class);
    }
}
