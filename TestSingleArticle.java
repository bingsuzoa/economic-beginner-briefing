import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.economicbriefing.analyzer.openai.OpenAiClient;
import com.economicbriefing.analyzer.openai.prompt.AnalysisPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.SystemPromptBuilder;
import com.economicbriefing.domain.analysis.AudienceProfile;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.domain.article.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestSingleArticle {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("OPENAI_API_KEY not found in environment");
            System.exit(1);
        }

        // 테스트할 기사 (동아일보 2026-07-28)
        Article article = new Article(
            "test-article-1",
            "고금리에 집값·전셋값 10%씩 하락… 다시 오를까",
            "금리 인하 기대감이 커지면서 주택 시장 회복 가능성에 관심이 쏠리고 있다. 하지만 전문가들은 과거와 달리 인구 감소와 공급 과잉으로 집값 반등은 제한적일 것으로 전망한다.",
            "기사 본문 전체 내용 (웹에서 수동으로 복사 필요)",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "https://www.donga.com/news/Economy/article/all/20260728/134382137/2",
            List.of(NewsCategory.HOUSING),
            "ko",
            "동아일보",
            SourceType.MAINSTREAM_MEDIA,
            null
        );

        // 독자 프로필
        AudienceProfile audience = new AudienceProfile(
            "beginner",
            List.of(NewsCategory.HOUSING, NewsCategory.LOAN, NewsCategory.INTEREST_RATE),
            List.of("생활 밀착형 정보 선호", "복잡한 투자 상품보다 실생활 영향에 관심")
        );

        // 프롬프트 생성
        String userPrompt = AnalysisPromptBuilder.build(
            List.of(article),
            LocalDate.now(),
            1,
            audience
        );

        System.out.println("=== System Prompt ===");
        System.out.println(SystemPromptBuilder.SYSTEM_PROMPT);
        System.out.println("\n=== User Prompt ===");
        System.out.println(userPrompt);

        System.out.println("\n기사 본문이 없어서 실제 OpenAI 호출은 생략합니다.");
        System.out.println("웹에서 기사 본문을 복사해서 article 생성 부분에 추가하면 테스트 가능합니다.");
    }
}
