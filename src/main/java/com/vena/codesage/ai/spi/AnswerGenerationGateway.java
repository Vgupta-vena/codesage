package com.vena.codesage.ai.spi;

public interface AnswerGenerationGateway {
    String generateAnswer(String systemPrompt, String userPrompt);
}