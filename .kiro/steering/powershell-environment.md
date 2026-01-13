# PowerShell 环境指南

## 重要规则

**本项目使用 Windows PowerShell 作为终端环境，禁止使用以下命令：**

- ❌ `curl` - 不要使用 curl 命令
- ❌ `wget` - 不要使用 wget 命令
- ❌ `bash` 语法 - 不要使用 bash 特有的语法

## API 测试方法

### 使用 Invoke-RestMethod（推荐）

```powershell
# GET 请求
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/endpoint" -Method GET | ConvertTo-Json -Depth 5

# 带 Headers 的 GET 请求
$headers = @{ "Content-Type" = "application/json" }
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/endpoint" -Method GET -Headers $headers | ConvertTo-Json -Depth 5

# POST 请求（带 JSON body）
$body = @{
    key1 = "value1"
    key2 = "value2"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8083/api/v1/endpoint" -Method POST -Headers $headers -Body $body | ConvertTo-Json -Depth 5

# 带认证的请求
$headers = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $token"
}
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/endpoint" -Method GET -Headers $headers | ConvertTo-Json -Depth 5

# PUT 请求
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/endpoint/1" -Method PUT -Headers $headers -Body $body | ConvertTo-Json -Depth 5

# DELETE 请求
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/endpoint/1" -Method DELETE -Headers $headers
```

### 使用 Invoke-WebRequest（需要更多控制时）

```powershell
# 获取完整响应（包括状态码、headers等）
$response = Invoke-WebRequest -Uri "http://localhost:8083/api/v1/endpoint" -Method GET
$response.StatusCode
$response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 5
```

## 文件操作

### 读取文件
```powershell
# 读取文件内容
Get-Content -Path "file.txt"

# 读取完整文件（保留换行）
Get-Content -Path "file.txt" -Raw

# 读取 UTF-8 编码文件
Get-Content -Path "file.txt" -Encoding UTF8
```

### 写入文件
```powershell
# 写入文件（推荐，确保 UTF-8 编码）
[System.IO.File]::WriteAllText("file.txt", $content, [System.Text.Encoding]::UTF8)

# 或使用 Set-Content
Set-Content -Path "file.txt" -Value $content -Encoding UTF8

# 追加内容
Add-Content -Path "file.txt" -Value $newContent -Encoding UTF8
```

### 执行 SQL 文件
```powershell
# 执行 SQL 文件到 Docker PostgreSQL
Get-Content -Path "script.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform

# 执行单条 SQL
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "SELECT * FROM table_name"
```

## 常用 PowerShell 命令对照

| Bash/Linux | PowerShell | 说明 |
|------------|------------|------|
| `curl` | `Invoke-RestMethod` | HTTP 请求 |
| `wget` | `Invoke-WebRequest` | 下载文件 |
| `cat` | `Get-Content` | 读取文件 |
| `echo` | `Write-Output` | 输出内容 |
| `ls` | `Get-ChildItem` | 列出目录 |
| `rm` | `Remove-Item` | 删除文件 |
| `cp` | `Copy-Item` | 复制文件 |
| `mv` | `Move-Item` | 移动文件 |
| `mkdir` | `New-Item -ItemType Directory` | 创建目录 |
| `grep` | `Select-String` | 搜索文本 |
| `&&` | `;` | 命令分隔符 |

## 环境变量

```powershell
# 设置环境变量（当前会话）
$env:VARIABLE_NAME = "value"

# 读取环境变量
$env:VARIABLE_NAME

# 在命令中使用环境变量
$env:SPRING_DATASOURCE_PASSWORD='platform123'; mvn spring-boot:run
```

## 字符串处理

```powershell
# Here-String（多行字符串）
$multiLine = @'
Line 1
Line 2
Line 3
'@

# 字符串拼接
$result = "Hello" + " " + "World"

# 字符串插值
$name = "World"
$result = "Hello $name"

# StringBuilder（大量字符串拼接时推荐）
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("Line 1")
[void]$sb.AppendLine("Line 2")
$result = $sb.ToString()
```

## JSON 处理

```powershell
# 对象转 JSON
$obj = @{ key1 = "value1"; key2 = "value2" }
$json = $obj | ConvertTo-Json -Depth 5

# JSON 转对象
$obj = $json | ConvertFrom-Json

# 格式化输出 JSON
$response | ConvertTo-Json -Depth 5
```
