package com.vena.codesage.api;

import com.vena.codesage.ai.CodeAssistantService;
import com.vena.codesage.dto.AskRequest;
import com.vena.codesage.dto.AskResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CodeAssistantService codeAssistantService;

    public ChatController(CodeAssistantService codeAssistantService) {
        this.codeAssistantService = codeAssistantService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return codeAssistantService.ask(request);
    }
}
