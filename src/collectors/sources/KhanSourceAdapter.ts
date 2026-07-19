import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * 경향신문 경제 RSS 수집 어댑터.
 * 종합 일간지로, 경제 정책·부동산·금융 기사를 다룹니다.
 */
export class KhanSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "경향신문",
      sourceType: "news_media",
      feedUrls: [
        "https://www.khan.co.kr/rss/rssdata/economy_news.xml",
      ],
    });
  }
}
