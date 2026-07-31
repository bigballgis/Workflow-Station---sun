# .kiro/specs — 正文已迁出

**规格正文（`requirements.md` / `bugfix.md` / `design.md` / `tasks.md`）已于 2026-07-30 迁移到
[`docs/specs/`](../../docs/specs/README.md)**，作为项目文档统一归集的一部分。

本目录现在只保留 Kiro 自身的 specId 状态文件（`.config.kiro`），用途：

| 目录情况 | 含义 |
|----------|------|
| 只有 `.config.kiro`，且 `docs/specs/` 下有同名目录 | 正文已迁出，此处仅留 Kiro 工作流状态 |
| 只有 `.config.kiro`，`docs/specs/` 下无同名目录 | 当年立过项但从未写入正文的空题目（共 21 个），保留以免重复立项 |

新增或修改规格请直接写在 `docs/specs/<name>/` 下，并在
[`docs/specs/README.md`](../../docs/specs/README.md) 索引中登记一行。

> 同目录下的 `.kiro/issues.md` 与 `.kiro/issues/` **未迁移**——它们是活问题台账，
> 被 `.cursor/rules`、skills 与 `.github/pull_request_template.md` 按固定路径引用。
> `.kiro/steering/` 与 `.kiro/skills/` 是 `sync-cursor-rules.mjs` 生成的镜像（已 gitignore）。
