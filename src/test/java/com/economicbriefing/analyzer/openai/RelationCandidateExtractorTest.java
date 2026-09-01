package com.economicbriefing.analyzer.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.prompt.RelationCandidateExtractorPromptBuilder;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;

class RelationCandidateExtractorTest {
    private final Article article = new Article("a1", "금리 기사", "", "연합뉴스",
            ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(), "url", List.of(), "ko",
            "기준금리 인상 가능성이 부각되면서 국고채 금리가 상승했다.");
    private final ArticleAnalysisResponse baseline = new ArticleAnalysisResponse(List.of(
            new ArticleAnalysisResponse.ArticleAnalysis("a1", List.of(new ArticleAnalysisResponse.Issue(
                    "채권시장", List.of(), List.of(), List.of(), List.of(), List.of())))));

    @Test
    void shouldProvideValidStrictSchema() throws Exception {
        new ObjectMapper().readTree(RelationCandidateExtractorPromptBuilder.schemaForArticleCount(1));
    }

    @Test
    void shouldMergeExactSourceRelationsAndRemoveExactDuplicates() {
        var relation = new RelationCandidateExtractor.AtomicRelation("기준금리 인상 가능성", "국고채 금리 상승",
                ArticleAnalysisResponse.RelationType.CAUSE_OR_RESULT,
                ArticleAnalysisResponse.StatementType.FACT, null);
        var response = new RelationCandidateExtractor.Response(List.of(
                new RelationCandidateExtractor.RelationArticle("a1", List.of(
                        new RelationCandidateExtractor.Candidate("채권시장",
                                "기준금리 인상 가능성이 부각되면서 국고채 금리가 상승했다.",
                                List.of(relation, relation))))));

        var merged = RelationCandidateExtractor.merge(response, List.of(article), baseline);

        assertEquals(1, merged.articles().getFirst().issues().getFirst().relations().size());
        assertEquals("기준금리 인상 가능성",
                merged.articles().getFirst().issues().getFirst().relations().getFirst().from());
    }

    @Test
    void shouldRejectEvidenceNotPresentInSource() {
        var relation = new RelationCandidateExtractor.AtomicRelation("기준금리", "국고채 금리",
                ArticleAnalysisResponse.RelationType.CAUSE_OR_RESULT,
                ArticleAnalysisResponse.StatementType.FACT, null);
        var response = new RelationCandidateExtractor.Response(List.of(
                new RelationCandidateExtractor.RelationArticle("a1", List.of(
                        new RelationCandidateExtractor.Candidate("채권시장", "LLM이 바꿔 쓴 근거", List.of(relation))))));

        assertThrows(IllegalArgumentException.class,
                () -> RelationCandidateExtractor.merge(response, List.of(article), baseline));
    }

    @Test
    void shouldDropOnlyInvalidEvidenceAfterRetries() {
        var valid = new RelationCandidateExtractor.AtomicRelation("기준금리 인상 가능성", "국고채 금리 상승",
                ArticleAnalysisResponse.RelationType.CAUSE_OR_RESULT,
                ArticleAnalysisResponse.StatementType.FACT, null);
        var invalid = new RelationCandidateExtractor.AtomicRelation("환율 상승", "주가 하락",
                ArticleAnalysisResponse.RelationType.CAUSE_OR_RESULT,
                ArticleAnalysisResponse.StatementType.FACT, null);
        var response = new RelationCandidateExtractor.Response(List.of(
                new RelationCandidateExtractor.RelationArticle("a1", List.of(
                        new RelationCandidateExtractor.Candidate("채권시장",
                                "기준금리 인상 가능성이 부각되면서 국고채 금리가 상승했다.", List.of(valid)),
                        new RelationCandidateExtractor.Candidate("채권시장", "원문에 없는 근거", List.of(invalid))))));

        var merged = RelationCandidateExtractor.mergeDroppingInvalidEvidence(response, List.of(article), baseline);

        assertEquals(List.of("기준금리 인상 가능성"), merged.articles().getFirst().issues().getFirst().relations()
                .stream().map(ArticleAnalysisResponse.Relation::from).toList());
    }

    @Test
    void shouldFailWhenAllEvidenceIsInvalid() {
        var invalid = new RelationCandidateExtractor.AtomicRelation("환율 상승", "주가 하락",
                ArticleAnalysisResponse.RelationType.CAUSE_OR_RESULT,
                ArticleAnalysisResponse.StatementType.FACT, null);
        var response = new RelationCandidateExtractor.Response(List.of(
                new RelationCandidateExtractor.RelationArticle("a1", List.of(
                        new RelationCandidateExtractor.Candidate("채권시장", "원문에 없는 근거", List.of(invalid))))));

        assertThrows(IllegalArgumentException.class,
                () -> RelationCandidateExtractor.mergeDroppingInvalidEvidence(response, List.of(article), baseline));
    }
}
