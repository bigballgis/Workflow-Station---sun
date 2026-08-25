---
name: extract-help-guideline
description: >-
  Extracts designer-facing articles for the /help/ Guidelines portal from product
  UI copy, validators, and business rules. Use when the user asks to 提取 guideline,
  generate /help/ articles, extract-help-guideline, 给人看的指南, or turn designer
  behavior into a help page. Does not generate Cursor rules or SKILL.md.
disable-model-invocation: true
---

# Extract /help/ guideline articles

Turn **what a designer sees and must follow** into a page on `frontend/help`.
This is not an agent rule dump. Audience: people using Developer Workstation /
Admin Center, unauthenticated, environment-independent (`/help/`).

**Do not write files until the user confirms the 提取清单.** Dry-run first.

## When to use

User names a topic (View 访问、计算字段、邮件模板) or says 提取 guideline / 生成 help 文章.

## When not to use

- Authoring `.cursor/rules` or `.cursor/skills` → `create-skill` / `/create-rule`
- Code review, SAST, playbook, performance N+1
- Inventing product behavior not present in UI or validators

## Workflow

### 1. Topic

If missing, ask **one** question: which topic (or “scan candidates”).
Do not extract the whole product in one pass.

### 2. Read sources (only user-facing)

Read [reference.md](reference.md) for where to look. Prefer:

1. Designer / Admin **i18n** strings and dialogs (labels, hints, errors)
2. Validators that **block save** or show `#ERR` / form messages
3. Existing `/help/` article on the same id (update, do not fork)
4. Business-rule skills (e.g. `view-access-control`) **rewritten for humans**

Skip: Java class names, Docker, Kong, Checkmarx, agent playbooks, internal IDs unless the UI shows them.

### 3. 提取清单 (required, wait)

Output this block, then **stop**. No Edit/Write until 确认 / 可以 / 执行.

```markdown
## 提取清单（help）

【主题】…
【读者】设计器用户（不登录 /help）
【来源】
- path — 抽了哪条可见规则
【将写入】
- `frontend/help/src/guidelines.ts` id: `…` path: `/…`
- `NAV_TREE` 挂到哪个门户菜单（例：DW → Function Units → Table Design）
- 源界面 `DesignerHelpLink`（问号跳 `/help/…`）
- view + i18n keys（en / zh-CN / zh-TW）
- 需要截图时：`frontend/help/public/guides/…png`（打码邮箱/姓名）+ section `figure`
【章节】标题 — 一句话
【不写】agent-only / 实现细节
【已有页面】无 | 将更新 `computed-fields` 等

回复 **确认** 后我再改 `frontend/help`。
```

Every 【来源】 line must be a real file you opened. If a rule is only in an agent skill, say so and rewrite; do not paste the skill.

### 4. After confirm — write like computed-fields

Copy the pattern in:

- `frontend/help/src/views/ComputedFieldGuide.vue` / `EmailSendGuide.vue`
- `frontend/help/src/components/GuideArticle.vue`
- `frontend/help/src/guidelines.ts` (`GUIDELINES` + `NAV_TREE`)
- `frontend/help/src/i18n/locales/{en,zh-CN,zh-TW}.ts`

Checklist:

- [ ] New `id` kebab-case; `path` `/that-id`
- [ ] Register in `GUIDELINES` (titleKey + summaryKey + lazy view)
- [ ] Add a `NAV_TREE` leaf under the **same menu** as the product (portal sidebar or Function Unit tab). Empty menus stay grey, no fake pages.
- [ ] Add `DesignerHelpLink` on the source screen (dialog title / toolbar / properties heading), `target=_blank` to `/help/<id>` (rule `help-guideline-link`)
- [ ] View uses `GuideArticle`; sections may set `anchor` and `figure`
- [ ] Figures: capture the real UI (`frontend/scripts/capture-help-guide-images.mjs` or equivalent), store under `frontend/help/public/guides/`, caption in three locales
- [ ] `data-testid="…-guide-page"` on the article
- [ ] i18n **three locales in the same change**; no raw `${token}` in locale strings
- [ ] Voice: what the user **ticks / types / saves**; short sentences; examples as field names they would type
- [ ] Do not add Element Plus; styles already in `help.css`

Do not create a second docs stack (Mintlify, AGENTS.md, extra markdown site).

### 5. Verify (after write)

- `cd frontend/help && pnpm run build`
- Optional: Playwright `frontend/scripts/verify-help-portal.mjs` after Docker help+edge rebuild
- Quote screenshot paths under `frontend/developer-workstation/verification-screenshots/` if you captured UI

## Voice

| Do | Don't |
|---|---|
| “Tick Computed, open Formula, then save the table.” | “`ComputedFieldEvaluator` then persists…” |
| “If BU and Role are both empty, only System Administrator sees the view.” | Copy mermaid of Portal services |
| Formula samples: `price * quantity` | Internal table prefixes `dw_` unless the UI shows them |

## Invoke

```text
/extract-help-guideline
主题：View 访问管控
```

or: `按 extract-help-guideline 提取邮件模板 guideline`
