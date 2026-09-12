#!/bin/bash

# 프롬프트 v4 테스트 스크립트
# 실제 기사로 OpenAI API를 호출하여 결과를 확인합니다

echo "=== 경제 뉴스 분석 프롬프트 v4 테스트 ==="
echo ""
echo "SystemPromptBuilder에서 프롬프트를 확인합니다..."
echo ""

grep -A 200 'public static final String SYSTEM_PROMPT' src/main/java/com/economicbriefing/analyzer/openai/prompt/SystemPromptBuilder.java | head -150

echo ""
echo "=== 테스트용 기사 정보 ==="
echo "제목: 지난달 주담보대출 금리 연 4.36%… 2년7개월만에 최고"
echo "출처: 동아일보"
echo "URL: https://www.donga.com/news/Economy/article/all/20260728/134382137/2"
echo ""
echo "기사 본문을 직접 입력하시려면 아래 파일을 수정하세요:"
echo "TestSingleArticle.java"
echo ""
echo "또는 Admin API로 전체 파이프라인을 실행하세요:"
echo "curl -X POST http://localhost:3000/api/admin/runs"
