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
        blocks.add(NotionBlock.heading3(news.easyTitle() + " (중요도 " + news.importance() + "/5)"));

        // 세 줄 핵심 정리
        if (news.threeLineSummary() != null && !news.threeLineSummary().isEmpty()) {
            blocks.add(NotionBlock.heading3("세 줄 핵심 정리"));
            blocks.addAll(toBulletList(news.threeLineSummary()));
        }

        // 무슨 일이 있었나요?
        if (news.whatHappened() != null && !news.whatHappened().isBlank()) {
            blocks.add(NotionBlock.heading3("무슨 일이 있었나요?"));
            blocks.add(NotionBlock.paragraph(news.whatHappened()));
        }

        // 왜 이런 일이 생겼나요?
        if (news.whyItHappened() != null && !news.whyItHappened().isBlank()) {
            blocks.add(NotionBlock.heading3("왜 이런 일이 생겼나요?"));
            blocks.add(NotionBlock.paragraph(news.whyItHappened()));
        }

        // 경제 영향
        if (news.economicImpact() != null && !news.economicImpact().isBlank()) {
            blocks.add(NotionBlock.heading3("경제에는 어떤 영향을 주나요?"));
            blocks.add(NotionBlock.paragraph(news.economicImpact()));
        }

        // 생활 영향
        if (news.householdImpact() != null && !news.householdImpact().isBlank()) {
            blocks.add(NotionBlock.heading3("우리 생활에는 어떤 영향이 있나요?"));
            blocks.add(NotionBlock.paragraph(news.householdImpact()));
        }

        // 영향 대상
        if (news.affectedPeople() != null && !news.affectedPeople().isEmpty()) {
            blocks.add(NotionBlock.heading3("누가 영향을 받나요?"));
            blocks.addAll(toBulletList(news.affectedPeople()));
        }

        // 긍정적 영향
        if (news.positiveImpact() != null && !news.positiveImpact().isBlank()) {
            blocks.add(NotionBlock.heading3("긍정적인 영향"));
            blocks.add(NotionBlock.paragraph(news.positiveImpact()));
        }

        // 부정적 영향
        if (news.negativeImpact() != null && !news.negativeImpact().isBlank()) {
            blocks.add(NotionBlock.heading3("부정적인 영향"));
            blocks.add(NotionBlock.paragraph(news.negativeImpact()));
        }

        // 확인할 것
        if (news.actionItems() != null && !news.actionItems().isEmpty()) {
            blocks.add(NotionBlock.heading3("지금 무엇을 확인하면 좋을까요?"));
            blocks.addAll(toBulletList(news.actionItems()));
        }

        // 용어 설명
        if (news.terms() != null && !news.terms().isEmpty()) {
            blocks.add(NotionBlock.heading3("어려운 용어 설명"));
            blocks.addAll(buildGlossaryBlocks(news.terms()));
        }

        // 불확실성
        if (news.uncertainties() != null && !news.uncertainties().isEmpty()) {
            blocks.add(NotionBlock.heading3("불확실성 구분"));
            blocks.addAll(toBulletList(news.uncertainties()));
        }

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
