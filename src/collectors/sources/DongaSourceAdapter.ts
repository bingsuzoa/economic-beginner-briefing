import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * 동아일보 경제 RSS 수집 어댑터.
 * 종합 일간지로, 부동산·금융·산업 기사를 다룹니다.
 */
export class DongaSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "동아일보",
      sourceType: "news_media",
      feedUrls: [
        "https://rss.donga.com/economy.xml",
      ],
    });
  }
}
