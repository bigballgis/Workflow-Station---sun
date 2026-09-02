# Source map — extract-help-guideline

Where to **read** product facts. Do not copy agent-only files into `/help/`.

Deep-documentation quality applies **inside** `frontend/help` only: layered
articles, exact UI surfaces, diagrams/flow, cross-links, `llms.txt`, same-task
sync. Canonical human site remains `/help/`. Do not add a second docs tree.

## Portal files (write target)

| File | Role |
|------|------|
| `frontend/help/src/guidelines.ts` | Catalog + `NAV_TREE` (mirrors DW / Admin / Portal menus) |
| `frontend/help/src/views/*Guide.vue` | Article (use `GuideArticle.vue`) |
| `frontend/help/src/components/GuideArticle.vue` | Layout: intro, flow, jump nav, how-to blocks (`intentKey`…), figures, block samples, fail list, related |
| `frontend/help/public/guides/` | UI screenshots referenced by `figure.src` |
| `frontend/help/public/llms.txt` | LLM index of this portal (keep in sync with `GUIDELINES`) |
| `frontend/help/public/llms-full.txt` | Compact English bundle of help articles (not the whole repo) |
| `frontend/help/src/i18n/locales/en.ts` | Canonical English |
| `frontend/help/src/i18n/locales/zh-CN.ts` | Simplified |
| `frontend/help/src/i18n/locales/zh-TW.ts` | Traditional |
| `frontend/help/src/App.vue` | Shell matches the three portals. Nav is `NAV_TREE`. |

Canonical live URL: `http://localhost:3000/help/<id>` (edge). Old DW path
`/dev/help/computed-fields` redirects only for computed-fields.

After replacing PNGs, bump `GUIDE_FIGURE_REV` in `GuideArticle.vue`. Capture
script: `frontend/scripts/capture-help-guide-images.mjs` (requires `HELP_GUIDE_FU_ID`).
Redact in `redact-help-guide-pii.mjs`.

## Article layers (required)

Each article should let a reader answer without opening source:

1. **Overview** — what this job is, what it is not
2. **Order of work** — `flow-keys` (visible steps)
3. **How-to** — real screenshots from the demo Function Unit. Script **effect**
   methods use `intentKey` / `beforeKey` / `afterKey` / `noteKey` on
   `GuideArticle` (see SKILL.md Readability gates): one card with user labels
   **Default / After you run the sample / Note**, not writer taxonomy. Visual
   effects use Form Preview figures.
4. **Field catalog** — one sample row per visible control: on-screen label
   (`code`), meaning + required/blank (`hintKey`). List dropdown choices. If a
   value is filled by Admin Center, say so on that row.
5. **Exact samples** — field/table names that appear on those screenshots
6. **Failures** — save blockers the designer can see
7. **Related** — other `/help/` ids, not a paste of their full text

Demo Function Unit for figures (when present): Purchase Request. Main table
`help_pr`, sub-table `help_pr_line`. Formula samples must use those names
(`quantity * unit_price`, `SUM(help_pr_line.line_total)`, `end_date - start_date`,
`IF(grand_total > 5000, "Y", "N")`). Email samples: PR Approved Notice, Vendor
quote to PR.

## Candidate topics (scan if user asks)

| Topic | Start here |
|-------|------------|
| Computed fields | Existing `/help/computed-fields`; `ComputedFieldEditor.vue`; `table.computedField` i18n; `backend/platform-common/.../computedfield/` only for **user-visible** error meanings |
| View access | `.cursor/skills/view-access-control/SKILL.md` (rewrite); View Design panel + view i18n |
| Email send | `EmailTemplateDesigner.vue`, `EmailBodySplitEditor.vue`, `SendTaskProperties.vue`, `emailTemplate` / `connection` i18n |
| Email monitor | `EmailMonitorDesigner.vue`, `StartEventEmailMonitorSection.vue`, `emailMonitor` i18n |
| Form / sub-table | Form designer i18n — extract **how to bind**, not MI invariant I1–I7 |
| Form events | Existing `/help/form-events` (+ basic/extend/layout); `HermesEventConfig.vue` / `HermesFnConfig.vue`; designer event i18n. Effect methods = how-to skeleton; `$inject` / Create lists = reference |
| Relation tables | Admin relation computed-field i18n (no MAIN/SUB) |

When scanning: grep designer `*Guide*`, `dialogHint`, `placeholder`, and
`ElMessage` / i18n error keys the **user** can see.

## Rewrite rules from agent skills

Allowed: `view-access-control`, `function-unit-portability` **user consequences**.

Forbidden to paste: `code-review`, `secure-coding-sast`, `fallback-audit`,
playbook 任务整理, Docker compose rebuild steps.

## i18n key shape

```ts
guides.myTopic: { title, summary }
myTopicGuide: { pageTitle, intro, flowTitle, flow1…, relatedTitle, sectionTitle, sectionBody, sampleHint? }
```

`vue-i18n` must not contain a raw `${name}` or `{ }` in the locale file
(named interpolation). Literal `{`: `{'{'}`. Literal `}`: `{'}'}`. A JUEL
token in help copy is `${'{'}fieldName{'}'}`, never `${fieldName}`.

Field-catalog `hintKey`s: meaning + required vs optional + what blank does.
One `samples` row per visible control. Name every dropdown choice. If Host /
Port / TLS are filled in Admin Center, still list that row.

## One article = one designer job

If the topic would exceed ~12 sections, split into two `GUIDELINES` ids and say
so in the 提取清单.
