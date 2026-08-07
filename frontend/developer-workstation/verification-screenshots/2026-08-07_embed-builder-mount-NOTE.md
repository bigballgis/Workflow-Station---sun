# 内嵌 Activepieces builder 挂载验证（embed bundle 重建后）

- Script: `frontend/scripts/_verify-embed-builder.mjs`（`_` 前缀，按 .gitignore 约定不入库）
- Screenshots: `2026-08-07_embed-builder-no-flow.png`、`2026-08-07_embed-builder-mounted.png`
- 跑法：`cd frontend && FU_ID=<功能单元id> [CREATE_FLOW=1] node scripts/_verify-embed-builder.mjs`

## 为什么要单独验这一条

embed bundle 是**独立的 lib-mode 产物**（`activepieces/dist/packages/web-embed/`，经 DW 的
`prebuild` 钩子拷进 `public/service-task-builder/`），不随 AP 镜像发布。所以「AP 自己好了」
不代表内嵌画布好了，反过来也一样；资源 200 也只说明文件在，不说明挂得起来。

## 2026-08-07 结果（bundle `mount-builder-HZ1g21gi.mjs`，6.99MB）

- Shadow DOM 210 节点、`hasCanvas: true`、console 零报错
- `/dev/service-task-builder/*` 与 `/api/ap/*` 全 200，含
  `/api/ap/v1/users/<影子用户>` —— L7 per-user 身份在内嵌里同样生效
- 红色主题与点阵画布渲染正常 ⇒ `:root`→`:host` 那条 CSS 改写在新 bundle 上仍然成立
  （这条一坏是**整体静默降级**，不会报错，只会变成没有主题变量的白板）

## 两个前提，缺一个就白跑

1. **服务任务必须已绑 flow**：没绑时 Automation 页只渲染「Create automation flow」，
   画布根本不挂（`selectedFlowId` 为空），不是坏了。脚本的 `CREATE_FLOW=1` 会真的建一个
   AP flow 并把 `ap:flowId` 写进该功能单元的 BPMN —— 只在需要看挂载效果时开。
2. 用的是仓库自己的 `playwright-login.mjs`（dev fixture 账号），不是手输密码。
