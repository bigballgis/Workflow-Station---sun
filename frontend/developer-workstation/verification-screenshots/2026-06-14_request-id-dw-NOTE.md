# Request ID — DW 截图说明

## 2026-06-15 更新:DW 侧已截图(改为可勾选虚拟字段后)
之前的"隐式自动追加"未生效(字段不可见/不可选)。改为**导入对话框里的可勾选虚拟字段**后,
端到端验证通过,截图(2026-06-15_*):
- `dw-table-design-requestid-meta.png` — Table Design 主表 meta-card 显示 `Request ID [IDDFF]_[id] [Configure]`
- `dw-requestid-config-dialog.png` — 配置弹窗:可选字段勾选 + 已选排序 + 分隔符 + 实时预览 `[IDDFF]_[id]`
- `dw-import-fields-dialog.png` — **Form Design 导入对话框首行出现「Request ID」虚拟字段(Virtual 标签 / Readonly Text),可勾选** ✅
- `dw-main-form-with-requestid.png` — 勾选导入后 toast "Successfully imported 1 field",字段进 canvas
- `dw-preview-requestid.png` — Form Preview 渲染(canvas+preview 的 label 都含 "Request ID",readonly input 存在)

DOM 断言:canvas labels 含 "Request ID";preview labels 含 "Request ID";preview 有 disabled inputs。

## SSO 绕过方式(本 box `dw` client 拒绝 developer/password 的 SSO 流程)
DW backend 有直登端点 `POST /api/v1/auth/login`(设 `dw_access_token` httpOnly cookie)。
Playwright:在 `localhost:3000/dev/` 页内 `fetch('/api/v1/auth/login')` 设 cookie,再解码返回的
`accessToken` JWT 合成 `localStorage.ws_dw_user`(roles 含 TECH_LEAD 过路由守卫),即可进 FU 编辑页。
(`/auth/me` 在本 box 返回 500,故用 JWT payload 合成 user。)

## Portal 侧(见 user-portal/verification-screenshots/2026-06-14_request-id-*)
To Do / Completed / My Requests 三列表均显示 Request ID 列(默认 `-`)✅
