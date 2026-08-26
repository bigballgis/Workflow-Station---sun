---
name: extract-help-guideline
description: >-
  Extracts and maintains designer-facing articles for the /help/ Guidelines
  portal from product UI copy, validators, and business rules. Merges
  deep-documentation quality (layered pages, exact surfaces, diagrams,
  cross-links, llms.txt, same-task doc sync) without creating a second docs
  site. Use when the user asks to 提取 guideline, generate /help/ articles,
  extract-help-guideline, 给人看的指南, 重构 guideline, refresh help docs, or
  turn designer behavior into a help page. Does not generate Cursor rules
  or SKILL.md (except this skill's own maintenance).
disable-model-invocation: true
---

# Extract /help/ guideline articles

Turn **what a designer sees and must follow** into a page on `frontend/help`.
This is not an agent rule dump. Audience: people using Developer Workstation /
Admin Center, unauthenticated, environment-independent (`/help/`).

Quality bar comes from **deep-documentation** (canonical:
[adamdroberts/agent-skills deep-documentation](https://github.com/adamdroberts/agent-skills/blob/main/deep-documentation/SKILL.md)),
adapted to this portal only. Do **not** spin up Mintlify, a second markdown
site, repo-wide `llms-full.txt`, or a parallel README stack.

**New article:** do not write files until the user confirms the 提取清单.
**User already named pages and asked to rewrite / 重构:** skip the清单 and edit
those articles.

## When to use

User names a topic (View 访问、计算字段、邮件模板) or says 提取 guideline /
生成 help 文章 / 重构 guideline / refresh `/help/`.

## When not to use

- Authoring `.cursor/rules` or new `.cursor/skills` → `create-skill` / `/create-rule`
- Code review, SAST, playbook, performance N+1
- Inventing product behavior not present in UI or validators
- Dumping Java class names, Docker, Kong, or playbooks into `/help/`

## Core rules (deep-documentation, scoped to /help/)

- Ground every sentence in the real UI: i18n labels, dialogs, save blockers, `#ERR`.
- Write operational copy: what to tick / type / save, and what fails. No marketing.
- Layer each article: **overview → order of work → how-to with figures → exact
  fields/samples → failures → related articles**.
- Cross-link related guidelines with `router-link` (Send email ↔ Email Monitor ↔
  Computed fields). Do not duplicate the other article.
- Use a **flow** (`GuideArticle` `flow-keys`) for multi-step designer jobs.
  Prefer that over a decorative paragraph. Mermaid in i18n is optional; the
  rendered flow list is the human diagram.
- Screenshots and formula/email **samples must match the same demo Function Unit**
  when figures exist (Purchase Request: main `help_pr`, sub `help_pr_line`).
  Do not keep leftover sample names (`leave_request`, `date_info`) next to those
  figures.
- Same task as the UI change: if Connections / Templates / Formula / Send Task
  behavior changes, update the matching `/help/` article, figures, `llms.txt`,
  and `DesignerHelpLink` in one change.
- Treat `frontend/help/public/llms.txt` as the LLM index for this portal.
  Keep it in sync with `GUIDELINES`. Optional `llms-full.txt` is a compact
  English bundle of the **help articles**, not the whole Git repo.

## Workflow

### 1. Topic

If missing, ask **one** question: which topic (or “scan candidates”).
Do not extract the whole product in one pass.

### 2. Read sources (only user-facing)

Read [reference.md](reference.md). Prefer:

1. Designer / Admin **i18n** strings and dialogs (labels, hints, errors)
2. Validators that **block save** or show `#ERR` / form messages
3. Existing `/help/` article on the same id (update, do not fork)
4. Business-rule skills (e.g. `view-access-control`) **rewritten for humans**
5. Current figures under `frontend/help/public/guides/` and the capture script

Skip: Java class names, Docker, Kong, Checkmarx, agent playbooks, internal IDs
unless the UI shows them.

### 3. 提取清单 (new articles only)

Output this block, then **stop**. No Edit/Write until 确认 / 可以 / 执行.

```markdown
## 提取清单（help）

【主题】…
【读者】设计器用户（不登录 /help）
【来源】
- path — 抽了哪条可见规则
【将写入】
- `frontend/help/src/guidelines.ts` id: `…` path: `/…`
- `NAV_TREE` 挂到哪个门户菜单
- 源界面 `DesignerHelpLink`
- view + i18n keys（en / zh-CN / zh-TW）
- `frontend/help/public/llms.txt`（及可选 `llms-full.txt`）
- 需要截图时：`frontend/help/public/guides/…png` + bump `GUIDE_FIGURE_REV`
【章节】overview / flow / how-to / samples / failures / related
【不写】agent-only / 实现细节
【已有页面】无 | 将更新 `computed-fields` 等

回复 **确认** 后我再改 `frontend/help`。
```

Every 【来源】 line must be a real file you opened. If a rule is only in an
agent skill, say so and rewrite; do not paste the skill.

### 4. After confirm (or explicit 重构) — write like the live articles

Copy the pattern in:

- `frontend/help/src/views/ComputedFieldGuide.vue` / `EmailSendGuide.vue`
- `frontend/help/src/components/GuideArticle.vue`
- `frontend/help/src/guidelines.ts` (`GUIDELINES` + `NAV_TREE`)
- `frontend/help/src/i18n/locales/{en,zh-CN,zh-TW}.ts`
- `frontend/help/public/llms.txt`

Checklist:

- [ ] New `id` kebab-case; `path` `/that-id`
- [ ] Register in `GUIDELINES` (titleKey + summaryKey + lazy view)
- [ ] `NAV_TREE` leaf under the **same menu** as the product. Empty menus stay grey.
- [ ] `DesignerHelpLink` on the source screen → `/help/<id>` (rule `help-guideline-link`)
- [ ] `GuideArticle`: sections may set `anchor`, `figure`, `samples`; page may set
      `flow-keys` and `related`
- [ ] Figures: capture real UI (`frontend/scripts/capture-help-guide-images.mjs`),
      store under `public/guides/`, redact email/name/process id, bump
      `GUIDE_FIGURE_REV` in `GuideArticle.vue` so browsers drop the old PNG
- [ ] Samples use the **same table/field names as the figures**
- [ ] `data-testid="…-guide-page"`
- [ ] i18n **three locales in the same change**; no raw `${token}` in locale strings
- [ ] Voice: ticks / types / saves; short sentences
- [ ] Update `public/llms.txt` (and `llms-full.txt` if present)
- [ ] Do not add Element Plus; styles already in `help.css`

### 5. Verify (after write)

- `cd frontend/help && pnpm run build`
- Rebuild `platform-help-frontend` (compose) when shipping to `localhost:3000/help/`
- Playwright `frontend/scripts/verify-help-portal.mjs`
- Quote screenshot paths under `frontend/developer-workstation/verification-screenshots/`

## Voice

| Do | Don't |
|---|---|
| “Tick Computed, open Formula, then save the table.” | “`ComputedFieldEvaluator` then persists…” |
| “If BU and Role are both empty, only System Administrator sees the view.” | Copy mermaid of Portal services |
| Formula samples: `quantity * unit_price` on `help_pr_line` | Leftover names that contradict the screenshot (`leave_request`) |

## Invoke

```text
/extract-help-guideline
主题：View 访问管控
```

or: `按 extract-help-guideline 提取邮件模板 guideline`

or: `按 extract-help-guideline 重构当前 guideline`
