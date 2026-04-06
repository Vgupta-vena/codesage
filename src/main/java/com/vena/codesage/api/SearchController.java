package com.vena.codesage.api;

import com.vena.codesage.dto.KnowledgeMode;
import com.vena.codesage.dto.KnowledgeResponseDto;
import com.vena.codesage.graph.service.CodebaseKnowledgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final CodebaseKnowledgeService codebaseKnowledgeService;

    public SearchController(CodebaseKnowledgeService codebaseKnowledgeService) {
        this.codebaseKnowledgeService = codebaseKnowledgeService;
    }

    @GetMapping
    public KnowledgeResponseDto search(@RequestParam String projectKey,
                                       @RequestParam("q") String query,
                                       @RequestParam(required = false) Integer limit,
                                       @RequestParam(defaultValue = "false") boolean debug,
                                       @RequestParam(defaultValue = "AUTO") KnowledgeMode mode,
                                       @RequestParam(defaultValue = "true") boolean collapse,
                                       @RequestParam(required = false) String entityType) {
        return codebaseKnowledgeService.query(
                projectKey,
                query,
                limit,
                debug,
                mode,
                collapse,
                entityType
        );
    }
}