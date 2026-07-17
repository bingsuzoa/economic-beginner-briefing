import { describe, expect, it } from "vitest";
import { buildNotionBriefingBlocks } from "../../../src/publishers/notion/buildNotionPage.js";
import { createBriefingFixture } from "./notionTestFixtures.js";

describe("buildNotionBriefingBlocks", () => {
  it("should build readable Notion blocks from a briefing", () => {
    const briefing = createBriefingFixture();

    const blocks = buildNotionBriefingBlocks(briefing);

    expect(blocks[0]).toMatchObject({
      type: "heading_2",
      heading_2: {
        rich_text: [{ text: { content: "오늘의 핵심 요약" } }],
      },
    });
    expect(blocks.some((block) => block.type === "divider")).toBe(true);
    expect(JSON.stringify(blocks)).toContain("전세보증금 별도 관리 방안 검토");
    expect(JSON.stringify(blocks)).toContain("신혼부부/주거 준비 가정 영향");
    expect(JSON.stringify(blocks)).toContain("https://example.com/news/1");
  });

  it("should preserve Korean and special characters", () => {
    const briefing = createBriefingFixture({
      overallSummary: ["한글, 특수문자 !@#$%^&*()와 숫자 123을 그대로 저장합니다."],
    });

    const blocks = buildNotionBriefingBlocks(briefing);

    expect(JSON.stringify(blocks)).toContain("한글, 특수문자 !@#$%^&*()");
  });

  it("should split long rich text content into Notion-safe chunks", () => {
    const longSummary = "긴 설명".repeat(800);
    const briefing = createBriefingFixture({
      overallSummary: [longSummary],
    });

    const blocks = buildNotionBriefingBlocks(briefing);
    const firstSummaryBlock = blocks.find(
      (block) => block.type === "bulleted_list_item",
    );

    expect(firstSummaryBlock?.type).toBe("bulleted_list_item");
    if (firstSummaryBlock?.type !== "bulleted_list_item") {
      throw new Error("Expected a bulleted list item");
    }
    expect(firstSummaryBlock.bulleted_list_item.rich_text.length).toBeGreaterThan(1);
    for (const richText of firstSummaryBlock.bulleted_list_item.rich_text) {
      expect(richText.text.content.length).toBeLessThanOrEqual(2_000);
    }
  });

  it("should render explicit fallback text for empty lists", () => {
    const briefing = createBriefingFixture({
      overallSummary: [],
      glossary: [],
    });

    const blocks = buildNotionBriefingBlocks(briefing);
    const serialized = JSON.stringify(blocks);

    expect(serialized).toContain("확인된 내용이 없습니다.");
    expect(serialized).toContain("정리된 경제용어가 없습니다.");
  });
});
