import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * 머니투데이 RSS 수집 어댑터.
 * 경제 전문 언론사로, 금융·증권·부동산 뉴스를 다룹니다.
 * 전체 뉴스 피드를 사용하며, 경제 외 기사는 카테고리 분류 단계에서 필터링됩니다.
 */
export class MoneyTodaySourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "머니투데이",
      sourceType: "news_media",
      feedUrls: [
        "https://rss.mt.co.kr/mt_news.xml",
      ],
    });
  }
}
