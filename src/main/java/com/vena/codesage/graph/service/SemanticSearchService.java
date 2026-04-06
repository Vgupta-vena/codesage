package com.vena.codesage.graph.service;

import com.vena.codesage.graph.model.CodeEntity;
import com.vena.codesage.graph.repo.CodeEntityRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class SemanticSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final CodeEntityRepository codeEntityRepository;

    public SemanticSearchService(CodeEntityRepository codeEntityRepository) {
        this.codeEntityRepository = codeEntityRepository;
    }

    public List<CodeEntity> search(Long scanRunId,
                                   String query,
                                   Integer limit,
                                   Boolean excludeTests,
                                   Boolean excludeAnonymous,
                                   String entityType) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        int effectiveLimit = normalizeLimit(limit);
        boolean effectiveExcludeTests = excludeTests == null || excludeTests;
        boolean effectiveExcludeAnonymous = excludeAnonymous == null || excludeAnonymous;

        return codeEntityRepository.findByScanRunId(scanRunId)
                .stream()
                .filter(entity -> matchesEntityType(entity, entityType))
                .filter(entity -> !effectiveExcludeTests || !isTestEntity(entity))
                .filter(entity -> !effectiveExcludeAnonymous || !isAnonymousEntity(entity))
                .map(entity -> new ScoredEntity(entity, score(entity, normalizedQuery)))
                .filter(scored -> scored.score() > 0.0)
                .sorted(Comparator
                        .comparingDouble(ScoredEntity::score)
                        .reversed()
                        .thenComparing(scored -> safe(scored.entity().getQualifiedName())))
                .limit(effectiveLimit)
                .map(ScoredEntity::entity)
                .toList();
    }

    private double score(CodeEntity entity, String query) {
        double score = 0.0;

        String qualifiedName = safe(entity.getQualifiedName());
        String simpleName = safe(entity.getSimpleName());
        String declaringType = safe(entity.getDeclaringType());
        String summary = safe(entity.getSummary());
        String entityType = safe(entity.getEntityType());
        String filePath = safe(entity.getFilePath());
        String signature = safe(entity.getSignature());

        String lastQualifiedSegment = extractLastSegment(qualifiedName);
        String declaringTypeShort = extractLastSegment(declaringType);

        if (qualifiedName.equals(query)) {
            score += 100.0;
        }

        if (declaringType.equals(query)) {
            score += 90.0;
        }

        if (simpleName.equals(query)) {
            score += 85.0;
        }

        if (lastQualifiedSegment.equals(query)) {
            score += 80.0;
        }

        if (declaringTypeShort.equals(query)) {
            score += 75.0;
        }

        if (qualifiedName.contains("." + query + ".")) {
            score += 40.0;
        }

        if (qualifiedName.endsWith("." + query)) {
            score += 35.0;
        }

        if (declaringType.contains(query)) {
            score += 30.0;
        }

        if (simpleName.contains(query)) {
            score += 25.0;
        }

        if (qualifiedName.contains(query)) {
            score += 20.0;
        }

        if (summary.contains(query)) {
            score += 12.0;
        }

        if (signature.contains(query)) {
            score += 8.0;
        }

        if (entityType.contains(query)) {
            score += 5.0;
        }

        if (filePath.contains(query)) {
            score += 4.0;
        }

        for (String token : query.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }

            if (lastQualifiedSegment.contains(token)) {
                score += 12.0;
            }

            if (declaringTypeShort.contains(token)) {
                score += 10.0;
            }

            if (declaringType.contains(token)) {
                score += 8.0;
            }

            if (simpleName.contains(token)) {
                score += 7.0;
            }

            if (qualifiedName.contains(token)) {
                score += 6.0;
            }

            if (summary.contains(token)) {
                score += 4.0;
            }
        }

        if (isProductionEntity(entity)) {
            score += 3.0;
        }

        if (isSynthetic(entity)) {
            score -= 15.0;
        }

        if (isAnonymousEntity(entity)) {
            score -= 30.0;
        }

        if (isTestEntity(entity)) {
            score -= 20.0;
        }

        return score;
    }

    private boolean matchesEntityType(CodeEntity entity, String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return true;
        }
        return safe(entity.getEntityType()).equals(normalize(entityType));
    }

    private boolean isTestEntity(CodeEntity entity) {
        String filePath = safe(entity.getFilePath());
        String qualifiedName = safe(entity.getQualifiedName());
        String declaringType = safe(entity.getDeclaringType());

        return filePath.contains("/src/test/")
                || filePath.contains("\\src\\test\\")
                || qualifiedName.contains(".test.")
                || declaringType.contains(".test.")
                || qualifiedName.endsWith("test")
                || declaringType.endsWith("test");
    }

    private boolean isProductionEntity(CodeEntity entity) {
        String filePath = safe(entity.getFilePath());
        return filePath.contains("/src/main/")
                || filePath.contains("\\src\\main\\");
    }

    private boolean isSynthetic(CodeEntity entity) {
        String qualifiedName = safe(entity.getQualifiedName());
        String signature = safe(entity.getSignature());

        return qualifiedName.contains(".<clinit>")
                || qualifiedName.contains(".<obinit>")
                || signature.contains("<clinit>")
                || signature.contains("<obinit>");
    }

    private boolean isAnonymousEntity(CodeEntity entity) {
        String qualifiedName = safe(entity.getQualifiedName());
        String declaringType = safe(entity.getDeclaringType());
        String filePath = safe(entity.getFilePath());

        return qualifiedName.contains("<anonymous class>")
                || declaringType.contains("<anonymous class>")
                || filePath.contains("$1")
                || filePath.contains("$2");
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String extractLastSegment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int index = value.lastIndexOf('.');
        if (index < 0 || index == value.length() - 1) {
            return value;
        }
        return value.substring(index + 1);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record ScoredEntity(CodeEntity entity, double score) {
    }
}