import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * 세계일보 RSS 수집 어댑터.
 * 종합 일간지로, 경제·부동산·생활경제 기사를 제공합니다.
 */
export class SegyeSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "세계일보",
      sourceType: "news_media",
      feedUrls: [
        "https://www.segye.com/Articles/RSSList/segye_economy.xml",
      ],
    });
  }
}
