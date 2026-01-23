#!/bin/bash

# =====================================================
# pnpm 安装和项目初始化脚本
# =====================================================

set -e

echo "📦 pnpm 安装和项目初始化脚本"
echo ""

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "❌ 错误: 未找到 Node.js，请先安装 Node.js 20+"
    exit 1
fi

NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 20 ]; then
    echo "⚠️  警告: Node.js 版本过低，建议使用 Node.js 20+"
fi

# 检查 pnpm
if ! command -v pnpm &> /dev/null; then
    echo "📥 安装 pnpm 10.28.0..."
    npm install -g pnpm@10.28.0
    echo "✅ pnpm 安装完成"
else
    PNPM_VERSION=$(pnpm -v)
    echo "✅ 已安装 pnpm 版本: $PNPM_VERSION"
    
    # 检查版本是否为 10.28.0
    if [ "$PNPM_VERSION" != "10.28.0" ]; then
        echo "⚠️  当前版本为 $PNPM_VERSION，建议使用 10.28.0"
        read -p "是否更新到 10.28.0? (y/n): " update_choice
        if [ "$update_choice" = "y" ] || [ "$update_choice" = "Y" ]; then
            npm install -g pnpm@10.28.0
            echo "✅ pnpm 已更新到 10.28.0"
        fi
    fi
fi

echo ""
echo "🧹 清理旧的 npm 文件..."

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 删除 package-lock.json 文件
for dir in "frontend/admin-center" "frontend/user-portal" "frontend/developer-workstation"; do
    if [ -f "$BASE_DIR/$dir/package-lock.json" ]; then
        echo "  删除 $dir/package-lock.json"
        rm "$BASE_DIR/$dir/package-lock.json"
    fi
done

echo ""
echo "📥 安装项目依赖..."

# 安装每个前端项目的依赖
for dir in "frontend/admin-center" "frontend/user-portal" "frontend/developer-workstation"; do
    echo ""
    echo "📦 安装 $dir 的依赖..."
    cd "$BASE_DIR/$dir"
    
    # 如果存在 node_modules，询问是否删除
    if [ -d "node_modules" ]; then
        read -p "  $dir 已存在 node_modules，是否删除并重新安装? (y/n): " reinstall_choice
        if [ "$reinstall_choice" = "y" ] || [ "$reinstall_choice" = "Y" ]; then
            rm -rf node_modules
            pnpm install
        else
            echo "  跳过 $dir"
        fi
    else
        pnpm install
    fi
done

echo ""
echo "✅ 所有依赖安装完成！"
echo ""
echo "📝 下一步："
echo "  1. 运行 ./start-frontend.sh 启动前端服务"
echo "  2. 查看 PNPM_MIGRATION.md 了解更多信息"
echo ""
