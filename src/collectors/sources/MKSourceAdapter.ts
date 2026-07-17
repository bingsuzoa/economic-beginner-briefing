import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * 매일경제 RSS 수집 어댑터.
 * 경제 전문 언론사로, 시장·정책 뉴스에 강점이 있습니다.
 */
export class MKSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "매일경제",
      sourceType: "news_media",
      feedUrls: [
        "https://www.mk.co.kr/rss/30100041/",
      ],
    });
  }
}
