# 用户选择组件规范

## 核心规则

**所有选择用户的地方都必须：**

1. **预览默认用户** - 当选择框获得焦点时，自动加载并显示前 20 个用户
2. **支持模糊搜索** - 支持按用户名、姓名、邮箱进行模糊搜索，无最小字符限制

## 实现模式

### 前端实现

```vue
<el-select
  v-model="selectedUserId"
  :placeholder="t('user.searchUserPlaceholder')"
  filterable
  remote
  :remote-method="searchUsers"
  :loading="searchLoading"
  @focus="loadDefaultUsers"
>
  <el-option
    v-for="user in searchResults"
    :key="user.id"
    :label="`${user.fullName} (${user.username})`"
    :value="user.id"
  />
</el-select>
```

```typescript
const loadDefaultUsers = async () => {
  if (defaultUsersLoaded.value || searchResults.value.length > 0) return
  searchLoading.value = true
  try {
    const result = await userApi.list({ size: 20 })
    // 可选：过滤掉已存在的用户
    searchResults.value = result.content
    defaultUsersLoaded.value = true
  } catch (e) {
    console.error('Failed to load default users:', e)
  } finally {
    searchLoading.value = false
  }
}

const searchUsers = async (query: string) => {
  if (!query) {
    loadDefaultUsers()
    return
  }
  searchLoading.value = true
  try {
    const result = await userApi.list({ keyword: query, size: 20 })
    searchResults.value = result.content
  } catch (e) {
    console.error('Failed to search users:', e)
  } finally {
    searchLoading.value = false
  }
}
```

### 后端支持

后端 `UserRepository.findByConditions` 已支持模糊搜索：
- 搜索字段：`username`, `fullName`, `displayName`, `email`
- 搜索模式：`LIKE '%keyword%'`

## 已实现的组件

以下组件已按此规范实现：

- [x] `VirtualGroupMembersDialog.vue` - 虚拟组成员管理
- [x] `VirtualGroupApproversDialog.vue` - 虚拟组审批人管理
- [x] `BusinessUnitApproversDialog.vue` - 业务单元审批人管理
- [x] `BusinessUnitMembersDialog.vue` - 业务单元成员管理

## 检查清单

添加新的用户选择功能时：

- [ ] 使用 `el-select` 组件，设置 `filterable` 和 `remote` 属性
- [ ] 添加 `@focus="loadDefaultUsers"` 事件处理
- [ ] 实现 `loadDefaultUsers()` 函数加载默认用户
- [ ] 实现 `searchUsers(query)` 函数支持模糊搜索
- [ ] 空查询时调用 `loadDefaultUsers()` 而不是清空列表
- [ ] 显示格式：`${user.fullName} (${user.username})`
