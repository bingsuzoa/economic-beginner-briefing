package com.economicbriefing.publisher.notion;

import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.domain.analysis.AnalyzedNews;
import com.economicbriefing.domain.analysis.EconomicTerm;
import com.economicbriefing.domain.briefing.Briefing;

public final class NotionPageBuilder {

    private NotionPageBuilder() {}

    public static List<NotionBlock> buildBriefingBlocks(Briefing briefing) {
        List<NotionBlock> blocks = new ArrayList<>();

        // Overall summary
        blocks.add(NotionBlock.heading2("오늘의 핵심 요약"));
        blocks.addAll(toBulletList(briefing.overallSummary()));

        // News
        blocks.add(NotionBlock.heading2("주요 뉴스"));
        for (AnalyzedNews news : briefing.news()) {
            blocks.addAll(buildNewsBlocks(news));
        }

        // Glossary
        blocks.add(NotionBlock.heading2("경제용어"));
        blocks.addAll(buildGlossaryBlocks(briefing.glossary()));

        // Metadata
        blocks.add(NotionBlock.heading2("브리핑 정보"));
        blocks.add(NotionBlock.paragraph("생성 시각: " + briefing.generatedAt()));
        blocks.add(NotionBlock.paragraph("수집 기사: " + briefing.metadata().collectedArticleCount() + "개"));
        blocks.add(NotionBlock.paragraph("분석 기사: " + briefing.metadata().analyzedArticleCount() + "개"));
        blocks.add(NotionBlock.paragraph("선택 뉴스: " + briefing.metadata().selectedNewsCount() + "개"));

        if (briefing.metadata().modelName() != null) {
            blocks.add(NotionBlock.paragraph("AI 모델: " + briefing.metadata().modelName()));
        }
        if (briefing.metadata().promptVersion() != null) {
            blocks.add(NotionBlock.paragraph("프롬프트 버전: " + briefing.metadata().promptVersion()));
        }

        return blocks;
    }

    private static List<NotionBlock> buildNewsBlocks(AnalyzedNews news) {
        List<NotionBlock> blocks = new ArrayList<>();

        blocks.add(NotionBlock.divider());
        blocks.add(NotionBlock.heading3(news.representativeTitle() + " (중요도 " + news.importance() + "/5)"));
        blocks.add(NotionBlock.paragraph("한 줄 결론: " + news.oneLineSummary()));
        blocks.add(NotionBlock.paragraph("왜 중요한가: " + news.whyImportant()));

        // Explanation paragraphs
        String[] explanationParagraphs = news.explanation().split("\n\n");
        for (String para : explanationParagraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.matches("^\\[.+]$")) {
                String sectionTitle = trimmed.substring(1, trimmed.length() - 1);
                blocks.add(NotionBlock.heading3(sectionTitle));
            } else {
                blocks.add(NotionBlock.paragraph(trimmed));
            }
        }

        // Economic terms
        blocks.add(NotionBlock.heading3("뉴스 안의 경제용어"));
        blocks.addAll(buildGlossaryBlocks(news.economicTerms()));

        // Sources
        blocks.add(NotionBlock.heading3("출처"));
        for (var source : news.sources()) {
            String primaryLabel = source.isPrimary() ? "대표 출처" : "참고 출처";
            String text = primaryLabel + ": " + source.sourceName()
                    + " - " + source.title() + " (" + source.publishedAt() + ")";
            blocks.add(NotionBlock.bulletedListItem(text, source.url()));
        }

        return blocks;
    }

    private static List<NotionBlock> buildGlossaryBlocks(List<EconomicTerm> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of(NotionBlock.paragraph("정리된 경제용어가 없습니다."));
        }

        return terms.stream()
                .map(term -> {
                    String example = term.example() != null ? " 예: " + term.example() : "";
                    return NotionBlock.bulletedListItem(term.term() + ": " + term.explanation() + example);
                })
                .toList();
    }

    private static List<NotionBlock> toBulletList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of(NotionBlock.paragraph("확인된 내용이 없습니다."));
        }
        return values.stream()
                .map(NotionBlock::bulletedListItem)
                .toList();
    }
}
