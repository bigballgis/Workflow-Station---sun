#!/usr/bin/env node
/**
 * sync-cursor-rules.mjs
 * --------------------------------------------------------------------------
 * Single source of truth = .cursor/rules/*.mdc
 *
 * Scans every .mdc rule, reads its frontmatter (`alwaysApply`, `globs`), and
 * rewrites the @import block inside the matching CLAUDE.md so Claude Code picks
 * up newly added Cursor rules automatically (run by a SessionStart hook).
 *
 * Routing (deterministic, mirrors Cursor's globs/alwaysApply semantics):
 *   globs contains deploy//Dockerfile/docker-compose/nginx/k8s -> deploy/CLAUDE.md
 *   else globs contains "frontend"                              -> frontend/CLAUDE.md
 *   else globs contains "backend"                               -> backend/CLAUDE.md
 *   else (no globs, or cross-cutting glob, or alwaysApply)      -> root CLAUDE.md
 *
 * Each CLAUDE.md must contain a managed region:
 *   <!-- BEGIN cursor-rules:auto --> ... <!-- END cursor-rules:auto -->
 * Only that region is rewritten; hand-written prose is left untouched.
 *
 * No external deps. Cross-platform (Node 16+). Idempotent.
 */
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '..', '..');           // .claude/scripts -> repo root
const RULES_DIR = join(REPO, '.cursor', 'rules');

const BEGIN = '<!-- BEGIN cursor-rules:auto -->';
const END = '<!-- END cursor-rules:auto -->';

// target key -> { file (abs), importPrefix }
const TARGETS = {
  root:     { file: join(REPO, 'CLAUDE.md'),            prefix: '@.cursor/rules/' },
  frontend: { file: join(REPO, 'frontend', 'CLAUDE.md'), prefix: '@../.cursor/rules/' },
  backend:  { file: join(REPO, 'backend', 'CLAUDE.md'),  prefix: '@../.cursor/rules/' },
  deploy:   { file: join(REPO, 'deploy', 'CLAUDE.md'),   prefix: '@../.cursor/rules/' },
};

// Pin a few foundational always-on rules to the top of the root block.
const ROOT_PRIORITY = [
  'project-context', 'domain-model', 'reasoning-protocol',
  'ai-guardrails', 'cross-cutting', 'change-playbook',
];

function parseFrontmatter(text) {
  const m = text.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!m) return {};
  const body = m[1];
  const fm = {};
  const always = body.match(/^alwaysApply:\s*(true|false)\s*$/m);
  if (always) fm.alwaysApply = always[1] === 'true';
  const globs = body.match(/^globs:\s*(.+?)\s*$/m);
  if (globs) {
    fm.globs = globs[1].trim().replace(/^["']|["']$/g, ''); // strip wrapping quotes
  }
  return fm;
}

function classify(globs) {
  if (!globs) return 'root';
  const g = globs.toLowerCase();
  if (/deploy\/|dockerfile|docker-compose|nginx|\/k8s|k8s\//.test(g)) return 'deploy';
  if (g.includes('frontend')) return 'frontend';
  if (g.includes('backend')) return 'backend';
  return 'root'; // cross-cutting glob (e.g. **/*.{java,ts}) -> always available
}

function rewriteRegion(absFile, importLines) {
  if (!existsSync(absFile)) {
    console.warn(`[sync-cursor-rules] skip (missing): ${absFile}`);
    return false;
  }
  const src = readFileSync(absFile, 'utf8');
  const bi = src.indexOf(BEGIN);
  const ei = src.indexOf(END);
  if (bi === -1 || ei === -1 || ei < bi) {
    console.warn(`[sync-cursor-rules] skip (no managed region): ${absFile}`);
    return false;
  }
  const block = importLines.length
    ? `${BEGIN}\n${importLines.join('\n')}\n${END}`
    : `${BEGIN}\n${END}`;
  const next = src.slice(0, bi) + block + src.slice(ei + END.length);
  if (next !== src) {
    writeFileSync(absFile, next);
    return true;
  }
  return false;
}

function main() {
  if (!existsSync(RULES_DIR)) {
    console.warn(`[sync-cursor-rules] no .cursor/rules dir; nothing to sync.`);
    return;
  }
  const files = readdirSync(RULES_DIR).filter((f) => f.endsWith('.mdc')).sort();
  const buckets = { root: [], frontend: [], backend: [], deploy: [] };

  for (const f of files) {
    const name = f.replace(/\.mdc$/, '');
    const fm = parseFrontmatter(readFileSync(join(RULES_DIR, f), 'utf8'));
    const target = classify(fm.globs);
    buckets[target].push(name);
  }

  // Order root bucket: pinned priority first, then the rest alphabetically.
  buckets.root.sort((a, b) => {
    const ia = ROOT_PRIORITY.indexOf(a);
    const ib = ROOT_PRIORITY.indexOf(b);
    if (ia !== -1 || ib !== -1) {
      return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib) || a.localeCompare(b);
    }
    return a.localeCompare(b);
  });

  let changed = 0;
  for (const [key, names] of Object.entries(buckets)) {
    const { file, prefix } = TARGETS[key];
    const lines = names.map((n) => `${prefix}${n}.mdc`);
    if (rewriteRegion(file, lines)) changed++;
    console.log(`[sync-cursor-rules] ${key}: ${names.length} rule(s)` +
      (names.length ? ` -> ${names.join(', ')}` : ''));
  }
  console.log(`[sync-cursor-rules] done; ${changed} CLAUDE.md file(s) updated.`);
}

main();
