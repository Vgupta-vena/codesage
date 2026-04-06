package com.vena.codesage.ingestion.jobs;

import com.vena.codesage.graph.model.CallEdge;
import com.vena.codesage.graph.model.CodeEntity;
import com.vena.codesage.graph.model.EndpointMapping;
import com.vena.codesage.graph.model.FlowEdge;
import com.vena.codesage.graph.model.Touchpoint;
import com.vena.codesage.graph.repo.CallEdgeRepository;
import com.vena.codesage.graph.repo.CodeEntityRepository;
import com.vena.codesage.graph.repo.EndpointMappingRepository;
import com.vena.codesage.graph.repo.FlowEdgeRepository;
import com.vena.codesage.graph.repo.TouchpointRepository;
import com.vena.codesage.graph.service.SummaryService;
import com.vena.codesage.ingestion.IngestionRequest;
import com.vena.codesage.ingestion.IngestionResult;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SemanticEnrichmentJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SemanticEnrichmentJob.class);
    private static final int BATCH_SIZE = 1000;

    private final CodeEntityRepository codeEntityRepository;
    private final CallEdgeRepository callEdgeRepository;
    private final EndpointMappingRepository endpointMappingRepository;
    private final TouchpointRepository touchpointRepository;
    private final FlowEdgeRepository flowEdgeRepository;
    private final SummaryService summaryService;

    public SemanticEnrichmentJob(CodeEntityRepository codeEntityRepository,
                                 CallEdgeRepository callEdgeRepository,
                                 EndpointMappingRepository endpointMappingRepository,
                                 TouchpointRepository touchpointRepository,
                                 FlowEdgeRepository flowEdgeRepository,
                                 SummaryService summaryService) {
        this.codeEntityRepository = codeEntityRepository;
        this.callEdgeRepository = callEdgeRepository;
        this.endpointMappingRepository = endpointMappingRepository;
        this.touchpointRepository = touchpointRepository;
        this.flowEdgeRepository = flowEdgeRepository;
        this.summaryService = summaryService;
    }

    @Transactional
    public IngestionResult run(IngestionRequest request) {
        Long scanRunId = request.scanRunId();

        LOGGER.info("Starting semantic enrichment scanRunId={}", scanRunId);

        List<CodeEntity> entities = codeEntityRepository.findByScanRunId(scanRunId);
        List<CallEdge> callEdges = callEdgeRepository.findByScanRunId(scanRunId);
        List<EndpointMapping> endpointMappings = endpointMappingRepository.findByScanRunId(scanRunId);
        List<Touchpoint> touchpoints = touchpointRepository.findByScanRunId(scanRunId);
        List<FlowEdge> flowEdges = flowEdgeRepository.findByScanRunId(scanRunId);

        LOGGER.info(
                "Loaded enrichment data scanRunId={} entities={} callEdges={} endpoints={} touchpoints={} flowEdges={}",
                scanRunId,
                entities.size(),
                callEdges.size(),
                endpointMappings.size(),
                touchpoints.size(),
                flowEdges.size()
        );

        SummaryService.SummaryIndex index = buildIndex(callEdges, endpointMappings, touchpoints, flowEdges);

        int updatedCount = 0;

        for (CodeEntity entity : entities) {
            if (entity.getSummary() == null || entity.getSummary().isBlank()) {
                String summary = summaryService.buildSeedSummary(entity, index);
                if (summary != null && !summary.isBlank()) {
                    entity.setSummary(summary);
                    updatedCount++;
                }
            }
        }

        for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, entities.size());
            codeEntityRepository.saveAll(entities.subList(i, end));
            LOGGER.info("Saved semantic enrichment batch scanRunId={} from={} to={}", scanRunId, i, end);
        }

        LOGGER.info("Completed semantic enrichment scanRunId={} updatedCount={}", scanRunId, updatedCount);

        return new IngestionResult("semantic_enrichment", updatedCount);
    }

    private SummaryService.SummaryIndex buildIndex(List<CallEdge> callEdges,
                                                   List<EndpointMapping> endpointMappings,
                                                   List<Touchpoint> touchpoints,
                                                   List<FlowEdge> flowEdges) {
        Map<String, List<CallEdge>> incomingCallsByCallee = callEdges.stream()
                .filter(edge -> edge.getCalleeQualifiedName() != null && !edge.getCalleeQualifiedName().isBlank())
                .collect(Collectors.groupingBy(CallEdge::getCalleeQualifiedName));

        Map<String, List<CallEdge>> outgoingCallsByCaller = callEdges.stream()
                .filter(edge -> edge.getCallerQualifiedName() != null && !edge.getCallerQualifiedName().isBlank())
                .collect(Collectors.groupingBy(CallEdge::getCallerQualifiedName));

        Map<String, List<EndpointMapping>> endpointsByMethod = endpointMappings.stream()
                .filter(endpoint -> endpoint.getMethodQualifiedName() != null && !endpoint.getMethodQualifiedName().isBlank())
                .collect(Collectors.groupingBy(EndpointMapping::getMethodQualifiedName));

        Map<String, List<Touchpoint>> touchpointsByCaller = touchpoints.stream()
                .filter(tp -> tp.getCallerQualifiedName() != null && !tp.getCallerQualifiedName().isBlank())
                .collect(Collectors.groupingBy(Touchpoint::getCallerQualifiedName));

        Map<String, List<FlowEdge>> outgoingFlowsBySource = flowEdges.stream()
                .filter(flow -> flow.getSourceMethodQualifiedName() != null && !flow.getSourceMethodQualifiedName().isBlank())
                .collect(Collectors.groupingBy(FlowEdge::getSourceMethodQualifiedName));

        Map<String, List<FlowEdge>> incomingFlowsBySink = flowEdges.stream()
                .filter(flow -> flow.getSinkMethodQualifiedName() != null && !flow.getSinkMethodQualifiedName().isBlank())
                .collect(Collectors.groupingBy(FlowEdge::getSinkMethodQualifiedName));

        return new SummaryService.SummaryIndex(
                incomingCallsByCallee,
                outgoingCallsByCaller,
                endpointsByMethod,
                touchpointsByCaller,
                outgoingFlowsBySource,
                incomingFlowsBySink
        );
    }
}