# pnpm 迁移指南

本项目已从 npm 迁移到 pnpm 10.28.0。

## 安装 pnpm

### 方法 1: 使用 npm（推荐）
```bash
npm install -g pnpm@10.28.0
```

### 方法 2: 使用 corepack（Node.js 16.13+）
```bash
corepack enable
corepack prepare pnpm@10.28.0 --activate
```

### 方法 3: 使用 Homebrew (macOS)
```bash
brew install pnpm@10.28.0
```

### 方法 4: 使用独立安装脚本
```bash
curl -fsSL https://get.pnpm.io/install.sh | sh -
# 然后指定版本
pnpm add -g pnpm@10.28.0
```

## 验证安装

```bash
pnpm -v
# 应该显示 10.28.0
```

## 迁移步骤

1. **安装 pnpm**（如果尚未安装）
   ```bash
   npm install -g pnpm@10.28.0
   ```

2. **删除旧的 node_modules 和 package-lock.json**（如果存在）
   ```bash
   # 在每个前端项目目录下执行
   cd frontend/admin-center
   rm -rf node_modules package-lock.json
   
   cd ../user-portal
   rm -rf node_modules package-lock.json
   
   cd ../developer-workstation
   rm -rf node_modules package-lock.json
   ```

3. **安装依赖**
   ```bash
   # 在每个前端项目目录下执行
   cd frontend/admin-center
   pnpm install
   
   cd ../user-portal
   pnpm install
   
   cd ../developer-workstation
   pnpm install
   ```

   或者使用脚本自动安装：
   ```bash
   ./start-frontend.sh
   ```

## 常用命令对比

| npm | pnpm |
|-----|------|
| `npm install` | `pnpm install` |
| `npm install <package>` | `pnpm add <package>` |
| `npm install -D <package>` | `pnpm add -D <package>` |
| `npm run <script>` | `pnpm run <script>` |
| `npm ci` | `pnpm install --frozen-lockfile` |
| `npx <command>` | `pnpm exec <command>` |

## 项目变更

以下文件已更新以使用 pnpm：

- ✅ `start-frontend.sh` - 启动脚本
- ✅ `start-services.sh` - 服务启动脚本
- ✅ `frontend/*/Dockerfile` - Docker 构建文件
- ✅ `.github/workflows/ci.yml` - CI/CD 配置
- ✅ `README.md` - 文档更新
- ✅ 已删除所有 `package-lock.json` 文件

## 注意事项

1. **pnpm-lock.yaml**: pnpm 使用 `pnpm-lock.yaml` 而不是 `package-lock.json`。该文件应该被提交到版本控制。

2. **node_modules 结构**: pnpm 使用符号链接和硬链接来节省磁盘空间，`node_modules` 的结构可能与 npm 不同，这是正常的。

3. **Docker 构建**: Dockerfile 已更新为使用 pnpm，确保在构建镜像时使用正确的包管理器。

4. **CI/CD**: GitHub Actions 工作流已更新为使用 pnpm，包括缓存配置。

## 故障排除

### 问题：pnpm 命令未找到
**解决方案**: 确保 pnpm 已正确安装并添加到 PATH。

### 问题：依赖安装失败
**解决方案**: 
```bash
# 清除 pnpm 缓存
pnpm store prune

# 重新安装
pnpm install
```

### 问题：版本不匹配
**解决方案**: 确保使用 pnpm 10.28.0：
```bash
pnpm add -g pnpm@10.28.0
```

## 更多信息

- [pnpm 官方文档](https://pnpm.io/)
- [pnpm vs npm 对比](https://pnpm.io/motivation)
