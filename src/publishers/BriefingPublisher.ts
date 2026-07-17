import type { PublishBriefingRequest, PublishBriefingResult } from "../domain/briefing.js";

export interface BriefingPublisher {
  publish(request: PublishBriefingRequest): Promise<PublishBriefingResult>;
}
