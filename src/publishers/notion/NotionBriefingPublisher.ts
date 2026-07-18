import type { BriefingPublisher } from "../BriefingPublisher.js";
import type {
  PublishBriefingRequest,
  PublishBriefingResult,
  PublishChannelResult,
} from "../../domain/briefing.js";
import { ErrorCodes } from "../../errors/errorCodes.js";
import { nowISOStringKST } from "../../utils/date.js";
import { buildNotionBriefingBlocks } from "./buildNotionPage.js";
import {
  getSafeNotionErrorMessage,
  isRetryableNotionError,
} from "./NotionClientAdapter.js";
import type { NotionBriefingPageClient } from "./notionTypes.js";

export interface NotionBriefingPublisherOptions {
  databaseId: string;
  client: NotionBriefingPageClient;
}

export class NotionBriefingPublisher implements BriefingPublisher {
  private readonly databaseId: string;
  private readonly client: NotionBriefingPageClient;

  constructor(options: NotionBriefingPublisherOptions) {
    this.databaseId = options.databaseId;
    this.client = options.client;
  }

  async publish(request: PublishBriefingRequest): Promise<PublishBriefingResult> {
    const channelResult = await this.publishToNotion(request);

    return {
      briefingId: request.briefing.id,
      results: [channelResult],
      completedAt: nowISOStringKST(),
    };
  }

  private async publishToNotion(
    request: PublishBriefingRequest,
  ): Promise<PublishChannelResult> {
    if (request.dryRun) {
      return {
        channel: "notion",
        status: "skipped",
        externalId: `dry-run:${request.briefing.id}`,
      };
    }

    try {
      const existingPage = await this.client.findPageByBriefingId(
        this.databaseId,
        request.briefing.id,
      );

      if (existingPage !== undefined) {
        return {
          channel: "notion",
          status: "skipped",
          externalId: existingPage.id,
          errorCode: ErrorCodes.PUBLISH_DUPLICATE,
          errorMessage: "이미 같은 브리핑 ID로 저장된 Notion 페이지가 있습니다.",
        };
      }

      const page = await this.client.createBriefingPage({
        databaseId: this.databaseId,
        briefing: request.briefing,
        children: buildNotionBriefingBlocks(request.briefing),
      });

      return {
        channel: "notion",
        status: "success",
        externalId: page.id,
      };
    } catch (error) {
      return {
        channel: "notion",
        status: "failed",
        errorCode: ErrorCodes.PUBLISH_CHANNEL_ERROR,
        errorMessage: getSafeNotionErrorMessage(error),
      };
    }
  }

  static isRetryableFailure(error: unknown): boolean {
    return isRetryableNotionError(error);
  }
}
