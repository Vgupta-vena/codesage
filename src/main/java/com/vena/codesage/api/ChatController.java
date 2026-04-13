package com.vena.codesage.api;

import com.vena.codesage.ai.CodeAssistantService;
import com.vena.codesage.dto.AskRequest;
import com.vena.codesage.dto.AskResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CodeAssistantService codeAssistantService;

    public ChatController(CodeAssistantService codeAssistantService) {
        this.codeAssistantService = codeAssistantService;
    }
}