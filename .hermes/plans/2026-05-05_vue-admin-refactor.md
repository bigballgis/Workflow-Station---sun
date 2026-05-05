# Vue3 Admin Center 工程级重构计划

> **目标**: 将 vibe-coding 产物重构为可维护的标准 Vue3 工程架构
> **原则**: 渐进式、不破坏功能、每阶段可独立验证
> **技术栈**: Vue3 + Composition API + Pinia + Axios + Vite + TypeScript + Element Plus

---

## 阶段 1：提取公共工具函数 ← 当前执行

**文件变更**:
- 新建: `src/utils/format.ts` — formatDate, statusType, statusText, tagType 等
- 新建: `src/utils/version.ts` — compareVersions, deduplicateByCode
- 修改: 6+ 个 views/*/index.vue — 替换为 import

## 阶段 2：创建通用 composables

**文件变更**:
- 新建: `src/composables/usePagination.ts`
- 新建: `src/composables/useConfirmDelete.ts`
- 新建: `src/composables/useToggleSwitch.ts`
- 新建: `src/composables/useTabRefresh.ts`
- 新建: `src/composables/useStatusMapping.ts`
- 修改: 页面组件使用 composables

## 阶段 3：创建业务 composables（解耦 API）

**文件变更**:
- 新建: `src/composables/modules/useFunctionUnit.ts`
- 新建: `src/composables/modules/useUser.ts`
- 新建: `src/composables/modules/useDictionary.ts`
- 新建: `src/composables/modules/useVirtualGroup.ts`
- 修改: 页面组件从直接调 API 改为调 composable

## 阶段 4：补充 Pinia Stores

**文件变更**:
- 新建: `src/stores/functionUnit.ts`
- 新建: `src/stores/dictionary.ts`
- 新建: `src/stores/virtualGroup.ts`
- 新建: `src/stores/biManagement.ts`
- 修改: 页面组件使用 store

## 阶段 5：提取全局共享 UI 组件

**文件变更**:
- 新建: `src/components/PageHeader.vue`
- 新建: `src/components/SearchBar.vue`
- 修改: 页面组件使用共享组件

---

## 层间调用规则

```
Component (template + 调用 composable)
    ↓ 使用
Composable (useXxx)  → 封装业务逻辑
    ↓ 调用
Store (useXxxStore)  → Pinia 全局状态
    ↓ 调用
API (xxxApi)         → Axios 请求封装
```

**强制规则**:
- Component 不直接调 API ✗
- Component 不写复杂逻辑 ✗
- 每个文件只做一件事 ✓
