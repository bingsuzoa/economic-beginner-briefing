#!/usr/bin/env node
//
// Fails if placeholder/mock data is referenced anywhere in the frontend source.
//
//   node scripts/check-mock-data.mjs
//
// Exit 0 = clean, 1 = violations found (prints every one), 2 = the check could not run.
//
// Why this exists: a UI-only commit once replaced the live `fetch` with a bundled mockData.js
// and left the real call inside a comment block. It merged, deployed, and the public site
// served fabricated briefings while the API was returning real ones. The deploy pipeline was
// working correctly - it shipped exactly what was on main - so the check has to run before the
// merge, not before the deploy.
//
// Runs on Linux (CI) and Windows (local) with no dependencies, so it can be the same check in
// both places. Add new patterns to PATTERNS below; nothing else needs to change.

import { readdirSync, statSync, readFileSync, existsSync } from 'node:fs'
import { join, extname, relative, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')

// ---------------------------------------------------------------- configuration
const TARGET_DIRS = ['frontend/src']
const EXTENSIONS = ['.js', '.jsx', '.ts', '.tsx']
const SKIP_DIRS = new Set(['node_modules', 'dist', 'build', '.git'])

// Add patterns here. `label` is only for the failure output.
const PATTERNS = [
  { label: 'mockData', regex: /mockData/i },
  { label: 'mockNews', regex: /mockNews/i },
  { label: 'import ... mock', regex: /import\s.*mock/i },
  { label: 'from ... mock', regex: /from\s.*mock/i },
]

// ---------------------------------------------------------------- scan
function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    if (SKIP_DIRS.has(entry)) continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (EXTENSIONS.includes(extname(entry))) out.push(full)
  }
  return out
}

const violations = []
let scanned = 0

for (const target of TARGET_DIRS) {
  const abs = join(REPO_ROOT, target)
  // A missing target must fail loudly. Silently passing because the directory moved would be
  // worse than having no check at all.
  if (!existsSync(abs)) {
    console.error(`ERROR: target directory not found: ${target}`)
    console.error('Update TARGET_DIRS in scripts/check-mock-data.mjs if the layout changed.')
    process.exit(2)
  }

  for (const file of walk(abs)) {
    scanned++
    const lines = readFileSync(file, 'utf8').split(/\r?\n/)
    lines.forEach((line, i) => {
      for (const { label, regex } of PATTERNS) {
        if (regex.test(line)) {
          violations.push({
            file: relative(REPO_ROOT, file).replace(/\\/g, '/'),
            line: i + 1,
            label,
            text: line.trim(),
          })
          break // one report per line is enough
        }
      }
    })
  }
}

// ---------------------------------------------------------------- report
if (violations.length === 0) {
  console.log(`OK: no mock data references in ${TARGET_DIRS.join(', ')} (${scanned} files scanned)`)
  process.exit(0)
}

console.error(`FAIL: ${violations.length} mock data reference(s) found in ${scanned} scanned files\n`)
for (const v of violations) {
  console.error(`  ${v.file}:${v.line}  [${v.label}]`)
  console.error(`      ${v.text}`)
}
console.error('\nProduction must render data from /api/briefing, not from a bundled fixture.')
console.error('Restore the real API call and delete the fixture before merging.')
console.error('If this is a false positive, adjust PATTERNS in scripts/check-mock-data.mjs.')
process.exit(1)
