import Parser from "rss-parser";
import { TIMEOUTS } from "../../config/constants.js";
import { AppError } from "../../errors/AppError.js";
import { ErrorCodes } from "../../errors/errorCodes.js";

export interface RSSItem {
  title?: string;
  link?: string;
  pubDate?: string;
  contentSnippet?: string;
  content?: string;
  isoDate?: string;
  guid?: string;
}

export interface RSSFeed {
  items: RSSItem[];
  title?: string;
  link?: string;
}

/**
 * Fetches and parses an RSS feed with timeout handling.
 */
export async function fetchRSSFeed(url: string): Promise<RSSFeed> {
  const parser = new Parser({
    timeout: TIMEOUTS.RSS_HTTP,
  });

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), TIMEOUTS.RSS_HTTP);

  try {
    const feed = await parser.parseURL(url);
    clearTimeout(timeoutId);

    return {
      items: feed.items.map((item) => ({
        title: item.title,
        link: item.link,
        pubDate: item.pubDate,
        contentSnippet: item.contentSnippet,
        content: item.content,
        isoDate: item.isoDate,
        guid: item.guid,
      })),
      title: feed.title,
      link: feed.link,
    };
  } catch (error) {
    clearTimeout(timeoutId);

    if (error instanceof Error) {
      if (error.name === "AbortError" || error.message.includes("timeout")) {
        throw new AppError({
          code: ErrorCodes.COLLECT_SOURCE_TIMEOUT,
          stage: "collect",
          retryable: true,
          safeMessage: `RSS feed timeout: ${url}`,
          cause: error,
        });
      }

      throw new AppError({
        code: ErrorCodes.COLLECT_PARSE_ERROR,
        stage: "collect",
        retryable: true,
        safeMessage: `Failed to parse RSS feed: ${url}`,
        cause: error,
      });
    }

    throw new AppError({
      code: ErrorCodes.COLLECT_SOURCE_UNAVAILABLE,
      stage: "collect",
      retryable: true,
      safeMessage: `RSS source unavailable: ${url}`,
    });
  }
}
