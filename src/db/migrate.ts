import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { DbPool } from "./pool.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const MIGRATIONS_DIR = path.resolve(__dirname, "../../migrations");

async function ensureMigrationsTable(pool: DbPool): Promise<void> {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      id SERIAL PRIMARY KEY,
      filename VARCHAR(255) NOT NULL UNIQUE,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
}

async function getAppliedMigrations(pool: DbPool): Promise<Set<string>> {
  const result = await pool.query<{ filename: string }>(
    "SELECT filename FROM schema_migrations ORDER BY id",
  );
  return new Set(result.rows.map((r) => r.filename));
}

function getMigrationFiles(direction: "up" | "down"): string[] {
  if (!fs.existsSync(MIGRATIONS_DIR)) {
    return [];
  }
  return fs
    .readdirSync(MIGRATIONS_DIR)
    .filter((f) => {
      if (direction === "up") {
        return f.endsWith(".sql") && !f.endsWith(".down.sql");
      }
      return f.endsWith(".down.sql");
    })
    .sort();
}

export async function migrateUp(pool: DbPool): Promise<string[]> {
  await ensureMigrationsTable(pool);
  const applied = await getAppliedMigrations(pool);
  const files = getMigrationFiles("up");
  const newlyApplied: string[] = [];

  for (const file of files) {
    if (applied.has(file)) continue;
    const sql = fs.readFileSync(path.join(MIGRATIONS_DIR, file), "utf-8");
    await pool.query(sql);
    await pool.query(
      "INSERT INTO schema_migrations (filename) VALUES ($1)",
      [file],
    );
    newlyApplied.push(file);
    console.log(`Applied migration: ${file}`);
  }

  if (newlyApplied.length === 0) {
    console.log("No new migrations to apply.");
  }

  return newlyApplied;
}

export async function migrateDown(pool: DbPool, steps = 1): Promise<string[]> {
  await ensureMigrationsTable(pool);
  const result = await pool.query<{ filename: string }>(
    "SELECT filename FROM schema_migrations ORDER BY id DESC LIMIT $1",
    [steps],
  );
  const rolledBack: string[] = [];

  for (const row of result.rows) {
    const downFile = row.filename.replace(".sql", ".down.sql");
    const downPath = path.join(MIGRATIONS_DIR, downFile);
    if (!fs.existsSync(downPath)) {
      console.warn(`No rollback file found: ${downFile}`);
      continue;
    }
    const sql = fs.readFileSync(downPath, "utf-8");
    await pool.query(sql);
    await pool.query(
      "DELETE FROM schema_migrations WHERE filename = $1",
      [row.filename],
    );
    rolledBack.push(row.filename);
    console.log(`Rolled back: ${row.filename}`);
  }

  return rolledBack;
}

// CLI entrypoint
if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename)) {
  const { loadEnv } = await import("../config/env.js");
  const { createPool } = await import("./pool.js");

  const env = loadEnv();
  const pool = createPool(env);

  const command = process.argv[2] ?? "up";
  try {
    if (command === "down") {
      const steps = parseInt(process.argv[3] ?? "1", 10);
      await migrateDown(pool, steps);
    } else {
      await migrateUp(pool);
    }
  } finally {
    await pool.end();
  }
}
