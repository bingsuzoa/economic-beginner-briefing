export const TIMEZONE = "Asia/Seoul" as const;

export const KST_OFFSET_HOURS = 9;

/** Cron expression: every hour at minute 0 (UTC) */
export const SCHEDULER_CRON = "0 * * * *";

/** Duration of one collection window in milliseconds (1 hour) */
export const COLLECTION_WINDOW_MS = 60 * 60 * 1000;

export const DEFAULT_MAX_SELECTED_NEWS = 10;

export const DEFAULT_AUDIENCE = {
  economicKnowledgeLevel: "beginner" as const,
  interests: [
    "interest_rate" as const,
    "loan" as const,
    "housing" as const,
    "jeonse_monthly_rent" as const,
    "deposit_saving" as const,
    "government_support" as const,
  ],
  contextNotes: [
    "신혼부부",
    "주택 구입과 출산 준비에 관심이 있음",
    "경제용어 설명이 필요함",
  ],
};

export const TIMEOUTS = {
  RSS_HTTP: 10_000,
  AI_API: 60_000,
  NOTION_API: 15_000,
  EMAIL_API: 15_000,
} as const;

export const RETRY = {
  MAX_ATTEMPTS: 2,
  INITIAL_DELAY_MS: 1_000,
  NEXT_DELAY_MS: 2_000,
} as const;

export const PIPELINE_LOCK_MAX_AGE_MS = 30 * 60 * 1000; // 30 minutes

export const ADMIN_DEFAULT_PAGE_SIZE = 20;
