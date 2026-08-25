# Source map — extract-help-guideline

Where to **read** product facts. Do not copy agent-only files into `/help/`.

## Portal files (write target)

| File | Role |
|------|------|
| `frontend/help/src/guidelines.ts` | Catalog + `NAV_TREE` (mirrors DW / Admin / Portal menus) |
| `frontend/help/src/views/*Guide.vue` | Article (use `GuideArticle.vue`) |
| `frontend/help/public/guides/` | UI screenshots referenced by `figure.src` |
| `frontend/help/src/i18n/locales/en.ts` | Canonical English |
| `frontend/help/src/i18n/locales/zh-CN.ts` | Simplified |
| `frontend/help/src/i18n/locales/zh-TW.ts` | Traditional |
| `frontend/help/src/App.vue` | Shell matches the three portals (left nav + red header). Nav is `NAV_TREE`. |

Canonical live URL: `http://localhost:3000/help/<id>` (edge). Old DW path `/dev/help/computed-fields` redirects only for computed-fields.

## Candidate topics (scan if user asks)

| Topic | Start here |
|-------|------------|
| Computed fields | `frontend/help` existing article; `frontend/developer-workstation/src/components/designer/ComputedFieldEditor.vue`; `table.computedField` in DW i18n; `backend/platform-common/.../computedfield/` only for **user-visible** error meanings |
| View access | `.cursor/skills/view-access-control/SKILL.md` (rewrite); View Design panel + view i18n in DW; Portal view list behavior described in that skill |
| Email templates | `EmailTemplateDesigner.vue`, `EmailBodySplitEditor.vue`, `emailTemplate` i18n; send-time empty-subject messages only if designers see them |
| Form / sub-table | Form designer i18n, `SubTableField` labels — extract **how to bind**, not MI invariant I1–I7 |
| Relation tables | Admin Center relation computed-field i18n (standalone tables, no MAIN/SUB) |

When scanning: grep designer `*Guide*`, `dialogHint`, `placeholder`, and `ElMessage` / i18n error keys the **user** can see.

## Rewrite rules from agent skills

Allowed: `view-access-control`, `function-unit-portability` **user consequences** (import missing a setting).

Forbidden to paste: `code-review`, `secure-coding-sast`, `fallback-audit`, playbook 任务整理, Docker compose rebuild steps.

## i18n key shape

```ts
guides.myTopic: { title, summary }
myTopicGuide: { pageTitle, intro, sectionTitle, sectionBody, sampleHint? }
```

`vue-i18n` must not contain a raw `${name}` in the locale file; describe tokens in words or split strings.

## One article = one designer job

If the topic would exceed ~12 sections, split into two `GUIDELINES` ids and say so in the 提取清单.
