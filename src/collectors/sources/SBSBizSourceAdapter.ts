import { BaseRSSAdapter } from "./BaseRSSAdapter.js";

/**
 * SBS Biz RSS 수집 어댑터.
 * 방송사 경제 뉴스로, 생활경제 분야에 강점이 있습니다.
 */
export class SBSBizSourceAdapter extends BaseRSSAdapter {
  constructor() {
    super({
      sourceName: "SBS Biz",
      sourceType: "news_media",
      feedUrls: [
        "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=02&plink=RSSREADER",
      ],
    });
  }
}
