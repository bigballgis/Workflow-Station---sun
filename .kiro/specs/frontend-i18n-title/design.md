# Design Document: Frontend I18n Title

## Overview

本设计文档描述如何为工作流平台的三个前端应用实现系统名称和浏览器标签页标题的国际化支持。设计采用 Vue I18n 的响应式特性，确保语言切换时标题能够实时更新。

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend Application                      │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐ │
│  │  index.html │    │  i18n/      │    │  router/index.ts    │ │
│  │  (default   │    │  locales/   │    │  (title update      │ │
│  │   title)    │    │  (app keys) │    │   with i18n)        │ │
│  └─────────────┘    └─────────────┘    └─────────────────────┘ │
│         │                  │                      │             │
│         ▼                  ▼                      ▼             │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    document.title                           ││
│  │         (dynamically updated based on locale)               ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. I18n Locale Files

每个前端应用的 locale 文件需要添加 `app` 命名空间：

```typescript
// 结构示例
export default {
  app: {
    name: 'Admin Center',      // 系统名称
    title: 'Workflow Platform' // 平台名称
  },
  // ... 其他翻译
}
```

### 2. Router Title Update

路由守卫中使用 i18n 翻译函数动态设置标题：

```typescript
// 伪代码
router.beforeEach((to, from, next) => {
  const pageTitle = to.meta.title ? t(`menu.${to.meta.titleKey}`) : t('app.name')
  document.title = `${pageTitle} - ${t('app.title')}`
  next()
})
```

### 3. Title Update Utility

创建一个工具函数用于更新标题，可在语言切换时调用：

```typescript
// utils/title.ts
export function updateDocumentTitle(i18n: I18n, routeTitle?: string) {
  const t = i18n.global.t
  const pageTitle = routeTitle || t('app.name')
  const appTitle = t('app.title')
  document.title = `${pageTitle} - ${appTitle}`
}
```

## Data Models

### I18n App Namespace Structure

| Key | Type | Description |
|-----|------|-------------|
| `app.name` | string | 应用名称（如"管理中心"） |
| `app.title` | string | 平台名称（如"工作流平台"） |

### Locale Translations

| Application | zh-CN name | en name | zh-CN title | en title |
|-------------|------------|---------|-------------|----------|
| Admin Center | 管理中心 | Admin Center | 工作流平台 | Workflow Platform |
| Developer Workstation | 开发工作站 | Developer Workstation | 工作流平台 | Workflow Platform |
| User Portal | 用户门户 | User Portal | 工作流平台 | Workflow Platform |

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

由于本功能主要涉及 UI 显示和国际化配置，大部分需求属于配置验证和 UI 行为测试，不适合用属性测试。以下是可验证的示例测试：

### Example Tests (Unit Tests)

1. **I18n Configuration Test**: 验证每个 locale 文件包含 `app.name` 和 `app.title` 键
   - **Validates: Requirements 3.1, 3.2**

2. **Chinese Locale Test**: 验证 zh-CN locale 返回正确的中文翻译
   - **Validates: Requirements 1.1, 2.2**

3. **English Locale Test**: 验证 en locale 返回正确的英文翻译
   - **Validates: Requirements 1.2, 2.3**

## Error Handling

| Scenario | Handling |
|----------|----------|
| Missing translation key | 使用 fallbackLocale (zh-CN) 的翻译 |
| Invalid locale | 回退到默认 locale (zh-CN) |
| localStorage 无法访问 | 使用默认 locale (zh-CN) |

## Testing Strategy

### Unit Tests

由于本功能主要是配置和 UI 行为，测试重点在于：

1. **配置完整性测试**：验证所有 locale 文件包含必需的 `app` 命名空间键
2. **翻译正确性测试**：验证各语言的翻译值符合预期

### Manual Testing

1. 切换语言后验证标签页标题更新
2. 刷新页面后验证标题保持正确语言
3. 验证登录页面系统名称显示正确

### Test Framework

- 使用 Vitest 进行单元测试
- 测试文件位置：`frontend/*/src/__tests__/`
