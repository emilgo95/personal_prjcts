package com.agriaudit.catalog;

import java.util.List;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record RegulationRefDto(String code, String title, String jurisdiction, String sourceUrl) {
    }

    public record RegulationDto(
            Long id,
            String code,
            String title,
            String jurisdiction,
            String scopeArea,
            String summary,
            String keyObligations,
            String sourceUrl) {
    }

    public record ControlPointDto(
            Long id,
            String ref,
            String title,
            String description,
            String level,
            String guidance,
            String evidenceExamples,
            String applicableScopes,
            String chapterCode,
            String chapterTitle,
            List<RegulationRefDto> regulations) {
    }

    public record ChapterDto(
            Long id,
            String code,
            String title,
            String description,
            int sortOrder,
            List<ControlPointDto> controlPoints) {
    }

    public record StandardSummaryDto(
            Long id,
            String code,
            String version,
            String name,
            String ownerBody,
            String category,
            String description,
            String officialUrl,
            int chapterCount,
            int controlPointCount) {
    }

    public record StandardDetailDto(
            Long id,
            String code,
            String version,
            String name,
            String ownerBody,
            String category,
            String description,
            String scoringRule,
            String officialUrl,
            List<ChapterDto> chapters) {
    }
}
