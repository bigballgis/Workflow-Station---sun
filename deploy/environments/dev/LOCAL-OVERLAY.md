# 本地 Docker Overlay（不提交仓库）

用于 **本机专用** 的 Compose 扩展（如 Mailpit 假 SMTP），避免污染共享的 `docker-compose.dev.yml`。

## 文件约定

| 文件 | 是否提交 | 说明 |
|------|----------|------|
| `docker-compose.local.example.yml` | ✅ 是 | 模板，团队可参考 |
| `docker-compose.local.yml` | ❌ **否** | 实际本地配置（已在 `.gitignore`） |

## 首次启用 Mailpit

```powershell
cd deploy/environments/dev
copy docker-compose.local.example.yml docker-compose.local.yml
docker rm -f mailpit 2>$null
.\build-and-deploy.ps1 -Service developer-workstation
docker compose -f docker-compose.dev.yml -f docker-compose.local.yml --env-file .env up -d mailpit
```

或直接全量部署（脚本会自动合并 local overlay）：

```powershell
.\build-and-deploy.ps1
```

## 连接参数（Developer Workstation）

| 字段 | 值 |
|------|-----|
| 类型 | 自定义 SMTP |
| SMTP 主机 | `mailpit` |
| 端口 | `1025` |
| 使用 TLS | 否 |
| 邮箱 | `test@local.dev`（任意） |

测试成功后打开 **http://localhost:8025** 查看邮件。

## 注意

- 提交前执行 `git status`，确认 **没有** `docker-compose.local.yml`。
- 公司 SMTP / QQ 等真实邮箱仍按 Connections 表单单独配置，与 Mailpit 无关。内网中继主机名加入 `.env` 的 `SSRF_ALLOWED_HOSTS` 后可在 Designer 测试连接；独立冒烟见 `scripts/email-smtp-test/`。
