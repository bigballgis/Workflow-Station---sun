# Project Agent Skills — 唯一真源

本目录是项目 skill 的**唯一真源**(入库、随 clone 分发)。

- **Cursor** 直接读本目录 `.cursor/skills/`。
- **同步镜像**（由 `.claude/scripts/sync-cursor-rules.mjs` 生成，勿手改）：
  - Claude Code → `.claude/skills/`
  - GitHub Copilot → `.github/skills/`
  - Kiro → `.kiro/skills/`
- 触发：打开工作区 `folderOpen`、Claude/Copilot SessionStart、或手动跑同步脚本。
- 新增 / 修改 skill：**只改本目录**，再同步并提交镜像。详见 `docs/ai-rules/ai-guidance-sync.md`。

| Skill | 用途 |
|-------|------|
| [feature-design-plan](feature-design-plan/SKILL.md) | 功能/模块设计 Plan（方案权衡、影响面、分期、验收）；确认后再 playbook 执行；细则见 [reference.md](feature-design-plan/reference.md) |
| [code-review](code-review/SKILL.md) | 只读、证据驱动的 staged/PR 审查编排（规则/skill、编译测试、安全隐私、FU 生命周期与通用性）；细则见 [reference.md](code-review/reference.md) |
| [config-over-heuristics](config-over-heuristics/SKILL.md) | 把「猜列名/表名/字段名」的运行时判据换成读设计器配置，并安全删掉兜底（影子探针、上下文可达性、返回值兼任开关、端到端验证） |
| [fallback-audit](fallback-audit/SKILL.md) | 复扫静默兜底反模式,对比 2026-07 基线量化治理降幅 |
| [function-unit-portability](function-unit-portability/SKILL.md) | FU 导出/导入/clone/版本快照必须带全部设计配置 |
| [function-unit-version-rollback](function-unit-version-rollback/SKILL.md) | DW 版本回滚必须还原目标版本完整 FU 设计 |
| [portal-dialog-form-labels](portal-dialog-form-labels/SKILL.md) | 三前端弹窗 label 不折行、不被输入框遮挡、输入框对齐 |
| [secure-coding-sast](secure-coding-sast/SKILL.md) | 从 Checkmarx/Cyberflows 结论提炼的编码规则,减少 SAST 告警 |
| [verify-ui-fix-with-screenshot](verify-ui-fix-with-screenshot/SKILL.md) | UI 改动后 build+重建 Docker+Playwright 截图验证 |
| [view-access-control](view-access-control/SKILL.md) | Main Table View BU/Role 访问 + SYS_ADMIN bypass(空配置仅 admin 可见) |
| [extract-help-guideline](extract-help-guideline/SKILL.md) | 提取/维护 `/help/` 给人看的指南（deep-documentation 质量：分层、精确界面、流程图、交叉链接、`llms.txt`）；细则见 [reference.md](extract-help-guideline/reference.md) |
