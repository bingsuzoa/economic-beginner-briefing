package com.economicbriefing.publisher.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.economicbriefing.domain.briefing.Briefing;
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
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "true", matchIfMissing = true)
public class MockBriefingPublisher implements BriefingPublisher {

    private static final Logger log = LoggerFactory.getLogger(MockBriefingPublisher.class);

    private final List<Briefing> publishedBriefings = new ArrayList<>();

    @Override
    public PublishBriefingResult publish(PublishBriefingRequest request) {
        log.info("[MOCK] Publishing briefing: id={}", request.briefing().id());

        publishedBriefings.add(request.briefing());

        PublishChannelResult channelResult;
        if (request.dryRun()) {
            channelResult = PublishChannelResult.skipped("mock", null);
        } else {
            channelResult = PublishChannelResult.success(
                    "mock", "mock-ext-" + request.briefing().id());
        }

        return new PublishBriefingResult(
                request.briefing().id(),
                List.of(channelResult),
                KstDateTimeUtil.now()
        );
    }

    public List<Briefing> getPublishedBriefings() {
        return Collections.unmodifiableList(publishedBriefings);
    }
}
