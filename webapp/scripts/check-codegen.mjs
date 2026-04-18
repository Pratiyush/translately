#!/usr/bin/env node
/* global process, console */
/**
 * Drift guard for the auto-generated OpenAPI client types (T120).
 *
 * Regenerates `src/lib/api/types.gen.ts` into a temp file, byte-compares
 * against the committed one, and exits non-zero with an actionable
 * message if they differ. Wired into the webapp's CI lint step so a PR
 * that edits an endpoint but forgets to regen fails fast.
 */
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const committedPath = 'src/lib/api/types.gen.ts';
const openapiJson = '../docs/api/openapi.json';

if (!existsSync(committedPath)) {
  console.error(`check-codegen: ${committedPath} is missing. Run \`pnpm codegen\` and commit the result.`);
  process.exit(1);
}

const tmp = mkdtempSync(join(tmpdir(), 'translately-codegen-'));
const scratch = join(tmp, 'types.gen.ts');

try {
  execFileSync('npx', ['openapi-typescript', openapiJson, '--output', scratch], {
    stdio: ['ignore', 'pipe', 'inherit'],
  });
} catch (err) {
  console.error('check-codegen: regeneration failed.');
  console.error(err?.message ?? err);
  rmSync(tmp, { recursive: true, force: true });
  process.exit(1);
}

const committed = readFileSync(committedPath, 'utf8');
const regenerated = readFileSync(scratch, 'utf8');
rmSync(tmp, { recursive: true, force: true });

if (committed !== regenerated) {
  console.error('check-codegen: src/lib/api/types.gen.ts is out of date with docs/api/openapi.json.');
  console.error('check-codegen: run `pnpm codegen` from webapp/ and commit the result.');
  process.exit(1);
}
