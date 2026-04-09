# Demo：界面语言与种子数据（英文）

外资银行 Demo 的约定：**用户可见的界面文案与种子数据以英文为准**，避免「英文 UI + 中文种子」造成演示违和或后端按用户 `language` 偏好返回非英文消息。

## 前端语言

| 层面 | 约定 |
|------|------|
| 源码 | 默认语言在 `frontend/<app>/src/i18n/index.ts`（`locale` / `fallbackLocale` 多为 `en`） |
| 构建物 | 语言打进静态资源；**不存在**通过 Docker/K8S 环境变量 `LOCALE` 切换 |
| 修改语言 | 改源码后重新执行 `npm run build` 并重建前端镜像 |

## 种子数据与账号

- `deploy/init-scripts/` 下种子脚本：用户姓名、部门、流程名称等 **建议英文**。
- 若用户表或 JWT 载荷含 **`language`** 等偏好字段，Demo 环境建议设为 **`en`**，与英文 UI 一致。

## 交叉引用

- `BUILD_GUIDE.md` §2.5（表格汇总）
- `deploy/environments/dev/docker-compose.dev.yml` 文件头注释
- `deploy/k8s/deployment-frontend.yaml` 文件头注释
- `deploy/README.md`「Demo：界面语言与种子数据」
