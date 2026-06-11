# UI verification screenshots (User Portal)



Playwright 截图输出目录。**验证后保留，不要删除。**



## MI 完整回归（单元 + 截图）



```bash

cd frontend && npm run regression:mi

```



6 个场景、每个脚本 MUST 写入本目录 PNG。场景 ↔ 单测映射见 `../MI_REGRESSION.md`。



## 通用截图



```bash

npm run verify:screenshot -- --app portal --url "http://localhost:3000/portal/..." --name my-feature

```



命名：`YYYY-MM-DD_{slug}.png`。详见 `.cursor/rules/frontend-screenshot-verification.mdc` 与 `performance-change-safety.mdc`。

