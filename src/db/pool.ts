import pg from "pg";
import type { AppEnv } from "../config/env.js";

const { Pool } = pg;

export type DbPool = pg.Pool;

export function createPool(env: AppEnv): DbPool {
  if (env.DATABASE_URL) {
    return new Pool({
      connectionString: env.DATABASE_URL,
    });
  }

  return new Pool({
    host: env.DB_HOST,
    port: env.DB_PORT,
    database: env.DB_NAME,
    user: env.DB_USER,
    password: env.DB_PASSWORD,
  });
}
