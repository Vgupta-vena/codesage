package com.vena.codesage.integration.confluence;

import com.vena.codesage.dto.KnowledgeSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfluenceKnowledgeServiceTest {

    private ConfluenceClient confluenceClient;
    private ConfluenceSignalExtractor signalExtractor;
    private ConfluenceProperties properties;
    private ConfluenceKnowledgeService service;

    @BeforeEach
    void setUp() {
        confluenceClient = mock(ConfluenceClient.class);
        signalExtractor = new ConfluenceSignalExtractor();
        properties = new ConfluenceProperties(
                true,
                "https://example.atlassian.net",
                "user@example.com",
                "token",
                "PAY",
                5000,
                10000,
                10
        );

        service = new ConfluenceKnowledgeService(confluenceClient, properties, signalExtractor);
    }

    @Test
    void shouldConvertPagesToKnowledgeItemsWithSignals() {
        var page = new ConfluencePage(
                "12345",
                "Payment callback design",
                "PAY",
                "https://example.atlassian.net/wiki/pages/viewpage.action?pageId=12345",
                "Short excerpt",
                """
                The flow enters through /payments/callback.
                PaymentOrchestrator coordinates the process.
                Main implementation is com.vena.payment.PaymentFlowService.
                """
        );

        when(confluenceClient.searchPages("payment callback", 5)).thenReturn(List.of(page));

        var results = service.search("payment callback", 5);

        assertEquals(1, results.size());

        var item = results.getFirst();
        assertEquals("DOCUMENT", item.resultType());
        assertEquals(KnowledgeSourceType.CONFLUENCE, item.sourceType());
        assertEquals("12345", item.sourceId());
        assertEquals("Payment callback design", item.title());
        assertEquals("Confluence | PAY", item.subtitle());
        assertEquals(page.webUrl(), item.location());
        assertNotNull(item.metadata());

        assertEquals("PAY", item.metadata().get("spaceKey"));
        assertEquals(page.webUrl(), item.metadata().get("url"));

        assertTrue(item.metadata().containsKey("endpoints"));
        assertTrue(item.metadata().containsKey("classNames"));
        assertTrue(item.metadata().containsKey("qualifiedNames"));

        assertFalse(item.highlights().isEmpty());
        assertTrue(item.score() >= 60);
    }

    @Test
    void shouldReturnEmptyWhenConfluenceDisabled() {
        var disabledProperties = new ConfluenceProperties(
                false,
                "https://example.atlassian.net",
                "user@example.com",
                "token",
                "PAY",
                5000,
                10000,
                10
        );

        var disabledService = new ConfluenceKnowledgeService(confluenceClient, disabledProperties, signalExtractor);

        var results = disabledService.search("payment callback", 5);

        assertTrue(results.isEmpty());
        verifyNoInteractions(confluenceClient);
    }

    @Test
    void shouldReturnEmptyWhenClientReturnsNoPages() {
        when(confluenceClient.searchPages("nothing", 5)).thenReturn(List.of());

        var results = service.search("nothing", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}