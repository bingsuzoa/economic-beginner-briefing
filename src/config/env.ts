import { z } from "zod";

const EnvSchema = z.object({
  NODE_ENV: z
    .enum(["development", "production", "test"])
    .default("development"),
  TZ: z.string().default("Asia/Seoul"),
  DRY_RUN: z
    .enum(["true", "false"])
    .default("true")
    .transform((v) => v === "true"),
  LOG_LEVEL: z
    .enum(["debug", "info", "warn", "error"])
    .default("info"),

  // External API keys (optional in foundation)
  OPENAI_API_KEY: z.string().optional(),
  NOTION_API_KEY: z.string().optional(),
  NOTION_DATABASE_ID: z.string().optional(),

  EMAIL_PROVIDER: z.string().optional(),
  EMAIL_FROM: z.string().optional(),
  EMAIL_TO: z.string().optional(),
  EMAIL_API_KEY: z.string().optional(),

  // Database (optional - required only when using admin dashboard)
  DATABASE_URL: z.string().optional(),
  DB_HOST: z.string().default("localhost"),
  DB_PORT: z
    .string()
    .default("5432")
    .transform((v) => parseInt(v, 10)),
  DB_NAME: z.string().default("economic_briefing"),
  DB_USER: z.string().default("postgres"),
  DB_PASSWORD: z.string().default(""),

  // Admin Dashboard
  ADMIN_TOKEN: z.string().default(""),
  ADMIN_PORT: z
    .string()
    .default("3000")
    .transform((v) => parseInt(v, 10)),
});

export type AppEnv = z.infer<typeof EnvSchema>;

export function loadEnv(
  source: Record<string, string | undefined> = process.env,
): AppEnv {
  return EnvSchema.parse(source);
}
