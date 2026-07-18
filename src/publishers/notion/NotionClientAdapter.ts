import { Client, isNotionClientError } from "@notionhq/client";
import { APIErrorCode, ClientErrorCode } from "@notionhq/client";
import type {
  BlockObjectRequest,
  PageObjectResponse,
  PartialPageObjectResponse,
} from "@notionhq/client/build/src/api-endpoints.js";
import type {
  CreateNotionPageInput,
  NotionBlock,
  NotionBriefingPageClient,
  NotionPageSummary,
} from "./notionTypes.js";

const MAX_CHILDREN_PER_REQUEST = 100;

export class NotionClientAdapter implements NotionBriefingPageClient {
  private readonly client: Client;
  private resolvedDataSourceId?: string;

  constructor(auth: string) {
    this.client = new Client({ auth });
  }

  async findPageByBriefingId(
    databaseId: string,
    briefingId: string,
  ): Promise<NotionPageSummary | undefined> {
    const dataSourceId = await this.resolveDataSourceId(databaseId);
    const response = await this.client.dataSources.query({
      data_source_id: dataSourceId,
      page_size: 1,
      result_type: "page",
      filter: {
        property: "Briefing ID",
        rich_text: {
          equals: briefingId,
        },
      },
    });

    const page = response.results[0];
    if (page === undefined || page.object !== "page") {
      return undefined;
    }

    return toPageSummary(page);
  }

  async createBriefingPage(input: CreateNotionPageInput): Promise<NotionPageSummary> {
    const dataSourceId = await this.resolveDataSourceId(input.databaseId);
    const children = toBlockObjectRequests(input.children);
    const [initialChildren, ...remainingChildChunks] = chunkBlocks(
      children,
      MAX_CHILDREN_PER_REQUEST,
    );

    const response = await this.client.pages.create({
      parent: {
        data_source_id: dataSourceId,
      },
      properties: {
        Name: {
          title: [
            {
              type: "text",
              text: {
                content: input.briefing.title,
              },
            },
          ],
        },
        "Briefing ID": {
          rich_text: [
            {
              type: "text",
              text: {
                content: input.briefing.id,
              },
            },
          ],
        },
        "Target Date": {
          date: {
            start: input.briefing.targetDate,
          },
        },
        "Generated At": {
          date: {
            start: input.briefing.generatedAt,
          },
        },
        "News Count": {
          number: input.briefing.news.length,
        },
      },
      children: initialChildren ?? [],
    });

    const page = toPageSummary(response);
    for (const childChunk of remainingChildChunks) {
      await this.client.blocks.children.append({
        block_id: page.id,
        children: childChunk,
      });
    }

    return page;
  }

  private async resolveDataSourceId(databaseId: string): Promise<string> {
    if (this.resolvedDataSourceId !== undefined) {
      return this.resolvedDataSourceId;
    }

    const database = await this.client.databases.retrieve({
      database_id: databaseId,
    });

    if (database.object !== "database" || !("data_sources" in database)) {
      this.resolvedDataSourceId = databaseId;
      return this.resolvedDataSourceId;
    }

    const firstDataSource = database.data_sources[0];
    this.resolvedDataSourceId = firstDataSource === undefined ? databaseId : firstDataSource.id;
    return this.resolvedDataSourceId;
  }
}

export function isRetryableNotionError(error: unknown): boolean {
  if (!isNotionClientError(error)) {
    return false;
  }

  return (
    error.code === APIErrorCode.RateLimited ||
    error.code === APIErrorCode.InternalServerError ||
    error.code === APIErrorCode.ServiceUnavailable ||
    error.code === ClientErrorCode.RequestTimeout
  );
}

export function getSafeNotionErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.length > 0) {
    return error.message;
  }

  return "Notion 저장 중 알 수 없는 오류가 발생했습니다.";
}

function toBlockObjectRequests(blocks: NotionBlock[]): BlockObjectRequest[] {
  return blocks.map((block) => block as BlockObjectRequest);
}

function chunkBlocks(
  blocks: BlockObjectRequest[],
  size: number,
): BlockObjectRequest[][] {
  const chunks: BlockObjectRequest[][] = [];
  for (let start = 0; start < blocks.length; start += size) {
    chunks.push(blocks.slice(start, start + size));
  }
  return chunks;
}

function toPageSummary(
  page: PageObjectResponse | PartialPageObjectResponse,
): NotionPageSummary {
  return {
    id: page.id,
    url: "url" in page ? page.url : undefined,
  };
}
