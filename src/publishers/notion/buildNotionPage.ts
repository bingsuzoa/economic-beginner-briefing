import type { AnalyzedNews, EconomicTerm, ImpactScore, TargetAudience } from "../../domain/analyzedNews.js";
import type { Briefing } from "../../domain/briefing.js";
import type { NotionBlock, NotionRichText } from "./notionTypes.js";

const MAX_RICH_TEXT_CONTENT_LENGTH = 2_000;

export function buildNotionBriefingBlocks(briefing: Briefing): NotionBlock[] {
  const blocks: NotionBlock[] = [];

  blocks.push(heading2("오늘의 핵심 요약"));
  blocks.push(...toBulletList(briefing.overallSummary));

  blocks.push(heading2("주요 뉴스"));
  for (const news of briefing.news) {
    blocks.push(...buildNewsBlocks(news));
  }

  blocks.push(heading2("경제용어"));
  blocks.push(...buildGlossaryBlocks(briefing.glossary));

  blocks.push(heading2("브리핑 정보"));
  blocks.push(
    paragraph(`생성 시각: ${briefing.generatedAt}`),
    paragraph(`수집 기사: ${briefing.metadata.collectedArticleCount}개`),
    paragraph(`분석 기사: ${briefing.metadata.analyzedArticleCount}개`),
    paragraph(`선택 뉴스: ${briefing.metadata.selectedNewsCount}개`),
  );

  if (briefing.metadata.modelName !== undefined) {
    blocks.push(paragraph(`AI 모델: ${briefing.metadata.modelName}`));
  }

  if (briefing.metadata.promptVersion !== undefined) {
    blocks.push(paragraph(`프롬프트 버전: ${briefing.metadata.promptVersion}`));
  }

  return blocks;
}

function buildNewsBlocks(news: AnalyzedNews): NotionBlock[] {
  const blocks: NotionBlock[] = [
    divider(),
    heading3(`${news.representativeTitle} (중요도 ${news.importance}/5)`),
    paragraph(`한 줄 결론: ${news.oneLineSummary}`),
    paragraph(`왜 중요한가: ${news.whyImportant}`),
  ];

  // 누가 꼭 읽어야 하나?
  blocks.push(heading3("누가 꼭 읽어야 하나?"));
  blocks.push(...buildTargetAudienceBlocks(news.targetAudience));

  if (news.impactAssessment && news.impactAssessment.length > 0) {
    blocks.push(heading3("영향도 평가"));
    blocks.push(...buildImpactBlocks(news.impactAssessment));
  }

  for (const explanationParagraph of news.explanation.split("\n\n")) {
    const trimmed = explanationParagraph.trim();
    if (trimmed.length === 0) {
      continue;
    }
    const sectionMatch = trimmed.match(/^\[(.+?)\]$/);
    if (sectionMatch) {
      blocks.push(heading3(sectionMatch[1]!));
    } else {
      blocks.push(paragraph(trimmed));
    }
  }

  blocks.push(heading3("뉴스 안의 경제용어"));
  blocks.push(...buildGlossaryBlocks(news.economicTerms));

  blocks.push(heading3("출처"));
  for (const source of news.sources) {
    const primaryLabel = source.isPrimary ? "대표 출처" : "참고 출처";
    blocks.push(
      bulletedListItem(
        `${primaryLabel}: ${source.sourceName} - ${source.title} (${source.publishedAt})`,
        source.url,
      ),
    );
  }

  return blocks;
}

function buildTargetAudienceBlocks(targetAudience: TargetAudience): NotionBlock[] {
  const blocks: NotionBlock[] = [];

  if (targetAudience.mustRead.length > 0) {
    for (const audience of targetAudience.mustRead) {
      blocks.push(bulletedListItem(`✅ ${audience}`));
    }
  }

  if (targetAudience.notRelevant.length > 0) {
    blocks.push(paragraph("크게 신경 쓰지 않아도 되는 사람:"));
    for (const audience of targetAudience.notRelevant) {
      blocks.push(bulletedListItem(`• ${audience}`));
    }
  }

  if (blocks.length === 0) {
    return [paragraph("확인된 내용이 없습니다.")];
  }

  return blocks;
}

function buildGlossaryBlocks(terms: EconomicTerm[]): NotionBlock[] {
  if (terms.length === 0) {
    return [paragraph("정리된 경제용어가 없습니다.")];
  }

  return terms.map((term) => {
    const example = term.example === undefined ? "" : ` 예: ${term.example}`;
    return bulletedListItem(`${term.term}: ${term.explanation}${example}`);
  });
}

function buildImpactBlocks(impacts: ImpactScore[]): NotionBlock[] {
  return impacts.map((impact) =>
    bulletedListItem(`${impact.target}: ${impact.score}/5점 — ${impact.reason}`),
  );
}

function toBulletList(values: string[]): NotionBlock[] {
  if (values.length === 0) {
    return [paragraph("확인된 내용이 없습니다.")];
  }

  return values.map((value) => bulletedListItem(value));
}

function heading2(content: string): NotionBlock {
  return {
    object: "block",
    type: "heading_2",
    heading_2: {
      rich_text: richText(content),
    },
  };
}

function heading3(content: string): NotionBlock {
  return {
    object: "block",
    type: "heading_3",
    heading_3: {
      rich_text: richText(content),
    },
  };
}

function paragraph(content: string): NotionBlock {
  return {
    object: "block",
    type: "paragraph",
    paragraph: {
      rich_text: richText(content),
    },
  };
}

function bulletedListItem(content: string, url?: string): NotionBlock {
  return {
    object: "block",
    type: "bulleted_list_item",
    bulleted_list_item: {
      rich_text: richText(content, url),
    },
  };
}

function divider(): NotionBlock {
  return {
    object: "block",
    type: "divider",
    divider: {},
  };
}

function richText(content: string, url?: string): NotionRichText[] {
  const chunks = splitText(content);
  return chunks.map((chunk) => ({
    type: "text",
    text:
      url === undefined
        ? { content: chunk }
        : { content: chunk, link: { url } },
  }));
}

function splitText(content: string): string[] {
  if (content.length === 0) {
    return [""];
  }

  const chunks: string[] = [];
  for (let start = 0; start < content.length; start += MAX_RICH_TEXT_CONTENT_LENGTH) {
    chunks.push(content.slice(start, start + MAX_RICH_TEXT_CONTENT_LENGTH));
  }
  return chunks;
}
