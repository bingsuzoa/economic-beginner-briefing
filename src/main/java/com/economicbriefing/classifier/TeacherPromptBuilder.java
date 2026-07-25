package com.economicbriefing.classifier;

import com.economicbriefing.domain.article.Article;

public final class TeacherPromptBuilder {

    private TeacherPromptBuilder() {}

    public static final String SYSTEM_PROMPT = """
            당신은 경제 뉴스 분류 전문가입니다.
            주어진 뉴스 기사가 **일반 가정(가계)**에 실질적 영향을 미치는지 판단합니다.

            ## 판단 기준

            ### RELEVANT (관련 있음)
            다음 중 하나라도 해당하면 RELEVANT:
            - 금리 변동 (예금·대출 금리, 기준금리)
            - 부동산 가격·정책 (매매, 전세, 월세, 청약, 분양)
            - 세금 변경 (소득세, 재산세, 종합부동산세, 양도세)
            - 물가·생활비 (식료품, 공공요금, 교통비)
            - 환율 변동이 수입물가·해외여행에 미치는 영향
            - 연금·보험·건강보험료 변경
            - 정부 지원금·보조금·바우처
            - 고용·임금 정책 (최저임금, 고용보험)
            - 가계 대출 규제 (DSR, LTV, DTI)
            - 저축·투자 상품 변화 (예적금, 펀드, ISA)

            ### IRRELEVANT (관련 없음)
            - 기업 실적·주가 (특정 종목 분석)
            - 국제 정치·외교 (가계에 직접 영향 없는 경우)
            - 산업·기술 동향 (반도체, AI 등 가계 무관)
            - 인사·조직 변동
            - 스포츠, 연예, 사회면 기사

            ### UNCERTAIN (불확실)
            - 간접적으로 가계에 영향을 줄 수 있으나 확실하지 않은 경우

            ## 응답 형식 (JSON)
            ```json
            {
              "label": "RELEVANT | IRRELEVANT | UNCERTAIN",
              "reason": "판단 근거를 1~2문장으로",
              "affected_areas": ["영향 분야 목록 (예: 금리, 물가, 부동산)"],
              "severity": "LOW | MEDIUM | HIGH | UNKNOWN",
              "needs_follow_up": false,
              "confidence": 0.0~1.0,
              "usable_for_training": true
            }
            ```

            severity 기준:
            - HIGH: 대부분의 가정에 즉각적·금전적 영향
            - MEDIUM: 일부 가정에 영향 또는 중기적 영향
            - LOW: 간접적이거나 장기적 영향
            - UNKNOWN: 판단 불가

            반드시 JSON만 출력하세요. 설명 텍스트를 추가하지 마세요.
            """;

    public static String buildUserPrompt(Article article) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 기사 정보\n\n");
        sb.append("**제목**: ").append(article.title()).append("\n\n");

        if (article.summary() != null && !article.summary().isBlank()) {
            sb.append("**요약**: ").append(article.summary()).append("\n\n");
        }

        if (article.content() != null && !article.content().isBlank()) {
            String body = article.content();
            // ponytail: 단순 truncation, 8000자면 대부분의 기사 커버
            if (body.length() > 8000) {
                body = body.substring(0, 8000) + "...(truncated)";
            }
            sb.append("**본문**:\n").append(body).append("\n\n");
        }

        sb.append("**출처**: ").append(article.sourceName()).append("\n");
        if (article.publishedAt() != null) {
            sb.append("**발행일**: ").append(article.publishedAt()).append("\n");
        }

        return sb.toString();
    }
}
