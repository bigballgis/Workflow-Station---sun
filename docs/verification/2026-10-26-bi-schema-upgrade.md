# 本地 Portal 首页 BI 502 修复验证

- 复现：2026-10-26 首页 `/api/portal/bi/dashboards` 返回 502，页面显示 Service temporarily unavailable, please try again later。Admin Center 日志显示 `bi_dashboard_assignment.enabled does not exist`。
- 根因：本地数据库未应用已有 97/98 号 BI 升级，代码需要的两个 enabled 列及 view_id 缺失。
- 处理：先 pg_dump 备份两张绑定表，再顺序执行已有 `97-bi-assignment-view-scope.sql`、`98-bi-assignment-retain-unavailable-table.sql`；两份脚本均再次执行验证幂等。未修改业务代码或既有 SQL。
- 备份：`C:/Users/Li Long/AppData/Local/Temp/workflow-bi-before97-2825a1d4f2a1466a9603ae048122e803.dump`，9531 字节。
- 已重启 Admin Center 和 User Portal，两个容器均 healthy。

## 红→绿证据

用同一个 developer 密码直连 helper 和同一 BU 上下文执行 `frontend/scripts/verify-bi-schema-upgrade.mjs`：

- 不带 BU 参数：修前 HTTP 502 / BI_UPSTREAM_UNAVAILABLE；修后 HTTP 200 / JSON `[]`。
- 显式带受影响 BU 参数：修前 HTTP 502 / BI_UPSTREAM_UNAVAILABLE；修后 HTTP 200 / JSON `[]`。
- 首页实际导航：修前捕获 BI 502 及对应提示；修后无 5xx、无浏览器运行异常，无该提示，任务和申请统计正常展示。
- 查库：两表 enabled 为 BOOLEAN NOT NULL DEFAULT TRUE；view_id 已加入；旧表绑定外键已按 98 移除，Dashboard/FU 外键及唯一约束保留。
- 数据：升级前两张绑定表均为 0 行；升级后仍为 0 行，legacy 备份表为 0 行。没有停用、扩展或丢弃现有 BI 绑定。
- 重启后的服务日志未出现缺列、SQLGrammarException 或 BI 上游不可用异常。

证据路径（均相对于 `frontend/user-portal/verification-screenshots/`）：

- `2026-10-26_bi-schema-before.png` / `2026-10-26_bi-schema-after.png`
- `2026-10-26_bi-schema-before.json` / `2026-10-26_bi-schema-after.json`

复跑：在 `frontend` 设置 `BI_VERIFY_BU` 为受影响 BU ID，执行 `node scripts/verify-bi-schema-upgrade.mjs`。默认验证修复后的响应；`BI_VERIFY_PHASE=before` 仅用于复现尚未升级的环境。

## 未验证项

本地没有配置 BI Dashboard Assignment / Data View Assignment，未验证非空绑定迁移和实际 Superset 看板嵌入。本次只处理本地数据库升级，不涉及业务代码重建或其他环境升级。

## 审查

本轮仅新增只读验证脚本、证据和问题记录；既有 97/98 脚本内容未改动。脚本语法检查通过；新增文件无凭证、token 或 API 原始业务载荷。前一轮 Change History 的 staged changes 保留，未提交或推送。
