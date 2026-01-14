# 多语言（i18n）开发指南

## 概述

本项目所有前端应用必须支持多语言，包括：
- **简体中文 (zh-CN)** - 默认语言
- **繁体中文 (zh-TW)**
- **英文 (en)**

## 核心规则

### 1. 禁止硬编码文本

**所有用户可见的文本必须使用 i18n 翻译函数**，禁止在代码中硬编码中文或英文文本。

```typescript
// ❌ 错误示例
<el-button>保存</el-button>
<span>操作成功</span>

// ✅ 正确示例
<el-button>{{ t('common.save') }}</el-button>
<span>{{ t('common.success') }}</span>
```

### 2. Locale 文件结构

每个前端应用的 locale 文件位于 `src/i18n/locales/` 目录：

```
src/i18n/locales/
├── zh-CN.ts  # 简体中文
├── zh-TW.ts  # 繁体中文
└── en.ts     # 英文
```

### 3. 三个文件必须保持同步

- 三个 locale 文件必须包含**完全相同的键结构**
- 添加新键时，必须同时在三个文件中添加
- 三个文件的行数应该一致

### 4. 繁体中文字符规范

zh-TW.ts 文件必须使用正确的繁体字，常见易错字：

| 简体 | 繁体 | 说明 |
|------|------|------|
| 平台 | 平臺 | 台/臺 |
| 工作台 | 工作臺 | 台/臺 |
| 确认 | 確認 | 确/確 |
| 删除 | 刪除 | 删/刪 |
| 编辑 | 編輯 | 编/編 |
| 数据 | 資料 | 数据/資料 |
| 时间 | 時間 | 时/時 |
| 创建 | 建立 | 创建/建立 |
| 选择 | 選擇 | 选/選 |
| 输入 | 輸入 | 输/輸 |
| 用户 | 使用者 | 用户/使用者 |
| 设置 | 設定 | 设置/設定 |
| 信息 | 資訊 | 信息/資訊 |
| 视频 | 影片 | 视频/影片 |
| 软件 | 軟體 | 软件/軟體 |

### 5. 标准命名空间

所有 locale 文件应包含以下标准命名空间：

```typescript
export default {
  app: {
    name: '应用名称',
    title: '页面标题'
  },
  common: {
    save: '保存',
    cancel: '取消',
    delete: '删除',
    edit: '编辑',
    create: '创建',
    search: '搜索',
    confirm: '确认',
    back: '返回',
    loading: '加载中...',
    success: '操作成功',
    error: '操作失败',
    noData: '暂无数据',
    close: '关闭',
    submit: '提交',
    refresh: '刷新',
    logout: '退出登录',
    logoutSuccess: '已退出登录'
    // ... 其他通用文本
  },
  login: {
    title: '登录标题',
    username: '用户名',
    password: '密码',
    login: '登录'
    // ... 其他登录相关
  },
  // ... 其他业务模块
}
```

### 6. 路由标题国际化

路由配置中使用 `titleKey` 而非硬编码标题：

```typescript
// ❌ 错误示例
{
  path: '/dashboard',
  meta: { title: '工作台' }
}

// ✅ 正确示例
{
  path: '/dashboard',
  meta: { titleKey: 'menu.dashboard' }
}
```

### 7. 语言切换实现

使用全局 i18n 实例进行语言切换：

```typescript
import i18n from '@/i18n'

// 切换语言
const changeLanguage = (lang: string) => {
  i18n.global.locale.value = lang
  localStorage.setItem('language', lang)
  document.documentElement.lang = lang
}
```

### 8. 浏览器标签标题

页面标题应随语言切换而更新：

```typescript
// 在路由守卫中
router.afterEach((to) => {
  const titleKey = to.meta.titleKey as string
  if (titleKey) {
    document.title = `${t(titleKey)} - ${t('app.name')}`
  }
})
```

## 开发检查清单

### 添加新功能时

- [ ] 所有用户可见文本使用 `t()` 函数
- [ ] 在三个 locale 文件中添加对应的翻译键
- [ ] zh-TW.ts 使用正确的繁体字
- [ ] 路由使用 `titleKey` 而非硬编码标题

### 代码审查时

- [ ] 检查是否有硬编码的中文/英文文本
- [ ] 检查三个 locale 文件的键是否同步
- [ ] 检查 zh-TW.ts 是否有简体字

### 发现未国际化的地方

**如果在开发过程中发现任何未实现多语言的地方，应立即修复：**

1. 将硬编码文本替换为 `t('key')` 调用
2. 在三个 locale 文件中添加对应翻译
3. 确保繁体中文使用正确字符

## 前端应用 Locale 文件位置

| 应用 | 路径 |
|------|------|
| Admin Center | `frontend/admin-center/src/i18n/locales/` |
| Developer Workstation | `frontend/developer-workstation/src/i18n/locales/` |
| User Portal | `frontend/user-portal/src/i18n/locales/` |

## 参考文件

- i18n 配置：`frontend/*/src/i18n/index.ts`
- Locale 文件：`frontend/*/src/i18n/locales/*.ts`
- 路由配置：`frontend/*/src/router/index.ts`
- 布局组件：`frontend/*/src/layout/*.vue`
