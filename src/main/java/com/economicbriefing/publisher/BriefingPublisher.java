package com.economicbriefing.publisher;

import com.economicbriefing.publisher.dto.PublishBriefingRequest;
import com.economicbriefing.publisher.dto.PublishBriefingResult;

public interface BriefingPublisher {

    PublishBriefingResult publish(PublishBriefingRequest request);
}
