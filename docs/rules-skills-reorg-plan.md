# Rule / Skill 整改方案

> 现状分析 + 归并映射表。**本文档只描述改法,不含代码改动。** 审阅后再决定执行。
> 生成日期:2026-07-14

## 背景与约束

- 规则正文的**单一事实来源** = `.cursor/rules/*.mdc`(29 条)。
- `CLAUDE.md` 里的 `@import` 块由 `.claude/scripts/sync-cursor-rules.mjs`(SessionStart 钩子)**自动生成**,按 frontmatter 路由:
  - `globs` 含 `deploy/` / `Dockerfile` / `docker-compose` / `nginx` / `k8s` → `deploy/CLAUDE.md`
  - 否则 `globs` 含 `frontend` → `frontend/CLAUDE.md`
  - 否则 `globs` 含 `backend` → `backend/CLAUDE.md`
  - 否则(无 glob / 跨切面 / `alwaysApply:true`)→ 根 `CLAUDE.md`
- **推论**:改一条 rule 的 `alwaysApply` / `globs`,它在哪个 CLAUDE.md 出现会自动重路由——**不要手改 CLAUDE.md**,改 `.mdc` 源即可,下次会话自动同步。
- Skill 正文在 `.claude/skills/*/SKILL.md`(7 个),按 `description` 触发词按需加载。

## 判定标准(rule vs skill)

| | rule(常驻) | skill(按需) |
|---|---|---|
| 本质 | 写代码时**必须始终满足**的约束 / 红线 / 命名 | 遇到**特定场景才跑**的流程 / 核对清单 |
| 加载 | always-on 或按 glob 自动进上下文 | 靠 description 触发词加载 |
| 例子 | 分层、表名前缀、禁止吞错、i18n 同步 | 截图验证、SAST 自查、审计重扫、FU 导入导出核对 |

**标杆范式**:`fallback-audit`(skill=按需重扫) 配 `error-handling-governance`(rule=常驻约束)。其余都照此拆:**约束留 rule,流程转 skill。**

---

## 问题清单(四类)

### A. 碎片化 —— 同一概念散在多条 rule(最高优先级)

| # | 概念 | 现状散落位置 | 建议唯一权威 |
|---|---|---|---|
| A1 | 性能阈值 + MI 门禁 | `performance-guardrails` + `performance-change-safety` + `code-quality-standards §6` | 合并为一条 `performance`(见下方 §合并 3) |
| A2 | 表名前缀 `dw_/ac_/up_/we_` | `domain-model` + `jpa-entity` + `code-quality-standards §3` + `project-context` + backend/CLAUDE.md | `domain-model`(命名锚点),其余改一行指针 |
| A3 | i18n 三语言同步 | `i18n-rules` + `ai-guardrails` + `cross-cutting` + `change-playbook` + `code-quality-standards §5` | `i18n-rules`,其余改一行指针 |
| A4 | 禁止吞错 / 兜底 | `error-handling-governance` + `security-guard` + `code-quality-standards §7` + `ai-guardrails` | `error-handling-governance`,其余改指针 |
| A5 | 分层 + 统一 `ApiResponse<T>` | `backend-architecture` + `code-quality-standards §3` + `project-context` + `api-design` | `backend-architecture`(分层)+ `api-design`(ApiResponse),其余指针 |
| A6 | Schema / init-scripts | `deployment-infra` + `init-scripts-append-only` + `jpa-entity` + `json-row-storage` + deploy/CLAUDE.md | `deployment-infra`(总纲)+ `init-scripts-append-only`(细则),其余指针 |
| A7 | 爆炸半径 / 关联文件追踪 | `cross-cutting` + `reasoning-protocol` + `change-playbook` | `cross-cutting`,另两条引用它 |
| A8 | FK/PK + MI 子表运行时 | `form-preview-fk-pk-runtime`(526行) + `portal-mi-subtable-my-request` + `performance-change-safety` | 保持三分但**去掉交叉重述**,统一 #1435/1438/1440/1441 指向一处 |

### B. rule 与 skill 重复

| # | rule | skill | 建议 |
|---|---|---|---|
| B1 | `portal-dialog-form-labels.mdc`(24行,已含"见 skill"指针) | `portal-dialog-form-labels` | **保留**——这是正确范式(rule=红线摘要+glob 触发,skill=完整流程)。仅建议给二者加一致命名说明,避免同名混淆 |
| B2 | `frontend-screenshot-verification.mdc`(always-on,近逐字重复) | `verify-ui-fix-with-screenshot` | rule **瘦身**为一段触发指针(何时截图 + 指向 skill),流程正文全归 skill |
| B3 | `security-guard.mdc` | `secure-coding-sast` | 分工写清:rule=通用红线,skill=如何写才不被 Checkmarx 报警;删两者 XXE/SQL/v-html 重叠段,skill 引用 rule |
| B4 | `json-row-storage-no-physical-tables §DDL` | `secure-coding-sast rule2` | 禁动态 DDL 只在 `json-row-storage` 讲全,skill 一行引用 |
| B5 | — | `view-access-control` + `function-unit-portability` + `function-unit-version-rollback` | 三 skill 覆盖同一 FU 生命周期;抽出共享的 accessRules 序列化描述到 `function-unit-portability`,另两个引用 |

### C. 写成 always-on rule、实为按需流程 → 转 skill

> **执行结论(2026-07-14,深读后推翻 C1/C2/C3 转换)**:C1/C2/C3 **保持 always-on rule,不转 skill**。
> 判据:skill 靠关键词**被用户唤起**;而这三条是**每轮主动生效的 disposition**——
> 「别臆测、读 Docker 日志」「改了可部署单元就 build+rebuild 才算完」「边工作边扫 issue」——
> 转成 skill 后 Claude 需「记得去调用」,会静默丢掉主动性,属真实行为回归。
> 与 `verify-ui`/`fallback-audit`(用户说了才跑)本质不同。C4 已按 B2 瘦身完成。
> 三者间的 `.kiro` 记录时机重叠已由 `debug-mode-docker-workflow §2` 显式声明优先级 + 指回
> `issue-radar`,属可接受的分层覆盖,无需再改。

| # | rule(现 alwaysApply:true) | 原建议 | 执行结论 |
|---|---|---|---|
| C1 | `debug-mode-docker-workflow` | 转 skill | **保留 rule**(always-on disposition) |
| C2 | `change-playbook` | 转 skill | **保留 rule**(每轮变更类型决策aid) |
| C3 | `issue-radar` | 转 skill | **保留 rule**(持续扫描 disposition) |
| C4 | `frontend-screenshot-verification` | rule 瘦成指针 | ✅ 已完成(B2) |

### D. `alwaysApply:true` 但实际很窄 → 改 `false` + 依赖 glob 自动路由

| # | rule | 现状 | 建议 |
|---|---|---|---|
| D1 | `performance-change-safety` | always-on,全是 user-portal MI 专属 | `alwaysApply:false` + glob `frontend/user-portal/**`(合并进 A1 后一并处理) |
| D2 | `portal-mi-subtable-my-request` | 有 glob 又 always-on | 去掉 always-on,仅靠 glob |
| D3 | `portal-design-parity` | always-on,Portal/DW 专属 | 去掉 always-on,仅靠 glob(portal+dw) |
| D4 | `json-row-storage-no-physical-tables` | always-on,只在改 Relation/deploy 时相关 | 去掉 always-on + 加 glob(relation table / deploy 路径) |
| D5 | `testing.mdc`(**反向 bug**) | 自身 `alwaysApply:false`+globs,却被根 CLAUDE.md `@import` 进 always-on | 核对 sync 脚本为何把它路由进 root;应仅按 test glob 加载 |

### E. 具体不一致(改动小、收益大,建议最先做)

| # | 问题 | 位置 | 建议 |
|---|---|---|---|
| E1 | Flyway 表述冲突 | `project-context.mdc:52` 说"双轨/Flyway 默认启用" vs `deployment-infra.mdc:57-58` 说"Flyway 已清退,禁止双轨" | 以 deployment-infra 为准(与 memory `flyway-retired-init-scripts-source` 一致);删/改 project-context 那行为一行指针 |
| E2 | skill 路径混用 | `frontend-screenshot-verification.mdc:9-10` 引 `.cursor/skills/...`;实际 skill 在 `.claude/skills/...`;`function-unit-version-rollback` 用 `.claude/`,`function-unit-portability` 用 `.cursor/` | **决策改为以 `.cursor` 为单源**;`.claude/skills` 由 sync 脚本镜像生成 |

---

## 执行结果(2026-07-15 已落地)

| 批 | 内容 | 状态 |
|---|---|---|
| E1 | Flyway 表述以「已清退」对齐;改 `project-context.mdc` + 重写 `docs/schema-and-migration.md`(原文档是双轨说法的源头,已纠正) | ✅ |
| E2 | 7 个 skill 合并到 **`.cursor/skills` 单源**(补齐 `.cursor` 缺的 `fallback-audit`/`secure-coding-sast`;`verify-ui`/`dialog-labels` 取 `.claude` 的更完整版;统一内部路径引用为 `.cursor`);扩展 `sync-cursor-rules.mjs` 增加 **skill 镜像**(`.cursor/skills → .claude/skills` + prune);加 `.claude/skills/README.md` 防手改 | ✅ |
| A2–A7 | 表名前缀(唯一权威 `domain-model`,3 处改指针)、schema(指 `init-scripts-append-only`)、分层/ApiResponse(`project-context` 概览改指针)、爆炸半径(`reasoning-protocol` 指 `cross-cutting`) | ✅ |
| A1 | MI 回归门禁 4 处重复 → 唯一权威 `performance-change-safety`,`performance-guardrails` 改指针(P95 阈值表因行不同保留互补) | ✅ |
| B2/B3 | 截图 rule 从 90 行瘦成常驻约束(流程归 skill);`security-guard` 加指向 `secure-coding-sast` 的指针 | ✅ |
| B4/B5 | 已是「skill 指回 rule/owner」的健康分层,无需再改 | ✅(验证) |
| C1/C2/C3 | **推翻转 skill**——深读后判定为每轮主动生效的 disposition,保留 always-on rule(理由见 §C) | ✅(保留) |
| D1–D4 | `performance-change-safety`/`portal-mi-subtable`/`portal-design-parity`/`json-row-storage` 去 always-on + 补 glob | ✅ |
| D5 | **sync 脚本 bug**:`classify()` 把 cross-cutting glob 的非 always 规则误路由进 root。改为 `classifyTargets()` 多目标路由 + 按扩展名推断 → `testing.mdc` 现正确落 frontend+backend,不再 always-on。root 常驻规则 15→13 条 | ✅ |

## 遗留债务(Flyway 残留清理 — 2026-07-15 已完成)

E1 定案「Flyway 已清退」后,仓库过时/失效的 Flyway 残留已一并清理:

1. ✅ **`docs/guides/function-unit-development-guide.md` §16** —— 原写 `enabled: true` 把 Flyway 描述为在用,已整段改写为「init-scripts 唯一来源(Flyway 已清退)」;同文件内 TOC、示例 `application.yml`(改 `enabled: false`)、`ddl-auto` 注释、env-var 表(删 `SPRING_FLYWAY_ENABLED` 行)、V309 历史引用一并对齐。
2. ✅ **`backend/admin-center/.../db/migration/admin-center/V214、V215.sql`** —— 核实其 DDL 全部已镜像进 `deploy/init-scripts/00-schema/`(V214→`49-sys-email-monitor-rules.sql`,V215→`46-sys-email-connections.sql`),确认是永不执行的死码,已 `git mv` 归档到 `docs/legacy-flyway-migrations/admin-center/admin-center/`,并删除遗留空目录。
3. ✅ 复核全仓:无 live `db/migration` 目录、无 yml `enabled: true`、无 pom Flyway 依赖 —— 文档与现实全一致。
4. `docs/database/flyway-unification-plan.md` 等 —— 历史决策记录(方案①被否、方案②被采纳),含「双轨」属正常,**保留不动**。

## 执行分批建议(按性价比)

- **批 1(最先,零风险)**:E1 + E2。纯文本对齐,不动结构。
- **批 2(收益最大)**:A2–A7 去重收敛——每个概念留唯一权威文件,其余改一行指针。逐条 `.mdc` 编辑,不动 sync 脚本。
- **批 3(结构调整)**:A1 性能三合一 + D1 一并处理;B2/B3/B4 rule 瘦身;D2/D3/D4 改 `alwaysApply`。改后跑一次 SessionStart(或手动 `node .claude/scripts/sync-cursor-rules.mjs`)核对 CLAUDE.md import 块正确重路由。
- **批 4(rule→skill 归位,最谨慎)**:C1/C2/C3 转 skill。需新建 `.claude/skills/*/SKILL.md`,并从 `.cursor/rules/` 删除或瘦身对应 `.mdc`,再验证 sync 脚本不再把它们注入 always-on 块。
- **批 5(可选)**:B5 FU 三 skill 去交叉重述;D5 排查 testing.mdc 路由 bug。

## 验证方式

每批改完:
1. `node .claude/scripts/sync-cursor-rules.mjs` 手动跑一次,`git diff CLAUDE.md backend/ frontend/ deploy/CLAUDE.md` 确认 import 块符合预期。
2. 全文搜任一被收敛概念(如"表名前缀""regression:mi"),确认只剩一处正文 + 若干指针。
3. 新增/转的 skill 用其 description 触发词自测能否被 Skill 工具识别。
