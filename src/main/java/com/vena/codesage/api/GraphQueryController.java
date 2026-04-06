package com.vena.codesage.api;

import com.vena.codesage.dto.*;
import com.vena.codesage.graph.service.CallGraphService;
import com.vena.codesage.graph.service.CodeEntityService;
import com.vena.codesage.graph.service.ReverseTraversalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
public class GraphQueryController {

    private final CallGraphService callGraphService;
    private final CodeEntityService codeEntityService;
    private final ReverseTraversalService reverseTraversalService;

    public GraphQueryController(CallGraphService callGraphService,
                                CodeEntityService codeEntityService, ReverseTraversalService reverseTraversalService) {
        this.callGraphService = callGraphService;
        this.codeEntityService = codeEntityService;
        this.reverseTraversalService = reverseTraversalService;
    }

    @GetMapping("/callers")
    public List<String> callers(@RequestParam String projectKey,
                                @RequestParam String method,
                                @RequestParam(defaultValue = "false") boolean excludeTests,
                                @RequestParam(defaultValue = "false") boolean excludeAnonymous,
                                @RequestParam(required = false) Integer limit) {
        return callGraphService.findDirectCallers(
                projectKey,
                method,
                excludeTests,
                excludeAnonymous,
                limit
        );
    }

    @GetMapping("/callees")
    public List<String> callees(@RequestParam String projectKey,
                                @RequestParam String method,
                                @RequestParam(defaultValue = "false") boolean excludeTests,
                                @RequestParam(defaultValue = "false") boolean excludeAnonymous,
                                @RequestParam(required = false) Integer limit) {
        return callGraphService.findDirectCallees(
                projectKey,
                method,
                excludeTests,
                excludeAnonymous,
                limit
        );
    }

    @GetMapping("/endpoints-reaching")
    public List<String> endpointsReaching(@RequestParam String projectKey,
                                          @RequestParam String method,
                                          @RequestParam(defaultValue = "true") boolean excludeTests,
                                          @RequestParam(defaultValue = "true") boolean excludeAnonymous,
                                          @RequestParam(required = false) Integer limit) {
        return callGraphService.findEndpointsReachingMethod(
                projectKey,
                method,
                excludeTests,
                excludeAnonymous,
                limit
        );
    }

    @GetMapping("/blast-radius")
    public BlastRadiusDto blastRadius(@RequestParam String projectKey,
                                      @RequestParam String method,
                                      @RequestParam(defaultValue = "3") int depth,
                                      @RequestParam(defaultValue = "true") boolean excludeTests,
                                      @RequestParam(defaultValue = "true") boolean excludeAnonymous,
                                      @RequestParam(required = false) Integer limit) {
        return callGraphService.computeBlastRadius(
                projectKey,
                method,
                depth,
                excludeTests,
                excludeAnonymous,
                limit
        );
    }

    @GetMapping("/describe")
    public EntitySummaryDto describe(@RequestParam String projectKey,
                                     @RequestParam String qualifiedName,
                                     @RequestParam(defaultValue = "true") boolean excludeTests,
                                     @RequestParam(defaultValue = "true") boolean excludeAnonymous,
                                     @RequestParam(required = false) Integer limit) {
        return codeEntityService.describeEntity(
                projectKey,
                qualifiedName,
                excludeTests,
                excludeAnonymous,
                limit
        );
    }

    @GetMapping("/search")
    public List<CodeEntitySearchResultDto> search(@RequestParam String projectKey,
                                                  @RequestParam("q") String query,
                                                  @RequestParam(defaultValue = "false") boolean excludeTests,
                                                  @RequestParam(defaultValue = "false") boolean excludeAnonymous,
                                                  @RequestParam(required = false) Integer limit) {
        return codeEntityService.search(
                projectKey,
                query,
                excludeTests,
                excludeAnonymous,
                limit
        );
    }

    @GetMapping("/paths-to-touchpoints")
    public List<CallPathDto> pathsToTouchpoints(@RequestParam String projectKey,
                                                @RequestParam String method,
                                                @RequestParam(defaultValue = "6") int depth,
                                                @RequestParam(defaultValue = "true") boolean excludeTests,
                                                @RequestParam(defaultValue = "true") boolean excludeAnonymous,
                                                @RequestParam(required = false) Integer limit) {
        return callGraphService.findPathsToTouchpoints(
                projectKey,
                method,
                depth,
                excludeTests,
                excludeAnonymous,
                limit
        );
    }

    @GetMapping("/trace")
    public TraceResponseDto trace(@RequestParam String projectKey,
                                  @RequestParam String qualifiedName,
                                  @RequestParam(defaultValue = "BOTH") TraceDirection direction,
                                  @RequestParam(defaultValue = "3") int depth,
                                  @RequestParam(defaultValue = "50") int limit) {
        return reverseTraversalService.trace(
                projectKey,
                qualifiedName,
                direction,
                depth,
                limit
        );
    }

    @GetMapping("/execution-paths")
    public ExecutionGraphDto executionPaths(
            @RequestParam String projectKey,
            @RequestParam String method,
            @RequestParam(defaultValue = "6") int depth,
            @RequestParam(defaultValue = "true") boolean excludeTests,
            @RequestParam(defaultValue = "true") boolean excludeAnonymous
    ) {
        return callGraphService.computeExecutionPaths(
                projectKey,
                method,
                depth,
                excludeTests,
                excludeAnonymous
        );
    }
}