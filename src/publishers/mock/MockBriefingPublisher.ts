import type { BriefingPublisher } from "../BriefingPublisher.js";
import type {
  PublishBriefingRequest,
  PublishBriefingResult,
  Briefing,
} from "../../domain/briefing.js";
import { nowISOStringKST } from "../../utils/date.js";

export class MockBriefingPublisher implements BriefingPublisher {
  private readonly publishedBriefings: Briefing[] = [];

  async publish(request: PublishBriefingRequest): Promise<PublishBriefingResult> {
    this.publishedBriefings.push(request.briefing);

    return {
      briefingId: request.briefing.id,
      results: [
        {
          channel: "mock",
          status: request.dryRun ? "skipped" : "success",
          externalId: request.dryRun
            ? undefined
            : `mock-ext-${request.briefing.id}`,
        },
      ],
      completedAt: nowISOStringKST(),
    };
  }

  getPublishedBriefings(): readonly Briefing[] {
    return this.publishedBriefings;
  }
}
