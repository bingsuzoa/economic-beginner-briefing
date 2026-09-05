package com.economicbriefing.analyzer.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.economicbriefing.exception.AnalyzeException;
import com.economicbriefing.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiNewsAnalyzerSelectionTest {

    @Test
    void acceptsOneBasedArticleIndexes() {
        var response = analyzer().parseSelection("""
                {"selectedArticleIndexes":[2,5]}
                """, 5, 5);

        assertEquals(java.util.List.of(2, 5), response.selectedArticleIndexes());
    }

    @Test
    void acceptsAnEmptySelection() {
        var response = analyzer().parseSelection("{\"selectedArticleIndexes\":[]}", 5, 5);

        assertEquals(java.util.List.of(), response.selectedArticleIndexes());
    }

    @Test
    void rejectsOutOfRangeOrDuplicateIndexesAsRetryableSelectionErrors() {
        assertSelectionError("{\"selectedArticleIndexes\":[0]}");
        assertSelectionError("{\"selectedArticleIndexes\":[6]}");
        assertSelectionError("{\"selectedArticleIndexes\":[2,2]}");
        assertSelectionError("{\"selectedArticleIndexes\":[1,2,3]}", 2);
    }

    private void assertSelectionError(String response) {
        assertSelectionError(response, 5);
    }

    private void assertSelectionError(String response, int maxSelectedNews) {
        var error = assertThrows(AnalyzeException.class,
                () -> analyzer().parseSelection(response, 5, maxSelectedNews));
        assertEquals(ErrorCode.ANALYZE_SELECTION_ERROR, error.getErrorCode());
    }

    private OpenAiNewsAnalyzer analyzer() {
        return new OpenAiNewsAnalyzer(mock(OpenAiClient.class), new ObjectMapper(), null, null,
                mock(ArticleBodyFetcher.class));
    }
}
