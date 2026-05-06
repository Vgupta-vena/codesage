package com.vena.sageorchestrator.codesage;

import com.vena.sageorchestrator.codesage.dto.EndpointMappingResponse;

public interface CodeSageFacade {
    EndpointMappingResponse endpointMapping(String projectKey, String symbol);
}
