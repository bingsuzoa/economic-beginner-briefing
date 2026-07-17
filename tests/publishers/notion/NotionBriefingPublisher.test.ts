import { describe, expect, it } from "vitest";
import { NotionBriefingPublisher } from "../../../src/publishers/notion/NotionBriefingPublisher.js";
import type {
  CreateNotionPageInput,
  NotionBriefingPageClient,
  NotionPageSummary,
} from "../../../src/publishers/notion/notionTypes.js";
import { ErrorCodes } from "../../../src/errors/errorCodes.js";
import { createBriefingFixture } from "./notionTestFixtures.js";

class FakeNotionClient implements NotionBriefingPageClient {
  existingPage?: NotionPageSummary;
  createdInput?: CreateNotionPageInput;
  findCalls = 0;
  createCalls = 0;
  error?: Error;

  async findPageByBriefingId(): Promise<NotionPageSummary | undefined> {
    this.findCalls += 1;
    if (this.error !== undefined) {
      throw this.error;
    }
    return this.existingPage;
  }

  async createBriefingPage(input: CreateNotionPageInput): Promise<NotionPageSummary> {
    this.createCalls += 1;
    if (this.error !== undefined) {
      throw this.error;
    }
    this.createdInput = input;
    return {
      id: "notion-page-1",
      url: "https://notion.so/notion-page-1",
    };
  }
}

describe("NotionBriefingPublisher", () => {
  it("should create a Notion briefing page", async () => {
    const client = new FakeNotionClient();
    const publisher = new NotionBriefingPublisher({
      databaseId: "notion-db-1",
      client,
    });
    const briefing = createBriefingFixture();

    const result = await publisher.publish({ briefing, dryRun: false });

    expect(result.briefingId).toBe(briefing.id);
    expect(result.results[0]).toMatchObject({
      channel: "notion",
      status: "success",
      externalId: "notion-page-1",
    });
    expect(client.findCalls).toBe(1);
    expect(client.createCalls).toBe(1);
    expect(client.createdInput?.databaseId).toBe("notion-db-1");
    expect(client.createdInput?.children.length).toBeGreaterThan(0);
  });

  it("should skip API writes during dry run", async () => {
    const client = new FakeNotionClient();
    const publisher = new NotionBriefingPublisher({
      databaseId: "notion-db-1",
      client,
    });
    const briefing = createBriefingFixture();

    const result = await publisher.publish({ briefing, dryRun: true });

    expect(result.results[0]).toMatchObject({
      channel: "notion",
      status: "skipped",
      externalId: `dry-run:${briefing.id}`,
    });
    expect(client.findCalls).toBe(0);
    expect(client.createCalls).toBe(0);
  });

  it("should prevent duplicate pages for the same briefing id", async () => {
    const client = new FakeNotionClient();
    client.existingPage = {
      id: "existing-page-1",
      url: "https://notion.so/existing-page-1",
    };
    const publisher = new NotionBriefingPublisher({
      databaseId: "notion-db-1",
      client,
    });

    const result = await publisher.publish({
      briefing: createBriefingFixture(),
      dryRun: false,
    });

    expect(result.results[0]).toMatchObject({
      channel: "notion",
      status: "skipped",
      externalId: "existing-page-1",
      errorCode: ErrorCodes.PUBLISH_DUPLICATE,
    });
    expect(client.createCalls).toBe(0);
  });

  it("should return a failed channel result when the Notion API fails", async () => {
    const client = new FakeNotionClient();
    client.error = new Error("Notion 권한이 없습니다.");
    const publisher = new NotionBriefingPublisher({
      databaseId: "notion-db-1",
      client,
    });

    const result = await publisher.publish({
      briefing: createBriefingFixture(),
      dryRun: false,
    });

    expect(result.results[0]).toMatchObject({
      channel: "notion",
      status: "failed",
      errorCode: ErrorCodes.PUBLISH_CHANNEL_ERROR,
      errorMessage: "Notion 권한이 없습니다.",
    });
  });
});
