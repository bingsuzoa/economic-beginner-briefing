import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * 서울경제 RSS 수집 어댑터.
 * 경제 전문 언론사로, 산업·금융·부동산 뉴스를 폭넓게 다룹니다.
 */
export class SedailySourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "서울경제",
      sourceType: "news_media",
      feedUrls: [
        "https://www.sedaily.com/RSS/Economy",
      ],
    });
  }
}
