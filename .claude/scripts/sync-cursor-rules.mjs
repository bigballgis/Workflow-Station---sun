#!/usr/bin/env node
/**
 * sync-cursor-rules.mjs
 * --------------------------------------------------------------------------
 * Single source of truth = .cursor/rules (*.mdc)  AND  .cursor/skills (SKILL.md)
 *
 * (1) Rules: scans every .mdc rule, reads its frontmatter (`alwaysApply`,
 *     `globs`), and rewrites the @import block inside the matching CLAUDE.md so
 *     Claude Code picks up newly added Cursor rules automatically.
 * (2) Skills: mirrors .cursor/skills -> .claude/skills so Claude loads the same
 *     skills Cursor does, from one source. .claude-only skills are pruned.
 * Run by a SessionStart hook.
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
import {
  readFileSync, writeFileSync, readdirSync, existsSync,
  mkdirSync, rmSync, statSync,
} from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '..', '..');           // .claude/scripts -> repo root
const RULES_DIR = join(REPO, '.cursor', 'rules');
const CURSOR_SKILLS = join(REPO, '.cursor', 'skills');   // single source of truth
const CLAUDE_SKILLS = join(REPO, '.claude', 'skills');   // generated mirror

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

/**
 * Decide which CLAUDE.md bucket(s) a rule belongs to.
 * - alwaysApply:true (or no glob at all) -> root (always in context).
 * - Otherwise honor alwaysApply:false: route to EVERY matching directory bucket
 *   by path substring, and — for cross-cutting globs with no path hint (e.g.
 *   `**\/*{Test}*.{java,ts}`) — infer from file extensions so the rule loads
 *   only when editing those files, NOT always-on.
 * Returns a de-duplicated array of bucket keys (at least one).
 */
function classifyTargets(fm) {
  const globs = fm.globs;
  if (!globs || fm.alwaysApply === true) return ['root'];

  const g = globs.toLowerCase();
  const targets = new Set();

  // Directory-scoped hints (path substrings).
  if (/deploy\/|dockerfile|docker-compose|nginx|\/k8s|k8s\//.test(g)) targets.add('deploy');
  if (g.includes('frontend')) targets.add('frontend');
  if (g.includes('backend')) targets.add('backend');

  // Cross-cutting globs with no path hint: infer by file extension so a
  // non-always rule (e.g. testing, glob `**/*.{java,ts}`) lands in the code
  // buckets, not root. Handles both bare `.ts` and brace `.{java,ts}` forms.
  if (targets.size === 0) {
    if (/\bjava\b/.test(g)) targets.add('backend');
    if (/\b(ts|tsx|vue|scss)\b/.test(g)) targets.add('frontend');
  }

  // Truly unroutable non-always glob: last-resort root so it isn't dropped.
  if (targets.size === 0) targets.add('root');

  return [...targets];
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

/**
 * Mirror .cursor/skills -> .claude/skills so Claude Code loads the same skills
 * Cursor does, from a single source (.cursor/skills). Each skill is a directory
 * with a SKILL.md (+ optional support files). Skills present only under
 * .claude/skills (no .cursor counterpart) are removed, so the mirror stays exact.
 */
function listSkillDirs(root) {
  if (!existsSync(root)) return [];
  return readdirSync(root).filter((d) => {
    const p = join(root, d);
    return statSync(p).isDirectory() && existsSync(join(p, 'SKILL.md'));
  });
}

function copyDirShallow(srcDir, dstDir) {
  mkdirSync(dstDir, { recursive: true });
  let wrote = 0;
  for (const entry of readdirSync(srcDir)) {
    const s = join(srcDir, entry);
    const d = join(dstDir, entry);
    if (statSync(s).isDirectory()) { wrote += copyDirShallow(s, d); continue; }
    const src = readFileSync(s);
    const cur = existsSync(d) ? readFileSync(d) : null;
    if (!cur || !cur.equals(src)) { writeFileSync(d, src); wrote++; }
  }
  return wrote;
}

function syncSkills() {
  if (!existsSync(CURSOR_SKILLS)) {
    console.warn('[sync-cursor-rules] no .cursor/skills dir; skipping skill mirror.');
    return;
  }
  const sourceSkills = listSkillDirs(CURSOR_SKILLS);
  mkdirSync(CLAUDE_SKILLS, { recursive: true });

  let changed = 0;
  for (const name of sourceSkills) {
    changed += copyDirShallow(join(CURSOR_SKILLS, name), join(CLAUDE_SKILLS, name));
  }

  // Prune .claude skills that no longer have a .cursor source (keep mirror exact).
  const sourceSet = new Set(sourceSkills);
  for (const name of listSkillDirs(CLAUDE_SKILLS)) {
    if (!sourceSet.has(name)) {
      rmSync(join(CLAUDE_SKILLS, name), { recursive: true, force: true });
      console.log(`[sync-cursor-rules] pruned .claude/skills/${name} (no .cursor source)`);
    }
  }
  console.log(`[sync-cursor-rules] skills: ${sourceSkills.length} mirrored ` +
    `(${changed} file(s) updated) -> ${sourceSkills.join(', ')}`);
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
    for (const target of classifyTargets(fm)) buckets[target].push(name);
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

  // Mirror skills from the single source (.cursor/skills) into .claude/skills.
  syncSkills();
}

main();
