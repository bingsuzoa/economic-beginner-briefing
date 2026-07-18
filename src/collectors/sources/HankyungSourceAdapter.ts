import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * 한국경제 RSS 수집 어댑터.
 * 경제 전문 언론사로, 경제·부동산·재테크를 폭넓게 다룹니다.
 */
export class HankyungSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "한국경제",
      sourceType: "news_media",
      feedUrls: [
        "https://www.hankyung.com/feed/economy",
      ],
    });
  }
}
