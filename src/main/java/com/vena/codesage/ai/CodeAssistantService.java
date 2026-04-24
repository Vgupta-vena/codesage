package com.vena.codesage.ai;

import com.vena.codesage.graph.service.CodebaseKnowledgeService;
import com.vena.codesage.graph.service.ReverseTraversalService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class CodeAssistantService {

    private final ChatClient chatClient;
    private final CodeIntelligenceTools tools;
    private final CodebaseKnowledgeService codebaseKnowledgeService;
    private final ReverseTraversalService reverseTraversalService;

    public CodeAssistantService(ChatClient chatClient,
                                CodeIntelligenceTools tools,
                                CodebaseKnowledgeService codebaseKnowledgeService,
                                ReverseTraversalService reverseTraversalService) {
        this.chatClient = chatClient;
        this.tools = tools;
        this.codebaseKnowledgeService = codebaseKnowledgeService;
        this.reverseTraversalService = reverseTraversalService;
    }
}
