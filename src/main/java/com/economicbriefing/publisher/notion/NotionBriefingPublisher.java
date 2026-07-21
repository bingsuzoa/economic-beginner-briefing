package com.economicbriefing.publisher.notion;

import java.util.List;

import com.economicbriefing.config.NotionProperties;
import com.economicbriefing.exception.ErrorCode;
import com.economicbriefing.publisher.BriefingPublisher;
import com.economicbriefing.publisher.dto.PublishBriefingRequest;
import com.economicbriefing.publisher.dto.PublishBriefingResult;
import com.economicbriefing.publisher.dto.PublishChannelResult;
import com.economicbriefing.util.KstDateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
public class NotionBriefingPublisher implements BriefingPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotionBriefingPublisher.class);

    private final NotionClient notionClient;
    private final NotionProperties notionProperties;

    public NotionBriefingPublisher(NotionClient notionClient, NotionProperties notionProperties) {
        this.notionClient = notionClient;
        this.notionProperties = notionProperties;
    }

    @Override
    public PublishBriefingResult publish(PublishBriefingRequest request) {
        PublishChannelResult channelResult = publishToNotion(request);

        return new PublishBriefingResult(
                request.briefing().id(),
                List.of(channelResult),
                KstDateTimeUtil.now()
        );
    }

    private PublishChannelResult publishToNotion(PublishBriefingRequest request) {
        if (request.dryRun()) {
            log.info("Dry-run mode: skipping Notion publish for briefing={}", request.briefing().id());
            return PublishChannelResult.skipped("notion", "dry-run:" + request.briefing().id());
        }

        try {
            // Check for duplicates
            String existingPageId = notionClient.findPageByBriefingId(
                    notionProperties.databaseId(), request.briefing().id());

            if (existingPageId != null) {
                log.warn("Duplicate briefing found in Notion: pageId={}, briefingId={}",
                        existingPageId, request.briefing().id());
                return PublishChannelResult.skipped(
                        "notion",
                        existingPageId,
                        ErrorCode.PUBLISH_DUPLICATE.name(),
                        "이미 같은 브리핑 ID로 저장된 Notion 페이지가 있습니다."
                );
            }

            // Build blocks and create page
            List<NotionBlock> blocks = NotionPageBuilder.buildBriefingBlocks(request.briefing());
            String pageId = notionClient.createBriefingPage(
                    notionProperties.databaseId(), request.briefing(), blocks);

            log.info("Published briefing to Notion: pageId={}, briefingId={}",
                    pageId, request.briefing().id());
            return PublishChannelResult.success("notion", pageId);

        } catch (Exception e) {
            log.error("Failed to publish briefing to Notion: briefingId={}",
                    request.briefing().id(), e);
            return PublishChannelResult.failed(
                    "notion",
                    ErrorCode.PUBLISH_CHANNEL_ERROR.name(),
                    e.getMessage() != null ? e.getMessage() : "Notion 저장 중 알 수 없는 오류가 발생했습니다."
            );
        }
    }
}
