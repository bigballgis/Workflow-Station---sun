# Project Agent Skills — 唯一真源

本目录是项目 skill 的**唯一真源**(入库、随 clone 分发)。

- **Cursor** 直接读本目录 `.cursor/skills/`。
- **Claude Code** 读 `.claude/skills/`——该目录由 `.claude/scripts/sync-cursor-rules.mjs`
  (SessionStart 钩子)从本目录**镜像生成**,**不入库**(见 `.gitignore`)。
- 新增 / 修改 skill:**只改本目录**;`.claude/skills` 下次会话自动同步,请勿手改。

| Skill | 用途 |
|-------|------|
| [fallback-audit](fallback-audit/SKILL.md) | 复扫静默兜底反模式,对比 2026-07 基线量化治理降幅 |
| [function-unit-portability](function-unit-portability/SKILL.md) | FU 导出/导入/clone/版本快照必须带全部设计配置 |
| [function-unit-version-rollback](function-unit-version-rollback/SKILL.md) | DW 版本回滚必须还原目标版本完整 FU 设计 |
| [portal-dialog-form-labels](portal-dialog-form-labels/SKILL.md) | 三前端弹窗 label 不折行、不被输入框遮挡、输入框对齐 |
| [secure-coding-sast](secure-coding-sast/SKILL.md) | 从 Checkmarx/Cyberflows 结论提炼的编码规则,减少 SAST 告警 |
| [verify-ui-fix-with-screenshot](verify-ui-fix-with-screenshot/SKILL.md) | UI 改动后 build+重建 Docker+Playwright 截图验证 |
| [view-access-control](view-access-control/SKILL.md) | Main Table View BU/Role 访问 + SYS_ADMIN bypass(空配置仅 admin 可见) |
