import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * KBS 경제 RSS 수집 어댑터.
 * 공영방송 경제 뉴스를 제공합니다.
 */
export class KBSSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "KBS",
      sourceType: "news_media",
      feedUrls: [
        "http://world.kbs.co.kr/rss/rss_news.htm?lang=k&id=Ede",
      ],
    });
  }
}
