# Email SMTP Test

独立 Maven 项目，用于在连上公司 VPN 后验证 SMTP 中继能否正常发信。

## 前置条件

- JDK 17+
- Maven 3.8+
- 能访问内网 SMTP 中继（通常需公司 VPN）

## 配置

在 PowerShell 中设置环境变量（**必填项无默认值，请按 IT 提供的参数填写**）：

```powershell
$env:SMTP_HOST='your-internal-smtp-relay.example.com'
$env:SMTP_SENDER='notification@your-domain.example.com'
$env:SMTP_RECIPIENT='your.name@example.com'
$env:SMTP_USERNAME='your-smtp-service-account'
$env:SMTP_PASSWORD='your-password'
```

可选：

```powershell
$env:SMTP_PORT='25'              # 内网中继常用 25；STARTTLS 常用 587
$env:SMTP_USE_AUTH='true'        # 内网 25 免认证时设为 false
$env:SMTP_DEBUG='false'          # 设为 true 可打印 JavaMail 协议日志
```

## 运行

**方式一（推荐）** — 交互式脚本，缺省变量时会提示输入：

```powershell
cd scripts/email-smtp-test
.\run-email-test.ps1
```

**方式二** — 手动 Maven：

```powershell
cd scripts/email-smtp-test
mvn clean compile exec:java
```

看到 `Email sent successfully!` 即表示发送成功。

## 与 Workflow Station 对齐

| 场景 | 端口 | Designer「使用 TLS」 | 认证 |
|------|------|---------------------|------|
| 内网中继（端口 25，实测路径） | 25 | **否** | 是 |
| STARTTLS 中继 | 587 | **是** | 是 |

在 Developer Workstation → Connections 创建 **自定义 SMTP** 时按上表填写。若中继主机解析到内网 IP，须把主机名加入 `SSRF_ALLOWED_HOSTS`（`deploy/environments/dev/.env`）。

## Email Monitor（入站）

Monitor 走 **IMAP 轮询**，与 SMTP 发信配置独立；需入站监控时在同一连接上额外填写 IMAP 主机/端口。
