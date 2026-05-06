package com.vena.sageorchestrator.api;

import com.vena.sageorchestrator.api.dto.AnswerResponse;
import com.vena.sageorchestrator.api.dto.BrainQueryRequest;
import com.vena.sageorchestrator.query.BrainQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/brain")
public class BrainQueryController {

    private final BrainQueryService brainQueryService;

    public BrainQueryController(BrainQueryService brainQueryService) {
        this.brainQueryService = brainQueryService;
    }

    @PostMapping("/query")
    public AnswerResponse query(@Valid @RequestBody BrainQueryRequest request) {
        return brainQueryService.query(request);
    }
}
