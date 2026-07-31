# Design Document: Vue3 Frontend Architecture Refactor

## Overview

This document describes the technical design for refactoring the admin-center Vue 3 frontend from a rapid development state to an engineering-grade architecture. The refactoring establishes clear separation of concerns, reusable patterns, comprehensive type safety, and maintainable code organization while preserving all existing functionality.

### Design Goals

1. **Separation of Concerns**: Establish clear boundaries between presentation, business logic, and data access layers
2. **Reusability**: Create composable patterns and reusable components to eliminate code duplication
3. **Type Safety**: Implement comprehensive TypeScript types for compile-time error detection
4. **Testability**: Enable unit testing of business logic independent of UI components
5. **Maintainability**: Organize code by feature and establish consistent patterns
6. **Progressive Migration**: Allow incremental refactoring without breaking existing functionality

### Design Principles

- **Composition over Inheritance**: Use Vue 3 Composition API and composables for logic reuse
- **Single Responsibility**: Each module, component, and function has one clear purpose
- **Explicit Dependencies**: All dependencies are explicitly imported and typed
- **Fail Fast**: Use TypeScript strict mode and runtime validation to catch errors early
- **Convention over Configuration**: Establish clear naming and organization conventions

## Architecture

### Layered Architecture

The refactored architecture follows a three-layer pattern:

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  (Vue Components, Templates, UI Logic)                       │
│  - View Components (pages)                                   │
│  - UI Components (reusable widgets)                          │
│  - Layouts                                                   │
└─────────────────────────────────────────────────────────────┘
                            ↓ ↑
┌─────────────────────────────────────────────────────────────┐
│                   Business Logic Layer                       │
│  (Composables, Services, Domain Logic)                       │
│  - Composables (useTable, useForm, useDialog)                │
│  - Services (domain-specific operations)                     │
│  - State Management (Pinia stores)                           │
│  - Utilities (pure functions)                                │
└─────────────────────────────────────────────────────────────┘
                            ↓ ↑
┌─────────────────────────────────────────────────────────────┐
│                    Data Access Layer                         │
│  (API Communication, Data Transformation)                    │
│  - API Modules (typed HTTP requests)                         │
│  - Request/Response Interceptors                             │
│  - Data Transformation Functions                             │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

```
User Interaction
      ↓
View Component (presentation logic)
      ↓
Composable (business logic + state)
      ↓
Service/API Module (data access)
      ↓
HTTP Request (axios)
      ↓
Backend API
      ↓
Response Processing (interceptors)
      ↓
Data Transformation (services)
      ↓
State Update (composable/store)
      ↓
Reactive UI Update (Vue reactivity)
```

### Directory Structure

```
src/
├── api/                      # Data Access Layer - API modules
│   ├── request.ts           # Axios instance and interceptors
│   ├── user.ts              # User-related API calls
│   ├── role.ts              # Role-related API calls
│   ├── organization.ts      # Organization-related API calls
│   └── index.ts             # API module exports
│
├── assets/                   # Static assets
│   ├── images/
│   ├── icons/
│   └── fonts/
│
├── components/               # Reusable UI Components (Presentation Layer)
│   ├── common/              # Common UI components
│   │   ├── AppButton/
│   │   ├── AppDialog/
│   │   ├── AppTable/
│   │   └── AppForm/
│   ├── forms/               # Form-specific components
│   │   ├── FormField/
│   │   ├── FormSection/
│   │   └── FormActions/
│   ├── tables/              # Table-specific components
│   │   ├── TableColumn/
│   │   ├── TableActions/
│   │   └── TableFilters/
│   └── layouts/             # Layout components
│       ├── PageHeader/
│       ├── PageContent/
│       └── PageFooter/
│
├── composables/              # Business Logic Layer - Reusable composition functions
│   ├── core/                # Core composables
│   │   ├── useTable.ts      # Table state and operations
│   │   ├── useForm.ts       # Form state and validation
│   │   ├── useDialog.ts     # Dialog state management
│   │   ├── usePagination.ts # Pagination logic
│   │   └── useRequest.ts    # HTTP request wrapper
│   ├── features/            # Feature-specific composables
│   │   ├── useUserManagement.ts
│   │   ├── useRoleManagement.ts
│   │   └── useAuth.ts
│   └── utils/               # Utility composables
│       ├── useDebounce.ts
│       ├── useThrottle.ts
│       └── useClipboard.ts
│
├── hooks/                    # Custom Vue lifecycle hooks
│   ├── useLifecycle.ts
│   └── useRouteGuard.ts
│
├── i18n/                     # Internationalization
│   ├── locales/
│   │   ├── zh-CN.ts
│   │   └── en-US.ts
│   ├── index.ts
│   └── helpers.ts           # Type-safe translation helpers
│
├── layouts/                  # Page layouts
│   ├── DefaultLayout.vue
│   ├── AuthLayout.vue
│   └── EmptyLayout.vue
│
├── router/                   # Vue Router configuration
│   ├── index.ts             # Router instance
│   ├── routes.ts            # Route definitions
│   ├── guards.ts            # Navigation guards
│   └── types.ts             # Route type definitions
│
├── services/                 # Business Logic Layer - Domain services
│   ├── user.service.ts      # User domain logic
│   ├── role.service.ts      # Role domain logic
│   ├── auth.service.ts      # Authentication logic
│   └── validation.service.ts # Validation logic
│
├── stores/                   # State Management (Pinia)
│   ├── user.ts              # User state store
│   ├── auth.ts              # Authentication state
│   ├── app.ts               # Application state
│   └── types.ts             # Store type definitions
│
├── styles/                   # Global styles
│   ├── variables.scss       # SCSS variables
│   ├── mixins.scss          # SCSS mixins
│   ├── global.scss          # Global styles
│   └── element-plus.scss    # Element Plus customization
│
├── types/                    # TypeScript type definitions
│   ├── models/              # Domain models
│   │   ├── user.ts
│   │   ├── role.ts
│   │   └── organization.ts
│   ├── api/                 # API request/response types
│   │   ├── request.ts
│   │   └── response.ts
│   ├── components/          # Component prop types
│   │   └── table.ts
│   └── common.ts            # Common type definitions
│
├── utils/                    # Utility functions
│   ├── format.ts            # Formatting utilities
│   ├── validation.ts        # Validation utilities
│   ├── storage.ts           # Local storage utilities
│   └── date.ts              # Date utilities
│
├── views/                    # View Components (Pages)
│   ├── user/                # User management feature
│   │   ├── UserList.vue
│   │   ├── UserDetail.vue
│   │   └── UserEdit.vue
│   ├── role/                # Role management feature
│   │   ├── RoleList.vue
│   │   └── RoleEdit.vue
│   ├── auth/                # Authentication pages
│   │   ├── Login.vue
│   │   └── Register.vue
│   └── dashboard/
│       └── Dashboard.vue
│
├── App.vue                   # Root component
└── main.ts                   # Application entry point
```


## Components and Interfaces

### Core Composables

#### useTable Composable

Provides table state management, pagination, sorting, filtering, and data loading.

```typescript
// composables/core/useTable.ts
import { ref, computed, watch } from 'vue'
import type { Ref } from 'vue'

export interface TableColumn<T = any> {
  prop: keyof T
  label: string
  width?: string | number
  sortable?: boolean
  formatter?: (row: T, column: TableColumn<T>, cellValue: any) => string
}

export interface TableOptions<T = any> {
  columns: TableColumn<T>[]
  fetchData: (params: TableQueryParams) => Promise<TableDataResponse<T>>
  immediate?: boolean
  pageSize?: number
}

export interface TableQueryParams {
  page: number
  pageSize: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  filters?: Record<string, any>
}

export interface TableDataResponse<T = any> {
  data: T[]
  total: number
}

export interface UseTableReturn<T = any> {
  // State
  data: Ref<T[]>
  loading: Ref<boolean>
  error: Ref<Error | null>
  currentPage: Ref<number>
  pageSize: Ref<number>
  total: Ref<number>
  sortBy: Ref<string | undefined>
  sortOrder: Ref<'asc' | 'desc' | undefined>
  filters: Ref<Record<string, any>>
  
  // Computed
  totalPages: Ref<number>
  hasData: Ref<boolean>
  isEmpty: Ref<boolean>
  
  // Methods
  fetchData: () => Promise<void>
  refresh: () => Promise<void>
  handlePageChange: (page: number) => void
  handleSizeChange: (size: number) => void
  handleSortChange: (prop: string, order: 'asc' | 'desc' | null) => void
  handleFilterChange: (filters: Record<string, any>) => void
  resetFilters: () => void
}

export function useTable<T = any>(options: TableOptions<T>): UseTableReturn<T>
```

**Usage Example:**

```typescript
// views/user/UserList.vue
<script setup lang="ts">
import { useTable } from '@/composables/core/useTable'
import { getUserList } from '@/api/user'
import type { User } from '@/types/models/user'

const {
  data: users,
  loading,
  currentPage,
  pageSize,
  total,
  handlePageChange,
  handleSizeChange,
  handleSortChange,
  refresh
} = useTable<User>({
  columns: [
    { prop: 'username', label: '用户名', sortable: true },
    { prop: 'email', label: '邮箱' },
    { prop: 'role', label: '角色' }
  ],
  fetchData: getUserList,
  immediate: true,
  pageSize: 20
})
</script>
```

#### useForm Composable

Provides form state management, validation, and submission handling.

```typescript
// composables/core/useForm.ts
import { ref, reactive, computed } from 'vue'
import type { Ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

export interface UseFormOptions<T = any> {
  initialValues: T
  rules?: FormRules
  onSubmit: (values: T) => Promise<void>
  onSuccess?: (result: any) => void
  onError?: (error: Error) => void
  resetAfterSubmit?: boolean
}

export interface UseFormReturn<T = any> {
  // State
  formRef: Ref<FormInstance | undefined>
  formData: T
  loading: Ref<boolean>
  error: Ref<Error | null>
  isDirty: Ref<boolean>
  
  // Methods
  handleSubmit: () => Promise<void>
  handleReset: () => void
  validate: () => Promise<boolean>
  validateField: (prop: keyof T) => Promise<boolean>
  clearValidate: () => void
  setFieldValue: (field: keyof T, value: any) => void
  setFieldsValue: (values: Partial<T>) => void
  resetFields: () => void
}

export function useForm<T = any>(options: UseFormOptions<T>): UseFormReturn<T>
```

**Usage Example:**

```typescript
// views/user/UserEdit.vue
<script setup lang="ts">
import { useForm } from '@/composables/core/useForm'
import { createUser, updateUser } from '@/api/user'
import type { UserFormData } from '@/types/models/user'

const props = defineProps<{
  userId?: string
  mode: 'create' | 'edit'
}>()

const {
  formRef,
  formData,
  loading,
  handleSubmit,
  handleReset
} = useForm<UserFormData>({
  initialValues: {
    username: '',
    email: '',
    roleId: ''
  },
  rules: {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
    ],
    email: [
      { required: true, message: '请输入邮箱', trigger: 'blur' },
      { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
    ]
  },
  onSubmit: async (values) => {
    if (props.mode === 'create') {
      await createUser(values)
    } else {
      await updateUser(props.userId!, values)
    }
  },
  onSuccess: () => {
    ElMessage.success('保存成功')
    router.push('/users')
  }
})
</script>
```

#### useDialog Composable

Provides dialog state management and lifecycle handling.

```typescript
// composables/core/useDialog.ts
import { ref, computed } from 'vue'
import type { Ref } from 'vue'

export interface UseDialogOptions {
  onOpen?: () => void
  onClose?: () => void
  onConfirm?: () => void | Promise<void>
  onCancel?: () => void
}

export interface UseDialogReturn {
  // State
  visible: Ref<boolean>
  loading: Ref<boolean>
  
  // Methods
  open: () => void
  close: () => void
  confirm: () => Promise<void>
  cancel: () => void
  toggle: () => void
}

export function useDialog(options?: UseDialogOptions): UseDialogReturn
```

#### usePagination Composable

Provides pagination state and calculation logic.

```typescript
// composables/core/usePagination.ts
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'

export interface UsePaginationOptions {
  initialPage?: number
  initialPageSize?: number
  total?: number
  pageSizes?: number[]
}

export interface UsePaginationReturn {
  // State
  currentPage: Ref<number>
  pageSize: Ref<number>
  total: Ref<number>
  
  // Computed
  totalPages: ComputedRef<number>
  offset: ComputedRef<number>
  hasNextPage: ComputedRef<boolean>
  hasPrevPage: ComputedRef<boolean>
  
  // Methods
  setPage: (page: number) => void
  setPageSize: (size: number) => void
  setTotal: (total: number) => void
  nextPage: () => void
  prevPage: () => void
  reset: () => void
}

export function usePagination(options?: UsePaginationOptions): UsePaginationReturn
```

### API Module Design

#### API Request Configuration

```typescript
// api/request.ts
import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

// Create axios instance
const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor
request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, data, message } = response.data
    
    if (code === 200 || code === 0) {
      return data
    } else {
      ElMessage.error(message || '请求失败')
      return Promise.reject(new Error(message || '请求失败'))
    }
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      
      switch (status) {
        case 401:
          ElMessage.error('未授权，请重新登录')
          const authStore = useAuthStore()
          authStore.logout()
          router.push('/login')
          break
        case 403:
          ElMessage.error('拒绝访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误')
          break
        default:
          ElMessage.error(data?.message || '请求失败')
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      ElMessage.error('请求配置错误')
    }
    
    return Promise.reject(error)
  }
)

export default request
```

#### API Module Pattern

```typescript
// api/user.ts
import request from './request'
import type { 
  User, 
  UserListParams, 
  UserListResponse,
  CreateUserRequest,
  UpdateUserRequest 
} from '@/types/api/user'

/**
 * Get user list with pagination and filters
 */
export function getUserList(params: UserListParams): Promise<UserListResponse> {
  return request({
    url: '/api/users',
    method: 'get',
    params
  })
}

/**
 * Get user detail by ID
 */
export function getUserById(id: string): Promise<User> {
  return request({
    url: `/api/users/${id}`,
    method: 'get'
  })
}

/**
 * Create new user
 */
export function createUser(data: CreateUserRequest): Promise<User> {
  return request({
    url: '/api/users',
    method: 'post',
    data
  })
}

/**
 * Update existing user
 */
export function updateUser(id: string, data: UpdateUserRequest): Promise<User> {
  return request({
    url: `/api/users/${id}`,
    method: 'put',
    data
  })
}

/**
 * Delete user by ID
 */
export function deleteUser(id: string): Promise<void> {
  return request({
    url: `/api/users/${id}`,
    method: 'delete'
  })
}
```

### State Management Design

#### Pinia Store Pattern

```typescript
// stores/auth.ts
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getUserInfo } from '@/api/auth'
import type { LoginRequest, User } from '@/types/models/user'
import { storage } from '@/utils/storage'

export const useAuthStore = defineStore('auth', () => {
  // State
  const token = ref<string | null>(storage.get('token'))
  const user = ref<User | null>(null)
  const permissions = ref<string[]>([])
  
  // Getters
  const isAuthenticated = computed(() => !!token.value)
  const hasPermission = (permission: string) => {
    return permissions.value.includes(permission)
  }
  
  // Actions
  async function login(credentials: LoginRequest) {
    try {
      const response = await loginApi(credentials)
      token.value = response.token
      user.value = response.user
      permissions.value = response.permissions
      storage.set('token', response.token)
    } catch (error) {
      throw error
    }
  }
  
  async function logout() {
    try {
      await logoutApi()
    } finally {
      token.value = null
      user.value = null
      permissions.value = []
      storage.remove('token')
    }
  }
  
  async function fetchUserInfo() {
    try {
      const response = await getUserInfo()
      user.value = response.user
      permissions.value = response.permissions
    } catch (error) {
      throw error
    }
  }
  
  return {
    // State
    token,
    user,
    permissions,
    // Getters
    isAuthenticated,
    hasPermission,
    // Actions
    login,
    logout,
    fetchUserInfo
  }
})
```

### Component Communication Patterns

#### Props and Emits Pattern

```typescript
// components/common/AppTable/AppTable.vue
<script setup lang="ts">
import type { TableColumn } from '@/types/components/table'

// Props
interface Props {
  data: any[]
  columns: TableColumn[]
  loading?: boolean
  pagination?: boolean
  currentPage?: number
  pageSize?: number
  total?: number
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  pagination: true,
  currentPage: 1,
  pageSize: 20,
  total: 0
})

// Emits
interface Emits {
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
  (e: 'sort-change', prop: string, order: 'asc' | 'desc' | null): void
  (e: 'row-click', row: any): void
}

const emit = defineEmits<Emits>()

// Methods
function handlePageChange(page: number) {
  emit('page-change', page)
}

function handleSizeChange(size: number) {
  emit('size-change', size)
}
</script>
```

#### Provide/Inject Pattern

```typescript
// layouts/DefaultLayout.vue
<script setup lang="ts">
import { provide, ref } from 'vue'
import type { Ref } from 'vue'

// Layout state
const sidebarCollapsed = ref(false)
const breadcrumbs = ref<string[]>([])

// Provide to child components
provide('sidebarCollapsed', sidebarCollapsed)
provide('breadcrumbs', breadcrumbs)
provide('toggleSidebar', () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
})
</script>

// Child component
<script setup lang="ts">
import { inject } from 'vue'
import type { Ref } from 'vue'

const sidebarCollapsed = inject<Ref<boolean>>('sidebarCollapsed')
const toggleSidebar = inject<() => void>('toggleSidebar')
</script>
```


## Data Models

### Domain Models

```typescript
// types/models/user.ts

/**
 * User entity
 */
export interface User {
  id: string
  username: string
  email: string
  avatar?: string
  roleId: string
  roleName: string
  organizationId: string
  organizationName: string
  status: UserStatus
  createdAt: string
  updatedAt: string
}

/**
 * User status enum
 */
export enum UserStatus {
  Active = 'active',
  Inactive = 'inactive',
  Locked = 'locked'
}

/**
 * User form data for create/update
 */
export interface UserFormData {
  username: string
  email: string
  password?: string
  roleId: string
  organizationId: string
  status: UserStatus
}

/**
 * User list query parameters
 */
export interface UserListParams {
  page: number
  pageSize: number
  keyword?: string
  roleId?: string
  organizationId?: string
  status?: UserStatus
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

/**
 * User list response
 */
export interface UserListResponse {
  data: User[]
  total: number
  page: number
  pageSize: number
}
```

```typescript
// types/models/role.ts

/**
 * Role entity
 */
export interface Role {
  id: string
  name: string
  code: string
  description?: string
  permissions: Permission[]
  createdAt: string
  updatedAt: string
}

/**
 * Permission entity
 */
export interface Permission {
  id: string
  name: string
  code: string
  resource: string
  action: string
}

/**
 * Role form data
 */
export interface RoleFormData {
  name: string
  code: string
  description?: string
  permissionIds: string[]
}
```

### API Types

```typescript
// types/api/request.ts

/**
 * Base API request configuration
 */
export interface ApiRequestConfig {
  url: string
  method: 'get' | 'post' | 'put' | 'delete' | 'patch'
  params?: Record<string, any>
  data?: any
  headers?: Record<string, string>
}

/**
 * Pagination request parameters
 */
export interface PaginationParams {
  page: number
  pageSize: number
}

/**
 * Sort request parameters
 */
export interface SortParams {
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

/**
 * Filter request parameters
 */
export interface FilterParams {
  filters?: Record<string, any>
}

/**
 * Common list query parameters
 */
export type ListQueryParams = PaginationParams & SortParams & FilterParams
```

```typescript
// types/api/response.ts

/**
 * Base API response wrapper
 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

/**
 * Paginated response wrapper
 */
export interface PaginatedResponse<T = any> {
  data: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

/**
 * Error response
 */
export interface ApiError {
  code: number
  message: string
  details?: any
  timestamp: number
}
```

### Component Types

```typescript
// types/components/table.ts

/**
 * Table column definition
 */
export interface TableColumn<T = any> {
  prop: keyof T | string
  label: string
  width?: string | number
  minWidth?: string | number
  fixed?: 'left' | 'right'
  sortable?: boolean
  filterable?: boolean
  formatter?: (row: T, column: TableColumn<T>, cellValue: any, index: number) => string
  align?: 'left' | 'center' | 'right'
  headerAlign?: 'left' | 'center' | 'right'
  showOverflowTooltip?: boolean
  type?: 'selection' | 'index' | 'expand'
}

/**
 * Table action button
 */
export interface TableAction<T = any> {
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  icon?: string
  onClick: (row: T) => void
  visible?: (row: T) => boolean
  disabled?: (row: T) => boolean
}

/**
 * Table configuration
 */
export interface TableConfig<T = any> {
  columns: TableColumn<T>[]
  actions?: TableAction<T>[]
  rowKey?: string
  stripe?: boolean
  border?: boolean
  size?: 'large' | 'default' | 'small'
  height?: string | number
  maxHeight?: string | number
}
```

```typescript
// types/components/form.ts

/**
 * Form field configuration
 */
export interface FormField {
  prop: string
  label: string
  type: 'input' | 'select' | 'date' | 'datetime' | 'textarea' | 'number' | 'switch' | 'radio' | 'checkbox'
  placeholder?: string
  required?: boolean
  disabled?: boolean
  options?: FormFieldOption[]
  rules?: FormFieldRule[]
  span?: number
  labelWidth?: string
}

/**
 * Form field option (for select, radio, checkbox)
 */
export interface FormFieldOption {
  label: string
  value: any
  disabled?: boolean
}

/**
 * Form field validation rule
 */
export interface FormFieldRule {
  required?: boolean
  message?: string
  trigger?: 'blur' | 'change'
  min?: number
  max?: number
  pattern?: RegExp
  validator?: (rule: any, value: any, callback: any) => void
}

/**
 * Form configuration
 */
export interface FormConfig {
  fields: FormField[]
  labelWidth?: string
  labelPosition?: 'left' | 'right' | 'top'
  inline?: boolean
  size?: 'large' | 'default' | 'small'
}
```

## Error Handling

### Error Handling Strategy

The application implements a multi-layered error handling approach:

1. **HTTP Layer**: Axios interceptors catch and handle HTTP errors
2. **API Layer**: API functions catch and transform errors
3. **Business Logic Layer**: Composables and services handle domain-specific errors
4. **Presentation Layer**: Components display error messages to users

### Error Types

```typescript
// types/common.ts

/**
 * Application error types
 */
export enum ErrorType {
  Network = 'NETWORK_ERROR',
  Authentication = 'AUTH_ERROR',
  Authorization = 'PERMISSION_ERROR',
  Validation = 'VALIDATION_ERROR',
  Business = 'BUSINESS_ERROR',
  Unknown = 'UNKNOWN_ERROR'
}

/**
 * Application error class
 */
export class AppError extends Error {
  type: ErrorType
  code?: string
  details?: any
  
  constructor(message: string, type: ErrorType, code?: string, details?: any) {
    super(message)
    this.name = 'AppError'
    this.type = type
    this.code = code
    this.details = details
  }
}
```

### Error Handling Utilities

```typescript
// utils/error.ts

import { ElMessage } from 'element-plus'
import { AppError, ErrorType } from '@/types/common'

/**
 * Handle API error and show user-friendly message
 */
export function handleApiError(error: any): void {
  if (error instanceof AppError) {
    switch (error.type) {
      case ErrorType.Network:
        ElMessage.error('网络连接失败，请检查网络')
        break
      case ErrorType.Authentication:
        ElMessage.error('登录已过期，请重新登录')
        break
      case ErrorType.Authorization:
        ElMessage.error('没有权限执行此操作')
        break
      case ErrorType.Validation:
        ElMessage.error(error.message || '数据验证失败')
        break
      case ErrorType.Business:
        ElMessage.error(error.message || '操作失败')
        break
      default:
        ElMessage.error('未知错误，请稍后重试')
    }
  } else {
    ElMessage.error(error.message || '操作失败')
  }
  
  // Log error in development
  if (import.meta.env.DEV) {
    console.error('Error:', error)
  }
}

/**
 * Create error from HTTP response
 */
export function createErrorFromResponse(response: any): AppError {
  const { status, data } = response
  
  let type: ErrorType
  switch (status) {
    case 401:
      type = ErrorType.Authentication
      break
    case 403:
      type = ErrorType.Authorization
      break
    case 422:
      type = ErrorType.Validation
      break
    default:
      type = ErrorType.Business
  }
  
  return new AppError(
    data?.message || '请求失败',
    type,
    data?.code,
    data?.details
  )
}
```

### Error Handling in Composables

```typescript
// composables/core/useRequest.ts

import { ref } from 'vue'
import type { Ref } from 'vue'
import { handleApiError } from '@/utils/error'

export interface UseRequestOptions<T, P extends any[]> {
  onSuccess?: (data: T) => void
  onError?: (error: any) => void
  immediate?: boolean
  initialData?: T
}

export interface UseRequestReturn<T, P extends any[]> {
  data: Ref<T | undefined>
  loading: Ref<boolean>
  error: Ref<any>
  execute: (...args: P) => Promise<T | undefined>
  reset: () => void
}

export function useRequest<T, P extends any[]>(
  requestFn: (...args: P) => Promise<T>,
  options: UseRequestOptions<T, P> = {}
): UseRequestReturn<T, P> {
  const data = ref<T | undefined>(options.initialData)
  const loading = ref(false)
  const error = ref<any>(null)
  
  async function execute(...args: P): Promise<T | undefined> {
    loading.value = true
    error.value = null
    
    try {
      const result = await requestFn(...args)
      data.value = result
      options.onSuccess?.(result)
      return result
    } catch (err) {
      error.value = err
      handleApiError(err)
      options.onError?.(err)
      return undefined
    } finally {
      loading.value = false
    }
  }
  
  function reset() {
    data.value = options.initialData
    loading.value = false
    error.value = null
  }
  
  if (options.immediate) {
    execute(...([] as unknown as P))
  }
  
  return {
    data,
    loading,
    error,
    execute,
    reset
  }
}
```

## Testing Strategy

### Testing Approach

This refactoring project focuses on **architectural patterns, code organization, and development guidelines** rather than algorithmic logic with clear input/output behavior. Therefore, **property-based testing (PBT) is NOT applicable** to this feature.

The testing strategy will focus on:

1. **Unit Tests**: Test individual functions, utilities, and composables
2. **Component Tests**: Test Vue components in isolation
3. **Integration Tests**: Test component interactions and data flow
4. **E2E Tests**: Test critical user workflows (optional, not part of this refactoring)

### Testing Infrastructure

#### Test Framework Configuration

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'tests/',
        '**/*.spec.ts',
        '**/*.test.ts',
        '**/types/**',
        '**/*.d.ts'
      ]
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
```

```typescript
// tests/setup.ts
import { config } from '@vue/test-utils'
import { vi } from 'vitest'
import ElementPlus from 'element-plus'

// Mock Element Plus
config.global.plugins = [ElementPlus]

// Mock router
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    go: vi.fn(),
    back: vi.fn()
  }),
  useRoute: () => ({
    params: {},
    query: {},
    path: '/'
  })
}))

// Mock i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: 'zh-CN' }
  })
}))
```

### Unit Testing Examples

#### Testing Utilities

```typescript
// utils/format.spec.ts
import { describe, it, expect } from 'vitest'
import { formatDate, formatCurrency, formatFileSize } from './format'

describe('format utilities', () => {
  describe('formatDate', () => {
    it('should format date string correctly', () => {
      const date = '2024-01-15T10:30:00Z'
      expect(formatDate(date, 'YYYY-MM-DD')).toBe('2024-01-15')
    })
    
    it('should handle invalid date', () => {
      expect(formatDate('invalid', 'YYYY-MM-DD')).toBe('')
    })
  })
  
  describe('formatCurrency', () => {
    it('should format number as currency', () => {
      expect(formatCurrency(1234.56)).toBe('¥1,234.56')
    })
    
    it('should handle zero', () => {
      expect(formatCurrency(0)).toBe('¥0.00')
    })
  })
  
  describe('formatFileSize', () => {
    it('should format bytes to KB', () => {
      expect(formatFileSize(1024)).toBe('1.00 KB')
    })
    
    it('should format bytes to MB', () => {
      expect(formatFileSize(1048576)).toBe('1.00 MB')
    })
  })
})
```

#### Testing Composables

```typescript
// composables/core/usePagination.spec.ts
import { describe, it, expect } from 'vitest'
import { usePagination } from './usePagination'

describe('usePagination', () => {
  it('should initialize with default values', () => {
    const { currentPage, pageSize, total } = usePagination()
    
    expect(currentPage.value).toBe(1)
    expect(pageSize.value).toBe(20)
    expect(total.value).toBe(0)
  })
  
  it('should calculate total pages correctly', () => {
    const { totalPages, setTotal, setPageSize } = usePagination()
    
    setTotal(100)
    setPageSize(20)
    
    expect(totalPages.value).toBe(5)
  })
  
  it('should handle page navigation', () => {
    const { currentPage, nextPage, prevPage, setTotal } = usePagination()
    
    setTotal(100)
    
    nextPage()
    expect(currentPage.value).toBe(2)
    
    prevPage()
    expect(currentPage.value).toBe(1)
    
    prevPage() // Should not go below 1
    expect(currentPage.value).toBe(1)
  })
  
  it('should calculate offset correctly', () => {
    const { offset, setPage, setPageSize } = usePagination()
    
    setPageSize(20)
    setPage(3)
    
    expect(offset.value).toBe(40) // (3 - 1) * 20
  })
})
```

#### Testing Components

```typescript
// components/common/AppButton/AppButton.spec.ts
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AppButton from './AppButton.vue'

describe('AppButton', () => {
  it('should render button with text', () => {
    const wrapper = mount(AppButton, {
      slots: {
        default: 'Click Me'
      }
    })
    
    expect(wrapper.text()).toBe('Click Me')
  })
  
  it('should emit click event', async () => {
    const wrapper = mount(AppButton)
    
    await wrapper.trigger('click')
    
    expect(wrapper.emitted('click')).toHaveLength(1)
  })
  
  it('should be disabled when loading', () => {
    const wrapper = mount(AppButton, {
      props: {
        loading: true
      }
    })
    
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })
  
  it('should show loading icon when loading', () => {
    const wrapper = mount(AppButton, {
      props: {
        loading: true
      }
    })
    
    expect(wrapper.find('.el-icon-loading').exists()).toBe(true)
  })
})
```

### Integration Testing Examples

```typescript
// views/user/UserList.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UserList from './UserList.vue'
import * as userApi from '@/api/user'

vi.mock('@/api/user')

describe('UserList', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })
  
  it('should load users on mount', async () => {
    const mockUsers = [
      { id: '1', username: 'user1', email: 'user1@example.com' },
      { id: '2', username: 'user2', email: 'user2@example.com' }
    ]
    
    vi.mocked(userApi.getUserList).mockResolvedValue({
      data: mockUsers,
      total: 2
    })
    
    const wrapper = mount(UserList)
    await flushPromises()
    
    expect(userApi.getUserList).toHaveBeenCalled()
    expect(wrapper.findAll('.user-row')).toHaveLength(2)
  })
  
  it('should handle pagination', async () => {
    const wrapper = mount(UserList)
    await flushPromises()
    
    await wrapper.find('.el-pagination').trigger('current-change', 2)
    
    expect(userApi.getUserList).toHaveBeenCalledWith(
      expect.objectContaining({ page: 2 })
    )
  })
})
```

### Test Coverage Goals

- **Utilities**: 90%+ coverage
- **Composables**: 85%+ coverage
- **Components**: 70%+ coverage
- **API Modules**: 80%+ coverage
- **Services**: 85%+ coverage

### Testing Best Practices

1. **Test Behavior, Not Implementation**: Focus on what the code does, not how it does it
2. **Use Descriptive Test Names**: Test names should clearly describe what is being tested
3. **Arrange-Act-Assert Pattern**: Structure tests with clear setup, execution, and verification
4. **Mock External Dependencies**: Mock API calls, router, and other external dependencies
5. **Test Edge Cases**: Include tests for error conditions, empty states, and boundary values
6. **Keep Tests Independent**: Each test should be able to run independently
7. **Use Test Utilities**: Leverage Vue Test Utils and Vitest utilities for cleaner tests

