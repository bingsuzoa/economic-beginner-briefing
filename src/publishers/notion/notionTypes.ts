import type { Briefing } from "../../domain/briefing.js";

export interface NotionPageSummary {
  id: string;
  url?: string;
}

export interface NotionRichText {
  type: "text";
  text: {
    content: string;
    link?: {
      url: string;
    };
  };
  annotations?: {
    bold?: boolean;
    italic?: boolean;
    code?: boolean;
  };
}

export type NotionBlock =
  | {
      object: "block";
      type: "heading_2";
      heading_2: {
        rich_text: NotionRichText[];
      };
    }
  | {
      object: "block";
      type: "heading_3";
      heading_3: {
        rich_text: NotionRichText[];
      };
    }
  | {
      object: "block";
      type: "paragraph";
      paragraph: {
        rich_text: NotionRichText[];
      };
    }
  | {
      object: "block";
      type: "bulleted_list_item";
      bulleted_list_item: {
        rich_text: NotionRichText[];
      };
    }
  | {
      object: "block";
      type: "numbered_list_item";
      numbered_list_item: {
        rich_text: NotionRichText[];
      };
    }
  | {
      object: "block";
      type: "divider";
      divider: Record<string, never>;
    };

export interface CreateNotionPageInput {
  databaseId: string;
  briefing: Briefing;
  children: NotionBlock[];
}

export interface NotionBriefingPageClient {
  findPageByBriefingId(
    databaseId: string,
    briefingId: string,
  ): Promise<NotionPageSummary | undefined>;
  createBriefingPage(input: CreateNotionPageInput): Promise<NotionPageSummary>;
}
