package com.vena.codesage.api;

import com.vena.codesage.ai.CodeAssistantService;
import com.vena.codesage.dto.BlastRadiusResponse;
import com.vena.codesage.dto.EndpointMappingResponse;
import com.vena.codesage.dto.ExplainResponse;
import com.vena.codesage.dto.ReachableEndpointsResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/code")
public class CodeIntelligenceController {

    private final CodeAssistantService codeAssistantService;

    public CodeIntelligenceController(CodeAssistantService codeAssistantService) {
        this.codeAssistantService = codeAssistantService;
    }

    @GetMapping("/endpoint-mapping")
    public EndpointMappingResponse endpointMapping(@RequestParam @NotBlank String projectKey,
                                                   @RequestParam @NotBlank String symbol) {
        return codeAssistantService.endpointMappingDetails(projectKey, symbol);
    }

    @GetMapping("/reachable-endpoints")
    public ReachableEndpointsResponse reachableEndpoints(@RequestParam @NotBlank String projectKey,
                                                         @RequestParam @NotBlank String symbol) {
        return codeAssistantService.reachableEndpointsDetails(projectKey, symbol);
    }

    @GetMapping("/explain")
    public ExplainResponse explain(@RequestParam @NotBlank String projectKey,
                                   @RequestParam @NotBlank String symbol) {
        return codeAssistantService.explainImplementationDetails(projectKey, symbol);
    }

    @GetMapping("/blast-radius")
    public BlastRadiusResponse blastRadius(@RequestParam @NotBlank String projectKey,
                                           @RequestParam @NotBlank String symbol) {
        return codeAssistantService.blastRadiusDetails(projectKey, symbol);
    }
}