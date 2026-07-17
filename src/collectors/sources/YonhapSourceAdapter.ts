import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * 연합뉴스 경제 RSS 수집 어댑터.
 * 국내 최대 통신사로, 경제 뉴스 전반을 커버합니다.
 */
export class YonhapSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "연합뉴스",
      sourceType: "news_media",
      feedUrls: [
        "https://www.yna.co.kr/rss/economy.xml",
      ],
    });
  }
}
