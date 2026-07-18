import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * 뉴시스 경제 RSS 수집 어댑터.
 * 통신사로, 경제·금융·부동산 뉴스를 폭넓게 다룹니다.
 */
export class NewsisSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "뉴시스",
      sourceType: "news_media",
      feedUrls: [
        "https://www.newsis.com/RSS/economy.xml",
      ],
    });
  }
}
