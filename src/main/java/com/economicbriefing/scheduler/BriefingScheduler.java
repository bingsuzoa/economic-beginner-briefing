package com.economicbriefing.scheduler;

import com.economicbriefing.article.YonhapArticleService;
import com.economicbriefing.briefing.DailyBriefingService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "briefing.scheduler.enabled", havingValue = "true")
public class BriefingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BriefingScheduler.class);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final YonhapArticleService articleService;
    private final DailyBriefingService briefingService;

    public BriefingScheduler(YonhapArticleService articleService, DailyBriefingService briefingService) {
        this.articleService = articleService;
        this.briefingService = briefingService;
    }

    public void collect() {
        try {
            int saved = articleService.collectRecent();
            log.info("[Scheduler] Yonhap collection completed: saved={}", saved);
        } catch (Exception e) {
            log.error("[Scheduler] Yonhap collection failed; next tick remains armed", e);
        }
    }

    public void publishDaily() {
        try {
            briefingService.run(LocalDate.now(KST), "SCHEDULED", false);
        } catch (Exception e) {
            log.error("[Scheduler] Daily economic flow failed; next day remains armed", e);
        }
    }
}
