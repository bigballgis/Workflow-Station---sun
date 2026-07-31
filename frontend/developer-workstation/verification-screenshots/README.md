# UI verification screenshots (Developer Workstation)

Playwright 截图输出目录。**验证后保留，不要删除。**

```bash
cd frontend && pnpm run verify:screenshot -- --app dw --url "http://localhost:3000/dev/..." --name form-preview

# UserTask 多角色 assignee（Fixed BU / Initiator BU role）
pnpm run verify:dw:assignee-multi-role
# 可选 FU id：node scripts/verify-dw-assignee-multi-role.mjs 50006

# Sensitive Mask Props Editor（Input 打码面板）
pnpm run verify:dw:sensitive-mask-props
```

详见 `.cursor/rules/frontend-screenshot-verification.mdc`。
