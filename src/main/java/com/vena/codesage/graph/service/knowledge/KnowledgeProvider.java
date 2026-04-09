package com.vena.codesage.graph.service.knowledge;

import com.vena.codesage.dto.KnowledgeMode;
import com.vena.codesage.dto.KnowledgeResultItemDto;

import java.util.List;

public interface KnowledgeProvider {

    KnowledgeMode mode();

    List<KnowledgeResultItemDto> search(com.vena.codesage.graph.service.knowledge.KnowledgeQuery query);
}
