package com.economicbriefing.classifier;

import java.util.List;

import com.economicbriefing.domain.article.Article;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "true", matchIfMissing = true)
public class MockTeacherClassifier implements TeacherClassifier {

    @Override
    public TeacherLabelResponse classify(Article article) {
        return new TeacherLabelResponse(
                "RELEVANT",
                "Mock: 모든 기사를 RELEVANT로 분류",
                List.of("기타"),
                "MEDIUM",
                false,
                0.9,
                false
        );
    }
}
