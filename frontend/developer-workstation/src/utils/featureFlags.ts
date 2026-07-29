/**
 * 前端功能开关（编译期常量，改完需重新构建前端）。
 */

/**
 * AI Generate（功能单元 AI 生成）—— **2026-07-28 关闭**。
 *
 * 该功能的模型调用原先走 Activepieces flow：DW 后端 POST `<ap>/api/v1/webhooks/<flowId>/sync`
 * → flow 内 `piece-ai` 的 run_agent → deepseek。该链路已废弃，替代实现尚未落地；保持开启只会让
 * 用户点开面板、发一轮对话、然后等到 300s 超时（`service-task.ai-generation.timeout-seconds`）。
 *
 * 关掉的是**入口**：按钮与 `<AiPanel>` 都不渲染，因此 `src/api/aiGeneration.ts` 里的任何请求都不会
 * 发出（AiPanel 的数据拉取挂在 `props.visible` 的 watch 上，组件不创建就不会触发）。
 *
 * 重新启用需要**两侧同时打开**，只改一侧会得到 404：
 *   1. 这里改回 `true` 并重新构建 developer-workstation 前端；
 *   2. 后端 `ai-generation.enabled`（环境变量 `AI_GENERATION_ENABLED`）置 `true`——
 *      `AiGenerationController` 带 `@ConditionalOnProperty`，为 false 时整个控制器不注册。
 */
export const AI_GENERATION_ENABLED = false
