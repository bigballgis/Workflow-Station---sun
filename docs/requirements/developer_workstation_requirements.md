# 开发人员工作站详细需求

## 1. 概述

开发人员工作站是低代码工作流平台的核心开发工具，为业务开发人员和技术人员提供可视化的流程设计、数据建模、表单设计和业务逻辑配置功能。

### 1.1 目标用户
- **业务分析师**：设计业务流程和表单
- **技术开发人员**：配置数据模型和集成逻辑
- **流程设计师**：创建和优化工作流程
- **系统管理员**：管理功能单元和版本

### 1.2 核心价值
- **可视化开发**：通过拖拽和配置减少编码工作
- **快速原型**：快速构建和验证业务流程
- **版本管理**：完整的功能单元生命周期管理
- **标准化**：统一的开发规范和最佳实践

## 2. 界面设计规范

### 2.1 整体布局
#### 2.1.1 布局结构
```yaml
layout_structure:
  header:
    height: 60px
    components:
      - logo: "工作流开发平台"
      - user_info: "用户头像、姓名、部门"
      - notifications: "系统通知图标"
      - settings: "个人设置入口"
      - logout: "退出登录"
      
  sidebar:
    width: 240px
    collapsible: true
    components:
      - navigation_menu: "主导航菜单"
      - quick_actions: "快捷操作按钮"
      - recent_items: "最近使用的功能单元"
      
  main_content:
    min_width: 800px
    components:
      - breadcrumb: "面包屑导航"
      - toolbar: "页面工具栏"
      - content_area: "主要内容区域"
      - status_bar: "状态栏"
```

#### 2.1.2 响应式设计
```css
/* 响应式断点 */
@media (max-width: 1200px) {
  .sidebar {
    width: 200px;
  }
  .main-content {
    margin-left: 200px;
  }
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }
  .sidebar.open {
    transform: translateX(0);
  }
  .main-content {
    margin-left: 0;
  }
}
```

### 2.2 视觉设计规范
#### 2.2.1 色彩系统
```yaml
color_palette:
  primary:
    main: "#1890ff"
    light: "#40a9ff"
    dark: "#096dd9"
    
  secondary:
    main: "#722ed1"
    light: "#9254de"
    dark: "#531dab"
    
  success: "#52c41a"
  warning: "#faad14"
  error: "#ff4d4f"
  info: "#1890ff"
  
  neutral:
    white: "#ffffff"
    gray_1: "#fafafa"
    gray_2: "#f5f5f5"
    gray_3: "#f0f0f0"
    gray_4: "#d9d9d9"
    gray_5: "#bfbfbf"
    gray_6: "#8c8c8c"
    gray_7: "#595959"
    gray_8: "#434343"
    gray_9: "#262626"
    black: "#000000"
```

#### 2.2.2 字体规范
```yaml
typography:
  font_family:
    primary: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
    monospace: "'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace"
    
  font_sizes:
    xs: "12px"
    sm: "14px"
    base: "16px"
    lg: "18px"
    xl: "20px"
    xxl: "24px"
    xxxl: "32px"
    
  line_heights:
    tight: 1.25
    normal: 1.5
    relaxed: 1.75
```

### 2.3 登录系统
#### 2.3.1 登录界面设计
```vue
<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <img src="/logo.svg" alt="Logo" class="logo" />
        <h1>工作流开发平台</h1>
        <p>企业级低代码开发工具</p>
      </div>
      
      <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef">
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        
        <el-form-item v-if="mfaEnabled">
          <el-input
            v-model="loginForm.mfaCode"
            placeholder="请输入验证码"
            prefix-icon="Shield"
            size="large"
          />
        </el-form-item>
        
        <el-form-item>
          <div class="login-options">
            <el-checkbox v-model="loginForm.rememberMe">
              记住密码
            </el-checkbox>
            <el-link type="primary" @click="showForgotPassword">
              忘记密码？
            </el-link>
          </div>
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loginLoading"
            @click="handleLogin"
            class="login-button"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>
```

#### 2.3.2 认证流程
```yaml
authentication_flow:
  step_1_basic_auth:
    - 用户输入用户名和密码
    - 前端验证输入格式
    - 发送登录请求到后端
    - 后端验证用户凭据
    
  step_2_mfa_check:
    - 检查用户是否启用MFA
    - 如果启用，发送验证码
    - 用户输入验证码
    - 验证MFA代码
    
  step_3_session_creation:
    - 生成JWT访问令牌
    - 创建用户会话
    - 返回用户信息和权限
    - 重定向到主界面
    
  error_handling:
    - 用户名或密码错误
    - 账户被锁定
    - MFA验证失败
    - 网络连接错误
```

## 3. 主导航和功能菜单

### 3.1 主导航结构
#### 3.1.1 导航菜单设计
```yaml
navigation_menu:
  dashboard:
    icon: "dashboard"
    label: "工作台"
    route: "/dashboard"
    description: "概览和快速访问"
    
  function_units:
    icon: "cube"
    label: "功能单元"
    route: "/function-units"
    description: "功能单元管理"
    children:
      - list: "功能单元列表"
      - create: "创建新单元"
      - templates: "模板库"
      
  icon_library:
    icon: "picture"
    label: "图标库"
    route: "/icons"
    description: "图标资源管理"
    
  documentation:
    icon: "book"
    label: "使用说明"
    route: "/docs"
    description: "操作手册和帮助"
    children:
      - getting_started: "快速入门"
      - user_guide: "用户指南"
      - api_reference: "API参考"
      - faq: "常见问题"
      
  settings:
    icon: "setting"
    label: "设置"
    route: "/settings"
    description: "个人和系统设置"
```

#### 3.1.2 搜索功能
```vue
<template>
  <div class="global-search">
    <el-input
      v-model="searchQuery"
      placeholder="搜索功能单元、文档..."
      prefix-icon="Search"
      @input="handleSearch"
      @keyup.enter="performSearch"
    >
      <template #suffix>
        <el-button
          text
          @click="showAdvancedSearch"
          title="高级搜索"
        >
          <el-icon><Filter /></el-icon>
        </el-button>
      </template>
    </el-input>
    
    <!-- 搜索结果下拉 -->
    <div v-if="searchResults.length > 0" class="search-dropdown">
      <div class="search-section" v-for="section in groupedResults" :key="section.type">
        <div class="section-title">{{ section.title }}</div>
        <div
          v-for="item in section.items"
          :key="item.id"
          class="search-item"
          @click="navigateToItem(item)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span class="item-title">{{ item.title }}</span>
          <span class="item-description">{{ item.description }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
```

### 3.2 快捷操作
#### 3.2.1 快捷按钮配置
```yaml
quick_actions:
  create_function_unit:
    label: "新建功能单元"
    icon: "plus"
    shortcut: "Ctrl+N"
    action: "openCreateDialog"
    
  import_function_unit:
    label: "导入功能单元"
    icon: "upload"
    shortcut: "Ctrl+I"
    action: "openImportDialog"
    
  clone_function_unit:
    label: "克隆功能单元"
    icon: "copy"
    shortcut: "Ctrl+D"
    action: "cloneSelected"
    condition: "hasSelection"
    
  export_function_unit:
    label: "导出功能单元"
    icon: "download"
    shortcut: "Ctrl+E"
    action: "exportSelected"
    condition: "hasPublishedSelection"
```

#### 3.2.2 最近使用项目
```typescript
interface RecentItem {
  id: string
  name: string
  type: 'function_unit' | 'workflow' | 'form' | 'table'
  lastModified: Date
  thumbnail?: string
  status: 'draft' | 'published'
}

class RecentItemsManager {
  private maxItems = 10
  
  addRecentItem(item: RecentItem): void {
    const existing = this.recentItems.findIndex(i => i.id === item.id)
    if (existing >= 0) {
      this.recentItems.splice(existing, 1)
    }
    
    this.recentItems.unshift(item)
    
    if (this.recentItems.length > this.maxItems) {
      this.recentItems = this.recentItems.slice(0, this.maxItems)
    }
    
    this.saveToStorage()
  }
  
  getRecentItems(): RecentItem[] {
    return this.recentItems.sort((a, b) => 
      b.lastModified.getTime() - a.lastModified.getTime()
    )
  }
}
```

## 4. 智能帮助系统

### 4.1 自动生成文档
#### 4.1.1 文档生成引擎
```yaml
documentation_engine:
  content_sources:
    - ui_components: "界面组件和操作步骤"
    - api_definitions: "API接口文档"
    - workflow_templates: "流程模板说明"
    - best_practices: "最佳实践指南"
    
  generation_triggers:
    - feature_update: "功能更新时自动重新生成"
    - manual_refresh: "手动刷新文档"
    - scheduled_update: "定时更新（每日凌晨）"
    
  output_formats:
    - interactive_web: "交互式网页文档"
    - pdf_export: "PDF导出功能"
    - markdown_source: "Markdown源文件"
    - api_json: "API文档JSON格式"
```

#### 4.1.2 文档结构
```yaml
documentation_structure:
  getting_started:
    - platform_overview: "平台概述"
    - first_function_unit: "创建第一个功能单元"
    - basic_workflow: "基础工作流设计"
    - publishing_guide: "发布和部署指南"
    
  user_guides:
    workflow_designer:
      - bpmn_elements: "BPMN元素详解"
      - node_configuration: "节点配置指南"
      - process_variables: "流程变量使用"
      - error_handling: "错误处理机制"
      
    table_designer:
      - data_types: "数据类型说明"
      - relationships: "表关系设计"
      - constraints: "约束和索引"
      - migration_scripts: "迁移脚本生成"
      
    form_designer:
      - field_types: "表单字段类型"
      - validation_rules: "验证规则配置"
      - layout_design: "布局设计技巧"
      - dynamic_forms: "动态表单实现"
      
    action_designer:
      - default_actions: "默认动作说明"
      - custom_actions: "自定义动作开发"
      - api_integration: "API集成配置"
      - error_handling: "动作错误处理"
      
  advanced_topics:
    - version_management: "版本管理策略"
    - performance_optimization: "性能优化技巧"
    - security_considerations: "安全注意事项"
    - integration_patterns: "集成模式"
    
  troubleshooting:
    - common_errors: "常见错误解决"
    - debugging_guide: "调试指南"
    - performance_issues: "性能问题排查"
    - support_contacts: "技术支持联系方式"
```

### 4.2 交互式帮助
#### 4.2.1 上下文帮助
```typescript
interface ContextHelp {
  trigger: 'hover' | 'click' | 'focus'
  position: 'top' | 'bottom' | 'left' | 'right'
  content: {
    title: string
    description: string
    examples?: string[]
    links?: Array<{
      text: string
      url: string
    }>
  }
}

class HelpSystem {
  private helpData: Map<string, ContextHelp> = new Map()
  
  registerHelp(elementId: string, help: ContextHelp): void {
    this.helpData.set(elementId, help)
    this.attachHelpTrigger(elementId, help)
  }
  
  showHelp(elementId: string): void {
    const help = this.helpData.get(elementId)
    if (help) {
      this.displayHelpTooltip(elementId, help)
    }
  }
  
  showGuidedTour(tourName: string): void {
    const tour = this.tours.get(tourName)
    if (tour) {
      this.startInteractiveTour(tour)
    }
  }
}
```

#### 4.2.2 引导式教程
```yaml
guided_tours:
  first_time_user:
    name: "新手入门"
    steps:
      - welcome: "欢迎使用工作流开发平台"
      - navigation: "了解界面布局"
      - create_unit: "创建第一个功能单元"
      - design_workflow: "设计简单流程"
      - test_workflow: "测试流程"
      - publish: "发布功能单元"
      
  workflow_design:
    name: "流程设计进阶"
    steps:
      - complex_flows: "复杂流程设计"
      - parallel_processing: "并行处理配置"
      - conditional_routing: "条件路由设置"
      - error_handling: "异常处理机制"
      
  form_design:
    name: "表单设计技巧"
    steps:
      - layout_principles: "布局设计原则"
      - field_validation: "字段验证配置"
      - dynamic_behavior: "动态行为设置"
      - responsive_design: "响应式设计"
```

### 4.3 智能提示系统
#### 4.3.1 代码补全
```typescript
interface AutoComplete {
  trigger: string[]
  suggestions: Array<{
    label: string
    insertText: string
    detail?: string
    documentation?: string
    kind: 'function' | 'variable' | 'keyword' | 'snippet'
  }>
}

class IntelliSenseProvider {
  private completionItems: Map<string, AutoComplete[]> = new Map()
  
  registerCompletions(context: string, completions: AutoComplete[]): void {
    this.completionItems.set(context, completions)
  }
  
  getCompletions(context: string, position: number, text: string): AutoComplete[] {
    const contextCompletions = this.completionItems.get(context) || []
    const currentWord = this.getCurrentWord(text, position)
    
    return contextCompletions.filter(completion =>
      completion.suggestions.some(s => 
        s.label.toLowerCase().includes(currentWord.toLowerCase())
      )
    )
  }
}
```

#### 4.3.2 实时验证提示
```yaml
validation_hints:
  workflow_design:
    - orphaned_nodes: "检测孤立节点"
    - missing_connections: "缺失连接线"
    - invalid_expressions: "无效表达式"
    - circular_dependencies: "循环依赖检测"
    
  table_design:
    - naming_conventions: "命名规范检查"
    - data_type_compatibility: "数据类型兼容性"
    - foreign_key_validation: "外键约束验证"
    - index_optimization: "索引优化建议"
    
  form_design:
    - accessibility_compliance: "无障碍访问合规"
    - validation_completeness: "验证规则完整性"
    - layout_responsiveness: "布局响应性检查"
    - performance_impact: "性能影响评估"
```

## 5. 图标资源管理系统

### 5.1 图标库架构
#### 5.1.1 图标分类体系
```yaml
icon_categories:
  system_icons:
    description: "系统内置图标"
    categories:
      - workflow: "流程相关图标"
      - forms: "表单相关图标"
      - actions: "动作相关图标"
      - status: "状态指示图标"
      - navigation: "导航相关图标"
    read_only: true
    
  business_icons:
    description: "业务场景图标"
    categories:
      - finance: "财务相关"
      - hr: "人力资源"
      - procurement: "采购相关"
      - approval: "审批相关"
      - notification: "通知相关"
    customizable: true
    
  custom_icons:
    description: "用户自定义图标"
    upload_enabled: true
    management_enabled: true
    categories: "用户自定义分类"
```

#### 5.1.2 图标存储规范
```yaml
icon_storage:
  file_formats:
    supported: ["SVG", "PNG", "ICO", "WEBP"]
    preferred: "SVG"
    
  size_requirements:
    min_size: "16x16px"
    max_size: "512x512px"
    recommended_sizes: ["16x16", "24x24", "32x32", "48x48", "64x64"]
    
  file_constraints:
    max_file_size: "2MB"
    svg_optimization: true
    png_compression: true
    
  storage_structure:
    base_path: "/icons"
    system_path: "/icons/system"
    business_path: "/icons/business"
    custom_path: "/icons/custom/{user_id}"
    
  metadata:
    required_fields:
      - name: "图标名称"
      - category: "分类"
      - tags: "标签数组"
      - created_by: "创建者"
      - created_at: "创建时间"
    optional_fields:
      - description: "描述"
      - keywords: "关键词"
      - license: "许可证信息"
      - source_url: "来源URL"
```

### 5.2 图标管理界面
#### 5.2.1 图标浏览器
```vue
<template>
  <div class="icon-browser">
    <!-- 搜索和筛选 -->
    <div class="icon-toolbar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索图标..."
        prefix-icon="Search"
        clearable
      />
      
      <el-select v-model="selectedCategory" placeholder="选择分类">
        <el-option
          v-for="category in categories"
          :key="category.value"
          :label="category.label"
          :value="category.value"
        />
      </el-select>
      
      <el-select v-model="iconSize" placeholder="图标大小">
        <el-option label="小 (24px)" value="24" />
        <el-option label="中 (32px)" value="32" />
        <el-option label="大 (48px)" value="48" />
      </el-select>
      
      <el-button type="primary" @click="showUploadDialog">
        <el-icon><Upload /></el-icon>
        上传图标
      </el-button>
    </div>
    
    <!-- 图标网格 -->
    <div class="icon-grid" :class="`size-${iconSize}`">
      <div
        v-for="icon in filteredIcons"
        :key="icon.id"
        class="icon-item"
        :class="{ selected: selectedIcons.includes(icon.id) }"
        @click="toggleIconSelection(icon)"
        @dblclick="selectIcon(icon)"
      >
        <div class="icon-preview">
          <img :src="icon.url" :alt="icon.name" />
        </div>
        <div class="icon-info">
          <div class="icon-name">{{ icon.name }}</div>
          <div class="icon-tags">
            <el-tag
              v-for="tag in icon.tags"
              :key="tag"
              size="small"
              type="info"
            >
              {{ tag }}
            </el-tag>
          </div>
        </div>
        <div class="icon-actions" v-if="icon.editable">
          <el-button size="small" text @click.stop="editIcon(icon)">
            <el-icon><Edit /></el-icon>
          </el-button>
          <el-button size="small" text type="danger" @click.stop="deleteIcon(icon)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
    
    <!-- 分页 -->
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="totalIcons"
      :page-sizes="[20, 40, 60, 100]"
      layout="total, sizes, prev, pager, next, jumper"
    />
  </div>
</template>
```

#### 5.2.2 图标上传功能
```typescript
interface IconUpload {
  file: File
  name: string
  category: string
  tags: string[]
  description?: string
}

class IconUploadManager {
  private allowedFormats = ['image/svg+xml', 'image/png', 'image/x-icon', 'image/webp']
  private maxFileSize = 2 * 1024 * 1024 // 2MB
  
  async uploadIcon(upload: IconUpload): Promise<IconMetadata> {
    // 1. 验证文件格式和大小
    this.validateFile(upload.file)
    
    // 2. 优化图标文件
    const optimizedFile = await this.optimizeIcon(upload.file)
    
    // 3. 生成缩略图
    const thumbnails = await this.generateThumbnails(optimizedFile)
    
    // 4. 上传到MinIO
    const uploadResult = await this.uploadToStorage(optimizedFile, thumbnails)
    
    // 5. 保存元数据
    const metadata: IconMetadata = {
      id: generateUUID(),
      name: upload.name,
      category: upload.category,
      tags: upload.tags,
      description: upload.description,
      url: uploadResult.url,
      thumbnails: uploadResult.thumbnails,
      fileSize: optimizedFile.size,
      format: optimizedFile.type,
      createdBy: getCurrentUser().id,
      createdAt: new Date(),
      editable: true
    }
    
    await this.saveMetadata(metadata)
    return metadata
  }
  
  private validateFile(file: File): void {
    if (!this.allowedFormats.includes(file.type)) {
      throw new Error(`不支持的文件格式: ${file.type}`)
    }
    
    if (file.size > this.maxFileSize) {
      throw new Error(`文件大小超过限制: ${file.size} > ${this.maxFileSize}`)
    }
  }
  
  private async optimizeIcon(file: File): Promise<File> {
    if (file.type === 'image/svg+xml') {
      return this.optimizeSVG(file)
    } else if (file.type === 'image/png') {
      return this.compressPNG(file)
    }
    return file
  }
}
```

### 5.3 图标使用和集成
#### 5.3.1 图标选择器组件
```vue
<template>
  <div class="icon-selector">
    <el-button @click="showIconPicker" class="icon-selector-trigger">
      <img v-if="selectedIcon" :src="selectedIcon.url" class="selected-icon" />
      <el-icon v-else><Picture /></el-icon>
      <span>{{ selectedIcon ? selectedIcon.name : '选择图标' }}</span>
    </el-button>
    
    <el-dialog
      v-model="iconPickerVisible"
      title="选择图标"
      width="80%"
      :before-close="handleClose"
    >
      <icon-browser
        :selection-mode="true"
        :max-selection="1"
        @icon-selected="handleIconSelected"
      />
      
      <template #footer>
        <el-button @click="iconPickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSelection">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
interface Props {
  modelValue?: IconMetadata
  placeholder?: string
  disabled?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: IconMetadata | undefined): void
  (e: 'change', value: IconMetadata | undefined): void
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '选择图标'
})

const emit = defineEmits<Emits>()

const selectedIcon = computed(() => props.modelValue)
const iconPickerVisible = ref(false)
const tempSelection = ref<IconMetadata>()

const showIconPicker = () => {
  if (!props.disabled) {
    iconPickerVisible.value = true
    tempSelection.value = selectedIcon.value
  }
}

const handleIconSelected = (icon: IconMetadata) => {
  tempSelection.value = icon
}

const confirmSelection = () => {
  emit('update:modelValue', tempSelection.value)
  emit('change', tempSelection.value)
  iconPickerVisible.value = false
}
</script>
```

#### 5.3.2 图标预览和导出
```typescript
class IconPreviewManager {
  generatePreview(icon: IconMetadata, size: number = 64): string {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')!
    
    canvas.width = size
    canvas.height = size
    
    if (icon.format === 'image/svg+xml') {
      return this.renderSVGPreview(icon.url, size)
    } else {
      return this.renderImagePreview(icon.url, size)
    }
  }
  
  async exportIcons(iconIds: string[], format: 'zip' | 'sprite'): Promise<Blob> {
    const icons = await this.getIconsByIds(iconIds)
    
    if (format === 'zip') {
      return this.createZipArchive(icons)
    } else {
      return this.createSpriteSheet(icons)
    }
  }
  
  private async createSpriteSheet(icons: IconMetadata[]): Promise<Blob> {
    const spriteSize = Math.ceil(Math.sqrt(icons.length)) * 64
    const canvas = document.createElement('canvas')
    canvas.width = spriteSize
    canvas.height = spriteSize
    
    const ctx = canvas.getContext('2d')!
    let x = 0, y = 0
    
    for (const icon of icons) {
      await this.drawIconToCanvas(ctx, icon, x, y, 64)
      
      x += 64
      if (x >= spriteSize) {
        x = 0
        y += 64
      }
    }
    
    return new Promise(resolve => {
      canvas.toBlob(resolve!, 'image/png')
    })
  }
}
```

## 功能单元管理
- 功能单元采用grid形式显示，每个功能单元占一个卡片。
- 标题栏显示“创建新功能单元”按钮，点击后弹出创建对话框，输入名称、描述等基本信息。
- 每个格子包含：图标（可上传或选择）、版本（自动生成或手动输入）、名称、状态（草稿/已发布）、描述等信息。
- 操作功能：
  - 发布：仅在功能单元为草稿状态时显示，点击后确认发布，状态变为已发布。
  - 导出：仅在功能单元为已发布状态时显示，点击后下载功能单元包（包含流程、表、表单、动作配置）。
  - 删除：点击后确认删除对话框。
  - 编辑：点击后进入功能单元编辑界面，包含以下tab页：
    - 流程设计器
    - 表设计器
    - 表单设计器
    - 动作设计器
    - tab栏布局：中间显示当前功能单元的名字，右侧显示当前功能单元的状态、发布/删除按钮和设置按钮（用于设置功能单元的详细信息：图标、版本、名称、状态、描述等）。

## 功能单元组成
- 功能单元包括：图标、名称、版本、状态、ID（自动生成）。
- 功能单元状态：编辑状态不能导出，只有发布状态才能导出。
- 组成：
  - 一个工作流：通过流程设计器创建。
  - 一张主表：通过表设计器创建。
  - 若干张子表：通过表设计器创建，支持父子关系。
  - 若干动作表：通过表设计器创建，用于动作表单数据。
- 绑定：
  - 主表和子表与主表单绑定：在表单设计器中选择绑定表。
  - 动作表与动作表单绑定：在表单设计器中选择绑定表。
- 一个功能单元有数个主表单，主表单与工作流步骤绑定。一个流程节点可以绑定一个主表单和若干默认或自定义的动作。

## 组件关系
- **功能单元** 是顶层容器，包含所有组件。
- **表**（主表、子表、动作表）提供数据存储结构，通过表设计器创建，生成DDL用于数据库创建。
- **表单**（主表单、动作表单）提供用户界面，通过表单设计器创建，绑定到表和流程节点。
- **动作** 定义用户操作，通过动作设计器创建，绑定到流程步骤，可调用API或弹出表单。
- **工作流** 通过流程设计器创建，连接表单和动作：节点绑定表单，步骤绑定动作。
- 整体关系：功能单元 → 工作流（绑定表单和动作） → 表单（绑定表） → 表（数据存储） → 动作（操作逻辑）。

## 流程设计器
- 使用bpmn.js实现，适配Flowable，支持拖拽创建节点、连接线。
- 流程的具体步骤可以与表单和动作进行绑定：右键节点，选择绑定表单/动作。
- 独立tab页，标题栏显示流程名字（默认与功能单元名字一致，可编辑）、保存按钮（点击保存流程定义）和设置按钮（配置流程属性，如描述、变量）。
- 用户操作：拖拽工具栏中的元素到画布，编辑属性，保存后生成BPMN XML。
- 错误处理：绑定无效表单/动作时提示“表单不存在，请先创建表单”；保存失败时显示BPMN语法错误。

## 表设计器
- tab页显示表管理界面，列表显示所有表（主表、子表、动作表）。
- 一个功能单元只能有一个主表。
- 表管理器有增删改功能，增删改按钮在数据行最后一列。
- 点击修改或新增表进入表设计器界面：使用Handsontable显示表结构，列包括字段名、类型、长度等。
- 子表可以选择主表或另一个子表作为父表，防止循环依赖：下拉选择父表，系统检查循环。
- 表设计器生成DDL（数据定义语言），在admin center导入时动态创建或修改表，而不是直接生成数据表供前端使用。
- 在开发者工作站的schema中测试DDL：点击测试按钮，执行SQL，显示成功/失败及错误信息。
- 保证生成的SQL可在admin center中增量部署，根据版本更新表结构：比较版本差异，生成迁移脚本。
- 错误处理：字段类型不匹配时提示“字段类型无效，请选择支持的类型”；循环依赖检测失败时阻止保存；DDL测试失败显示具体错误和修复建议。

## 表单设计器
- tab页显示表单管理界面，列表显示所有表单（主表单、动作表单）。
- 支持增删改功能，按钮在行末。
- 点击修改或新增表单进入表单设计器界面：使用form-create拖拽组件设计表单，设置字段属性。
- 表单设计器生成表单配置（JSON格式），在admin center导入时使用，而不是直接生成表单供前端使用。
- 在开发者工作站中测试表单配置：点击预览按钮，显示表单渲染效果。
- 保证表单配置可在admin center中增量部署，根据版本更新表单结构：版本控制表单配置。
- 主表单绑定具体的流程节点：在流程设计器中选择节点，关联表单。
- 动作表单绑定具体的动作：在动作设计器中选择动作，关联表单。
- 错误处理：字段绑定表不存在时提示“表不存在，请先创建表”；预览失败显示JSON语法错误；必填字段未设置时警告。

## 动作设计器
- 显示动作列表，包括默认和自定义动作。
- 默认动作：提交、审批、回退等，系统初始化，配置绑定流程步骤和可见角色：选择步骤、角色。
- 自定义动作：
  - 直接调用API：输入API URL、方法、参数。
  - 弹出一个动作表单：选择关联表单，保存后触发处理。
- 动作可与一个或多个流程步骤绑定：在流程设计器中绑定，表现为任务或流程的操作按钮，可选按钮颜色（颜色选择器）。
- 用户操作：添加新动作，编辑属性，绑定步骤。
- 错误处理：API URL无效时提示“URL格式错误，请输入有效HTTP/HTTPS URL”；绑定步骤不存在时警告；动作表单未选择时阻止保存。

## 错误处理和验证
- **输入验证**：
  - 功能单元名称：必须唯一，不能重复；长度限制（1-50字符）；不允许特殊字符。
  - 表字段：类型检查（文本、数字、日期等）；长度/精度限制；必填字段标记。
  - 表单字段：必填验证；数据类型匹配（如数字字段只能输入数字）。
  - 动作配置：API URL格式验证；参数必填检查。
- **错误提示**：
  - DDL测试失败：显示具体SQL错误信息、行号、建议修复方法。
  - 绑定冲突：如表字段与表单不匹配时，弹出警告对话框。
  - 循环依赖：子表选择父表时检测循环，提示“无法选择，会造成循环依赖”。
  - 保存失败：网络错误或权限不足时，显示友好提示。
- **回滚机制**：
  - 未保存更改：离开页面时提示“有未保存的更改，是否保存？”。
  - 自动保存：编辑过程中每5分钟自动保存草稿，避免数据丢失。
  - 版本回滚：功能单元编辑失败时，可回滚到上次成功保存的版本。
- **其他验证**：
  - 权限检查：无编辑权限时，禁用相关按钮并提示。
  - 数据一致性：发布前检查所有绑定是否完整，缺失时阻止发布。

## 7. 流程设计器详细规范

### 7.1 BPMN流程设计器界面设计

#### 7.1.1 设计器整体布局
**界面分区**：
- 左侧工具面板：宽度200像素，包含BPMN元素工具箱
- 中央画布区域：占据剩余宽度，最小宽度600像素
- 右侧属性面板：宽度300像素，可折叠隐藏
- 顶部工具栏：高度50像素，包含常用操作按钮
- 底部状态栏：高度30像素，显示画布状态信息

**工具栏功能按钮**：
- 文件操作：新建、打开、保存、另存为
- 编辑操作：撤销、重做、复制、粘贴、删除
- 视图操作：放大、缩小、适应画布、实际大小
- 验证操作：语法检查、流程验证、预览测试
- 导出操作：导出图片、导出XML、打印

#### 7.1.2 BPMN元素工具箱设计
**事件类元素**：
- 开始事件：圆形绿色图标，用于标记流程开始
- 结束事件：圆形红色图标，用于标记流程结束
- 中间事件：圆形黄色图标，用于流程中的事件处理
- 边界事件：附加在活动边界的事件

**活动类元素**：
- 用户任务：矩形蓝色图标，需要人工处理的任务
- 服务任务：矩形灰色图标，系统自动执行的任务
- 脚本任务：矩形紫色图标，执行脚本代码的任务
- 子流程：矩形带加号图标，包含其他流程的任务

**网关类元素**：
- 排他网关：菱形黄色图标，基于条件的单路径选择
- 并行网关：菱形绿色图标，多路径并行执行
- 包容网关：菱形橙色图标，基于条件的多路径选择
- 事件网关：菱形紫色图标，基于事件的路径选择

**连接类元素**：
- 顺序流：带箭头的实线，连接流程元素
- 消息流：带箭头的虚线，表示消息传递
- 关联线：点线，用于连接注释和元素

#### 7.1.3 画布操作功能
**元素拖拽功能**：
- 从工具箱拖拽元素到画布创建新元素
- 拖拽画布上的元素改变位置
- 支持多选元素进行批量移动
- 拖拽时显示对齐辅助线

**连接线绘制**：
- 点击源元素后点击目标元素创建连接
- 拖拽元素边缘的连接点创建连接
- 自动路径规划避免元素重叠
- 支持手动调整连接线路径

**选择和编辑**：
- 单击选择单个元素，显示选择框
- 按住Ctrl键多选元素
- 拖拽选择框进行区域选择
- 双击元素进入快速编辑模式

#### 7.1.4 属性配置面板
**通用属性配置**：
- 元素ID：自动生成的唯一标识符
- 元素名称：用户自定义的显示名称
- 元素描述：详细的说明文字
- 文档链接：相关文档的URL链接

**用户任务特有属性**：
- 任务分配方式：指定用户、指定角色、表达式分配
- 分配用户：从用户列表中选择具体用户
- 候选角色：从角色列表中选择候选角色
- 任务优先级：设置任务的优先级别
- 到期时间：设置任务的截止时间
- 表单绑定：选择与任务关联的表单
- 动作绑定：选择任务可执行的动作

**网关条件配置**：
- 默认路径：设置网关的默认执行路径
- 条件表达式：为每个出口设置条件表达式
- 表达式编辑器：提供可视化的表达式编辑工具
- 条件测试：支持条件表达式的测试验证

### 7.2 流程验证和测试功能

#### 7.2.1 实时语法验证
**验证规则检查**：
- 结构完整性：检查流程是否有开始和结束事件
- 连接有效性：检查所有元素是否正确连接
- 网关条件：检查网关是否配置了必要的条件
- 任务分配：检查用户任务是否配置了分配规则
- 表单绑定：检查任务是否绑定了有效的表单

**错误提示显示**：
- 错误元素用红色边框标识
- 鼠标悬停显示具体错误信息
- 右侧面板显示所有错误列表
- 点击错误项自动定位到对应元素
- 提供修复建议和帮助链接

**警告信息提示**：
- 潜在问题用黄色边框标识
- 性能优化建议
- 最佳实践提醒
- 可选配置项提示

#### 7.2.2 流程模拟测试
**测试场景配置**：
- 创建测试用例：定义测试场景名称和描述
- 设置初始变量：配置流程启动时的变量值
- 模拟用户操作：为每个用户任务定义模拟操作
- 配置测试数据：准备测试所需的数据集

**模拟执行过程**：
- 逐步执行流程：按步骤执行流程逻辑
- 高亮当前节点：正在执行的节点用绿色高亮
- 显示执行路径：已执行的路径用绿色标识
- 记录执行日志：详细记录每步的执行情况
- 变量状态跟踪：实时显示流程变量的变化

**测试结果分析**：
- 执行时长统计：记录每个节点的执行时间
- 路径覆盖分析：检查哪些路径被执行了
- 异常情况记录：记录执行过程中的异常
- 性能瓶颈识别：识别执行缓慢的节点
- 测试报告生成：生成详细的测试报告

### 7.3 流程版本管理

#### 7.3.1 版本控制功能
**版本创建**：
- 自动版本号：按照语义化版本规则自动生成
- 手动版本号：允许用户自定义版本号
- 版本描述：记录版本的主要变更内容
- 变更标记：标识哪些元素发生了变更

**版本比较**：
- 可视化对比：并排显示两个版本的差异
- 变更高亮：用不同颜色标识新增、修改、删除的元素
- 详细差异：显示元素属性的具体变更内容
- 影响分析：分析版本变更对现有流程实例的影响

**版本回滚**：
- 选择回滚版本：从版本历史中选择要回滚的版本
- 影响评估：评估回滚操作的影响范围
- 确认回滚：提供二次确认避免误操作
- 回滚记录：记录回滚操作的详细信息

#### 7.3.2 变更历史记录
**操作记录**：
- 操作时间：精确到秒的操作时间戳
- 操作用户：执行操作的用户信息
- 操作类型：创建、修改、删除、移动等
- 操作对象：被操作的具体元素
- 操作详情：操作的具体内容描述

**历史查看**：
- 时间线视图：按时间顺序显示所有变更
- 筛选功能：按用户、时间、操作类型筛选
- 搜索功能：搜索特定的变更记录
- 详情查看：点击记录查看详细的变更内容

**变更统计**：
- 活跃度统计：统计流程的修改频率
- 贡献者统计：统计各用户的贡献度
- 热点分析：识别经常被修改的元素
- 稳定性评估：评估流程设计的稳定性
```

#### 7.1.2 自定义元素面板
```typescript
class CustomPaletteProvider {
  constructor(palette: Palette, create: Create, elementFactory: ElementFactory) {
    this.palette = palette
    this.create = create
    this.elementFactory = elementFactory
    
    palette.registerProvider(this)
  }
  
  getPaletteEntries(): PaletteEntries {
    return {
      'create.start-event': {
        group: 'event',
        className: 'bpmn-icon-start-event-none',
        title: '开始事件',
        action: {
          dragstart: this.createStartEvent.bind(this),
          click: this.createStartEvent.bind(this)
        }
      },
      'create.user-task': {
        group: 'activity',
        className: 'bpmn-icon-user-task',
        title: '用户任务',
        action: {
          dragstart: this.createUserTask.bind(this),
          click: this.createUserTask.bind(this)
        }
      },
      'create.service-task': {
        group: 'activity',
        className: 'bpmn-icon-service-task',
        title: '服务任务',
        action: {
          dragstart: this.createServiceTask.bind(this),
          click: this.createServiceTask.bind(this)
        }
      },
      'create.exclusive-gateway': {
        group: 'gateway',
        className: 'bpmn-icon-gateway-xor',
        title: '排他网关',
        action: {
          dragstart: this.createExclusiveGateway.bind(this),
          click: this.createExclusiveGateway.bind(this)
        }
      },
      'create.parallel-gateway': {
        group: 'gateway',
        className: 'bpmn-icon-gateway-parallel',
        title: '并行网关',
        action: {
          dragstart: this.createParallelGateway.bind(this),
          click: this.createParallelGateway.bind(this)
        }
      },
      'create.end-event': {
        group: 'event',
        className: 'bpmn-icon-end-event-none',
        title: '结束事件',
        action: {
          dragstart: this.createEndEvent.bind(this),
          click: this.createEndEvent.bind(this)
        }
      }
    }
  }
}
```

### 7.2 节点配置和属性面板
#### 7.2.1 用户任务配置
```vue
<template>
  <div class="user-task-properties">
    <el-form :model="taskProperties" label-width="120px">
      <el-form-item label="任务名称">
        <el-input v-model="taskProperties.name" />
      </el-form-item>
      
      <el-form-item label="任务描述">
        <el-input v-model="taskProperties.documentation" type="textarea" />
      </el-form-item>
      
      <el-form-item label="分配类型">
        <el-select v-model="taskProperties.assignmentType">
          <el-option label="指定用户" value="user" />
          <el-option label="指定角色" value="role" />
          <el-option label="指定部门" value="department" />
          <el-option label="表达式" value="expression" />
        </el-select>
      </el-form-item>
      
      <el-form-item v-if="taskProperties.assignmentType === 'user'" label="指定用户">
        <user-selector v-model="taskProperties.assignee" />
      </el-form-item>
      
      <el-form-item v-if="taskProperties.assignmentType === 'role'" label="指定角色">
        <role-selector v-model="taskProperties.candidateGroups" />
      </el-form-item>
      
      <el-form-item v-if="taskProperties.assignmentType === 'expression'" label="分配表达式">
        <el-input v-model="taskProperties.assigneeExpression" />
      </el-form-item>
      
      <el-form-item label="绑定表单">
        <form-selector 
          v-model="taskProperties.formKey"
          :forms="availableForms"
        />
      </el-form-item>
      
      <el-form-item label="任务优先级">
        <el-select v-model="taskProperties.priority">
          <el-option label="低" value="25" />
          <el-option label="普通" value="50" />
          <el-option label="高" value="75" />
          <el-option label="紧急" value="100" />
        </el-select>
      </el-form-item>
      
      <el-form-item label="到期时间">
        <el-input v-model="taskProperties.dueDate" placeholder="如：P1D（1天后）" />
      </el-form-item>
      
      <el-form-item label="绑定动作">
        <action-selector 
          v-model="taskProperties.actions"
          :actions="availableActions"
          multiple
        />
      </el-form-item>
    </el-form>
  </div>
</template>
```

#### 7.2.2 网关条件配置
```typescript
interface GatewayCondition {
  id: string
  name: string
  expression: string
  description?: string
  priority: number
}

class GatewayConditionManager {
  private conditions: Map<string, GatewayCondition> = new Map()
  
  addCondition(sequenceFlowId: string, condition: GatewayCondition): void {
    this.conditions.set(sequenceFlowId, condition)
    this.updateBpmnElement(sequenceFlowId, condition)
  }
  
  private updateBpmnElement(sequenceFlowId: string, condition: GatewayCondition): void {
    const modeling = this.modeler.get('modeling')
    const elementRegistry = this.modeler.get('elementRegistry')
    
    const sequenceFlow = elementRegistry.get(sequenceFlowId)
    if (sequenceFlow) {
      modeling.updateProperties(sequenceFlow, {
        name: condition.name,
        conditionExpression: {
          $type: 'bpmn:FormalExpression',
          body: condition.expression
        }
      })
    }
  }
  
  validateConditions(gatewayId: string): ValidationResult {
    const gateway = this.elementRegistry.get(gatewayId)
    const outgoingFlows = gateway.businessObject.outgoing || []
    
    const errors: string[] = []
    const warnings: string[] = []
    
    // 检查是否所有出口都有条件
    for (const flow of outgoingFlows) {
      if (!this.conditions.has(flow.id) && !flow.conditionExpression) {
        errors.push(`序列流 "${flow.name || flow.id}" 缺少条件表达式`)
      }
    }
    
    // 检查是否有默认流
    if (!gateway.businessObject.default && outgoingFlows.length > 1) {
      warnings.push('建议设置一个默认序列流以处理所有条件都不满足的情况')
    }
    
    return { errors, warnings }
  }
}
```

### 7.3 流程验证和测试
#### 7.3.1 实时验证规则
```typescript
interface ValidationRule {
  id: string
  name: string
  description: string
  severity: 'error' | 'warning' | 'info'
  validator: (element: BpmnElement) => ValidationResult
}

class ProcessValidator {
  private rules: ValidationRule[] = [
    {
      id: 'start-event-required',
      name: '开始事件必需',
      description: '流程必须包含至少一个开始事件',
      severity: 'error',
      validator: this.validateStartEvent.bind(this)
    },
    {
      id: 'end-event-required',
      name: '结束事件必需',
      description: '流程必须包含至少一个结束事件',
      severity: 'error',
      validator: this.validateEndEvent.bind(this)
    },
    {
      id: 'orphaned-elements',
      name: '孤立元素检查',
      description: '检查是否存在未连接的元素',
      severity: 'warning',
      validator: this.validateOrphanedElements.bind(this)
    },
    {
      id: 'gateway-conditions',
      name: '网关条件检查',
      description: '检查网关的条件配置',
      severity: 'error',
      validator: this.validateGatewayConditions.bind(this)
    },
    {
      id: 'task-assignments',
      name: '任务分配检查',
      description: '检查用户任务的分配配置',
      severity: 'warning',
      validator: this.validateTaskAssignments.bind(this)
    }
  ]
  
  validateProcess(processDefinition: ProcessDefinition): ValidationReport {
    const results: ValidationResult[] = []
    
    for (const rule of this.rules) {
      try {
        const result = rule.validator(processDefinition)
        if (result.errors.length > 0 || result.warnings.length > 0) {
          results.push({
            ruleId: rule.id,
            ruleName: rule.name,
            severity: rule.severity,
            ...result
          })
        }
      } catch (error) {
        results.push({
          ruleId: rule.id,
          ruleName: rule.name,
          severity: 'error',
          errors: [`验证规则执行失败: ${error.message}`],
          warnings: []
        })
      }
    }
    
    return {
      isValid: results.every(r => r.severity !== 'error'),
      results
    }
  }
}
```

#### 7.3.2 流程模拟测试
```typescript
class ProcessSimulator {
  private processEngine: MockProcessEngine
  private testData: TestDataSet
  
  async simulateProcess(
    processDefinition: ProcessDefinition,
    testScenarios: TestScenario[]
  ): Promise<SimulationReport> {
    const results: SimulationResult[] = []
    
    for (const scenario of testScenarios) {
      try {
        const result = await this.runScenario(processDefinition, scenario)
        results.push(result)
      } catch (error) {
        results.push({
          scenarioId: scenario.id,
          scenarioName: scenario.name,
          success: false,
          error: error.message,
          executionPath: [],
          duration: 0
        })
      }
    }
    
    return {
      processId: processDefinition.id,
      totalScenarios: testScenarios.length,
      successfulScenarios: results.filter(r => r.success).length,
      results
    }
  }
  
  private async runScenario(
    processDefinition: ProcessDefinition,
    scenario: TestScenario
  ): Promise<SimulationResult> {
    const startTime = Date.now()
    const executionPath: ExecutionStep[] = []
    
    // 启动流程实例
    const processInstance = await this.processEngine.startProcess(
      processDefinition.id,
      scenario.variables
    )
    
    executionPath.push({
      elementId: 'start',
      elementName: '流程开始',
      timestamp: new Date(),
      variables: { ...scenario.variables }
    })
    
    // 模拟执行流程
    let currentTasks = await this.processEngine.getTasks(processInstance.id)
    
    while (currentTasks.length > 0) {
      for (const task of currentTasks) {
        const taskAction = scenario.taskActions.find(a => a.taskKey === task.taskDefinitionKey)
        
        if (taskAction) {
          await this.processEngine.completeTask(task.id, taskAction.variables)
          
          executionPath.push({
            elementId: task.taskDefinitionKey,
            elementName: task.name,
            timestamp: new Date(),
            action: taskAction.action,
            variables: taskAction.variables
          })
        } else {
          // 使用默认动作
          await this.processEngine.completeTask(task.id, {})
          
          executionPath.push({
            elementId: task.taskDefinitionKey,
            elementName: task.name,
            timestamp: new Date(),
            action: 'default',
            variables: {}
          })
        }
      }
      
      currentTasks = await this.processEngine.getTasks(processInstance.id)
    }
    
    const endTime = Date.now()
    const finalState = await this.processEngine.getProcessInstance(processInstance.id)
    
    return {
      scenarioId: scenario.id,
      scenarioName: scenario.name,
      success: finalState.ended,
      executionPath,
      duration: endTime - startTime,
      finalVariables: finalState.variables
    }
  }
}
```

## 8. 表设计器详细规范

### 8.1 表设计器界面布局

#### 8.1.1 主界面设计
**整体布局结构**：
- 左侧表列表面板：宽度250像素，显示当前功能单元的所有表
- 中央表格编辑区域：占据主要空间，使用类似Excel的表格界面
- 右侧属性配置面板：宽度300像素，配置选中字段的详细属性
- 顶部工具栏：高度50像素，包含表操作和字段操作按钮
- 底部状态栏：显示当前表信息和DDL生成状态

**表列表面板功能**：
- 显示表类型图标：主表（蓝色数据库图标）、子表（绿色表格图标）、动作表（橙色表单图标）
- 表名称显示：支持内联编辑修改表名
- 表状态指示：已保存（绿色圆点）、有修改（黄色圆点）、有错误（红色圆点）
- 右键菜单：新建表、复制表、删除表、重命名表
- 拖拽排序：支持拖拽调整表的显示顺序

#### 8.1.2 表格编辑区域设计
**列标题设计**：
- 字段名：文本输入，支持内联编辑，必填项
- 数据类型：下拉选择，包含VARCHAR、INTEGER、DECIMAL、DATE、TIMESTAMP、BOOLEAN、TEXT、BLOB
- 长度：数字输入，根据数据类型自动启用或禁用
- 精度：数字输入，仅DECIMAL类型启用
- 小数位：数字输入，仅DECIMAL类型启用
- 允许空值：复选框，默认选中
- 默认值：文本输入，根据数据类型验证格式
- 主键：复选框，一个表只能有一个主键
- 唯一：复选框，设置唯一约束
- 索引：复选框，创建普通索引
- 注释：文本输入，字段说明

**行操作功能**：
- 行号显示：左侧显示行号，便于定位
- 行选择：点击行号选择整行
- 插入行：右键菜单或工具栏按钮在指定位置插入新行
- 删除行：选中行后按Delete键或右键删除
- 移动行：拖拽行号调整字段顺序
- 复制行：Ctrl+C复制，Ctrl+V粘贴

#### 8.1.3 数据类型配置详细说明
**VARCHAR类型配置**：
- 长度范围：1-65535字符
- 默认长度：255字符
- 用途说明：存储可变长度字符串，如姓名、地址等
- 索引建议：长度超过255时不建议创建索引

**INTEGER类型配置**：
- 取值范围：-2147483648到2147483647
- 存储大小：4字节
- 用途说明：存储整数，如年龄、数量等
- 自增选项：可设置为自增主键

**DECIMAL类型配置**：
- 精度范围：1-65位数字
- 小数位范围：0-30位小数
- 用途说明：存储精确的小数，如金额、比率等
- 格式示例：DECIMAL(10,2)表示总共10位数字，其中2位小数

**DATE和TIMESTAMP类型**：
- DATE：仅存储日期，格式YYYY-MM-DD
- TIMESTAMP：存储日期和时间，格式YYYY-MM-DD HH:MM:SS
- 时区处理：TIMESTAMP自动处理时区转换
- 默认值：支持CURRENT_DATE和CURRENT_TIMESTAMP

### 8.2 表关系设计功能

#### 8.2.1 父子表关系配置
**关系类型定义**：
- 一对多关系：一个主表记录对应多个子表记录
- 主外键关联：子表通过外键字段关联主表主键
- 级联操作：配置删除和更新时的级联行为

**关系配置界面**：
- 父表选择：下拉列表选择当前功能单元中的其他表作为父表
- 外键字段：选择子表中用作外键的字段
- 关联字段：选择父表中被关联的字段（通常是主键）
- 级联删除：选择是否在删除父记录时自动删除子记录
- 级联更新：选择是否在更新父记录主键时自动更新子记录外键

**循环依赖检测**：
- 实时检测：在配置关系时实时检查是否会形成循环依赖
- 依赖路径显示：如果检测到循环，显示完整的依赖路径
- 阻止保存：存在循环依赖时阻止保存表结构
- 修复建议：提供解决循环依赖的建议方案

#### 8.2.2 约束和索引管理
**主键约束**：
- 单字段主键：选择一个字段作为主键
- 复合主键：选择多个字段组成复合主键
- 自动索引：主键自动创建唯一索引
- 命名规则：主键约束自动命名为PK_表名

**外键约束**：
- 外键定义：指定外键字段和引用的父表字段
- 约束命名：自动生成或手动指定外键约束名称
- 引用完整性：确保外键值在父表中存在
- 级联操作：CASCADE、SET NULL、RESTRICT等选项

**唯一约束**：
- 单字段唯一：为单个字段创建唯一约束
- 复合唯一：为多个字段组合创建唯一约束
- 约束命名：自动生成或手动指定约束名称
- 空值处理：配置唯一约束对空值的处理方式

**索引管理**：
- 普通索引：提高查询性能的普通索引
- 唯一索引：保证数据唯一性的索引
- 复合索引：多个字段组成的复合索引
- 索引命名：自动生成或手动指定索引名称

### 8.3 DDL生成和测试功能

#### 8.3.1 DDL自动生成
**CREATE TABLE语句生成**：
- 表名处理：自动添加表前缀或后缀（如果配置）
- 字段定义：根据字段配置生成完整的字段定义
- 约束定义：生成主键、外键、唯一等约束定义
- 索引定义：生成普通索引和唯一索引定义
- 注释添加：为表和字段添加注释说明

**ALTER TABLE语句生成**：
- 版本比较：比较当前版本和上一版本的表结构差异
- 增量更新：生成添加字段、修改字段、删除字段的语句
- 约束变更：生成添加或删除约束的语句
- 索引变更：生成创建或删除索引的语句
- 安全检查：检查变更操作的安全性，如删除字段的影响

**数据库方言支持**：
- MySQL语法：生成符合MySQL语法的DDL语句
- PostgreSQL语法：生成符合PostgreSQL语法的DDL语句
- Oracle语法：生成符合Oracle语法的DDL语句
- SQL Server语法：生成符合SQL Server语法的DDL语句

#### 8.3.2 DDL测试验证功能
**语法验证**：
- SQL语法检查：验证生成的DDL语句语法正确性
- 关键字检查：检查是否使用了数据库保留关键字
- 命名规范：检查表名和字段名是否符合命名规范
- 长度限制：检查名称长度是否超过数据库限制

**结构验证**：
- 数据类型兼容性：检查数据类型在目标数据库中的兼容性
- 约束有效性：验证约束定义的有效性
- 关系完整性：检查外键关系的完整性
- 索引合理性：评估索引设置的合理性

**测试执行**：
- 连接测试数据库：连接到开发环境的测试数据库
- 事务执行：在事务中执行DDL语句进行测试
- 错误捕获：捕获执行过程中的错误信息
- 自动回滚：测试完成后自动回滚事务，不影响数据库

**测试报告**：
- 执行结果：显示每条DDL语句的执行结果
- 错误详情：详细显示执行失败的错误信息
- 性能统计：记录DDL语句的执行时间
- 修复建议：针对错误提供修复建议

### 8.4 数据字典生成

#### 8.4.1 字典信息收集
**表级信息**：
- 表名称：中文名称和英文名称
- 表描述：详细的表用途说明
- 创建时间：表结构创建时间
- 修改时间：最后修改时间
- 创建人：表结构设计人员
- 版本信息：当前版本号

**字段级信息**：
- 字段名称：中文名称和英文名称
- 数据类型：完整的数据类型定义
- 长度精度：字段长度和小数精度
- 是否必填：NULL约束信息
- 默认值：字段默认值
- 字段说明：详细的字段用途说明

**关系信息**：
- 主键信息：主键字段列表
- 外键信息：外键关系详细说明
- 索引信息：索引字段和类型
- 约束信息：其他约束条件

#### 8.4.2 字典文档生成
**HTML格式文档**：
- 目录导航：按表分类的导航目录
- 表格展示：使用表格展示字段信息
- 样式美化：专业的CSS样式设计
- 交互功能：支持搜索和筛选功能

**Excel格式文档**：
- 多工作表：每个表一个工作表
- 格式化：使用颜色和边框美化表格
- 公式计算：自动计算统计信息
- 图表展示：生成表关系图表

**PDF格式文档**：
- 专业排版：使用专业的文档排版
- 目录书签：生成PDF书签导航
- 页眉页脚：添加页码和文档信息
- 打印优化：优化打印效果
```

#### 8.1.2 数据类型管理
```typescript
interface DataTypeDefinition {
  name: string
  category: 'string' | 'number' | 'date' | 'boolean' | 'binary'
  hasLength: boolean
  hasPrecision: boolean
  hasScale: boolean
  defaultLength?: number
  maxLength?: number
  validation: (value: any, column: TableColumn) => ValidationResult
}

class DataTypeManager {
  private dataTypes: Map<string, DataTypeDefinition> = new Map([
    ['VARCHAR', {
      name: 'VARCHAR',
      category: 'string',
      hasLength: true,
      hasPrecision: false,
      hasScale: false,
      defaultLength: 255,
      maxLength: 65535,
      validation: this.validateVarchar.bind(this)
    }],
    ['INTEGER', {
      name: 'INTEGER',
      category: 'number',
      hasLength: false,
      hasPrecision: false,
      hasScale: false,
      validation: this.validateInteger.bind(this)
    }],
    ['DECIMAL', {
      name: 'DECIMAL',
      category: 'number',
      hasLength: false,
      hasPrecision: true,
      hasScale: true,
      validation: this.validateDecimal.bind(this)
    }],
    ['DATE', {
      name: 'DATE',
      category: 'date',
      hasLength: false,
      hasPrecision: false,
      hasScale: false,
      validation: this.validateDate.bind(this)
    }],
    ['TIMESTAMP', {
      name: 'TIMESTAMP',
      category: 'date',
      hasLength: false,
      hasPrecision: false,
      hasScale: false,
      validation: this.validateTimestamp.bind(this)
    }],
    ['BOOLEAN', {
      name: 'BOOLEAN',
      category: 'boolean',
      hasLength: false,
      hasPrecision: false,
      hasScale: false,
      validation: this.validateBoolean.bind(this)
    }]
  ])
  
  getDataType(typeName: string): DataTypeDefinition | undefined {
    return this.dataTypes.get(typeName.toUpperCase())
  }
  
  validateColumn(column: TableColumn): ValidationResult {
    const dataType = this.getDataType(column.type)
    if (!dataType) {
      return {
        valid: false,
        errors: [`不支持的数据类型: ${column.type}`]
      }
    }
    
    return dataType.validation(column.defaultValue, column)
  }
}
```

### 8.2 DDL生成和测试
#### 8.2.1 DDL生成器
```typescript
class DDLGenerator {
  private dialect: DatabaseDialect
  
  constructor(dialect: DatabaseDialect = 'postgresql') {
    this.dialect = dialect
  }
  
  generateCreateTableDDL(table: TableSchema): string {
    const columns = table.columns.map(col => this.generateColumnDefinition(col))
    const constraints = this.generateConstraints(table)
    const indexes = this.generateIndexes(table)
    
    let ddl = `CREATE TABLE ${table.name} (\n`
    ddl += columns.join(',\n')
    
    if (constraints.length > 0) {
      ddl += ',\n' + constraints.join(',\n')
    }
    
    ddl += '\n);'
    
    if (indexes.length > 0) {
      ddl += '\n\n' + indexes.join('\n')
    }
    
    return ddl
  }
  
  private generateColumnDefinition(column: TableColumn): string {
    let definition = `  ${column.name} ${this.mapDataType(column.type)}`
    
    // 长度和精度
    if (column.length && this.requiresLength(column.type)) {
      if (column.precision && column.scale) {
        definition += `(${column.precision}, ${column.scale})`
      } else if (column.length) {
        definition += `(${column.length})`
      }
    }
    
    // 非空约束
    if (!column.nullable) {
      definition += ' NOT NULL'
    }
    
    // 默认值
    if (column.defaultValue !== undefined && column.defaultValue !== '') {
      definition += ` DEFAULT ${this.formatDefaultValue(column.defaultValue, column.type)}`
    }
    
    // 唯一约束
    if (column.unique) {
      definition += ' UNIQUE'
    }
    
    // 注释
    if (column.comment) {
      definition += ` COMMENT '${column.comment}'`
    }
    
    return definition
  }
  
  generateAlterTableDDL(oldTable: TableSchema, newTable: TableSchema): string[] {
    const statements: string[] = []
    
    // 比较列变化
    const oldColumns = new Map(oldTable.columns.map(col => [col.name, col]))
    const newColumns = new Map(newTable.columns.map(col => [col.name, col]))
    
    // 添加新列
    for (const [name, column] of newColumns) {
      if (!oldColumns.has(name)) {
        statements.push(`ALTER TABLE ${newTable.name} ADD COLUMN ${this.generateColumnDefinition(column)};`)
      }
    }
    
    // 修改现有列
    for (const [name, newColumn] of newColumns) {
      const oldColumn = oldColumns.get(name)
      if (oldColumn && this.isColumnChanged(oldColumn, newColumn)) {
        statements.push(`ALTER TABLE ${newTable.name} MODIFY COLUMN ${this.generateColumnDefinition(newColumn)};`)
      }
    }
    
    // 删除列
    for (const [name] of oldColumns) {
      if (!newColumns.has(name)) {
        statements.push(`ALTER TABLE ${newTable.name} DROP COLUMN ${name};`)
      }
    }
    
    return statements
  }
}
```

#### 8.2.2 DDL测试和验证
```typescript
class DDLTester {
  private testConnection: DatabaseConnection
  
  constructor(connectionConfig: DatabaseConnectionConfig) {
    this.testConnection = new DatabaseConnection(connectionConfig)
  }
  
  async testDDL(ddlStatements: string[]): Promise<DDLTestResult> {
    const results: StatementResult[] = []
    let transaction: Transaction | null = null
    
    try {
      // 开始事务
      transaction = await this.testConnection.beginTransaction()
      
      for (let i = 0; i < ddlStatements.length; i++) {
        const statement = ddlStatements[i]
        
        try {
          const startTime = Date.now()
          await transaction.execute(statement)
          const endTime = Date.now()
          
          results.push({
            index: i,
            statement,
            success: true,
            executionTime: endTime - startTime
          })
        } catch (error) {
          results.push({
            index: i,
            statement,
            success: false,
            error: error.message,
            sqlState: error.sqlState,
            errorCode: error.code
          })
          
          // DDL错误通常是致命的，停止执行
          break
        }
      }
      
      // 回滚事务（测试环境不实际应用更改）
      await transaction.rollback()
      
      return {
        success: results.every(r => r.success),
        totalStatements: ddlStatements.length,
        successfulStatements: results.filter(r => r.success).length,
        results
      }
      
    } catch (error) {
      if (transaction) {
        await transaction.rollback()
      }
      
      return {
        success: false,
        totalStatements: ddlStatements.length,
        successfulStatements: 0,
        results,
        globalError: error.message
      }
    }
  }
  
  async validateTableStructure(tableName: string, expectedColumns: TableColumn[]): Promise<ValidationResult> {
    try {
      const actualColumns = await this.getTableColumns(tableName)
      const differences = this.compareTableStructures(expectedColumns, actualColumns)
      
      return {
        valid: differences.length === 0,
        differences
      }
    } catch (error) {
      return {
        valid: false,
        error: error.message
      }
    }
  }
}
```

## 9. 表单设计器详细规范

### 9.1 Form-Create集成和定制
#### 9.1.1 表单设计器配置
```typescript
interface FormDesignerConfig {
  container: string
  rule: FormRule[]
  option: FormOption
  components: CustomComponent[]
  validators: CustomValidator[]
  dataBinding: DataBindingConfig
}

class FormDesigner {
  private formCreate: FormCreate
  private componentLibrary: ComponentLibrary
  private dataBindingManager: DataBindingManager
  
  constructor(config: FormDesignerConfig) {
    this.initializeFormCreate(config)
    this.setupCustomComponents()
    this.setupDataBinding()
    this.setupValidation()
  }
  
  private initializeFormCreate(config: FormDesignerConfig): void {
    this.formCreate = FormCreate.designer({
      height: '100%',
      control: [
        {
          type: 'input',
          field: 'input',
          title: '输入框',
          info: '基础文本输入组件',
          props: {
            type: 'text'
          }
        },
        {
          type: 'select',
          field: 'select',
          title: '选择器',
          info: '下拉选择组件',
          options: []
        },
        {
          type: 'datePicker',
          field: 'datePicker',
          title: '日期选择',
          info: '日期时间选择组件'
        },
        {
          type: 'upload',
          field: 'upload',
          title: '文件上传',
          info: '文件上传组件'
        },
        {
          type: 'tableSelect',
          field: 'tableSelect',
          title: '表格选择',
          info: '关联表数据选择组件'
        }
      ],
      menu: {
        'basic': '基础组件',
        'advanced': '高级组件',
        'layout': '布局组件',
        'business': '业务组件'
      }
    })
  }
}
```

#### 9.1.2 自定义组件开发
```vue
<!-- 表格选择组件 -->
<template>
  <div class="table-select-component">
    <el-input
      :model-value="displayValue"
      :placeholder="placeholder"
      readonly
      @click="showSelector"
    >
      <template #suffix>
        <el-button text @click="showSelector">
          <el-icon><Search /></el-icon>
        </el-button>
      </template>
    </el-input>
    
    <el-dialog
      v-model="selectorVisible"
      :title="`选择${tableConfig.displayName}`"
      width="80%"
    >
      <el-table
        :data="tableData"
        @selection-change="handleSelectionChange"
        v-loading="loading"
      >
        <el-table-column
          v-if="multiple"
          type="selection"
          width="55"
        />
        <el-table-column
          v-for="column in displayColumns"
          :key="column.field"
          :prop="column.field"
          :label="column.label"
          :width="column.width"
        />
      </el-table>
      
      <template #footer>
        <el-button @click="selectorVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSelection">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
interface Props {
  modelValue?: any
  tableConfig: TableSelectConfig
  multiple?: boolean
  placeholder?: string
}

interface TableSelectConfig {
  tableName: string
  displayName: string
  valueField: string
  displayField: string
  displayColumns: ColumnConfig[]
  filterConditions?: FilterCondition[]
}

const props = withDefaults(defineProps<Props>(), {
  multiple: false,
  placeholder: '请选择'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
}>()

const selectorVisible = ref(false)
const tableData = ref([])
const selectedRows = ref([])
const loading = ref(false)

const displayValue = computed(() => {
  if (!props.modelValue) return ''
  
  if (props.multiple && Array.isArray(props.modelValue)) {
    return props.modelValue.map(item => item[props.tableConfig.displayField]).join(', ')
  } else {
    return props.modelValue[props.tableConfig.displayField] || ''
  }
})

const showSelector = async () => {
  selectorVisible.value = true
  await loadTableData()
}

const loadTableData = async () => {
  loading.value = true
  try {
    const response = await tableApi.getTableData(
      props.tableConfig.tableName,
      props.tableConfig.filterConditions
    )
    tableData.value = response.data
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const confirmSelection = () => {
  if (props.multiple) {
    emit('update:modelValue', selectedRows.value)
  } else {
    emit('update:modelValue', selectedRows.value[0])
  }
  selectorVisible.value = false
}
</script>
```

### 9.2 数据绑定和验证
#### 9.2.1 数据绑定管理器
```typescript
class DataBindingManager {
  private bindings: Map<string, DataBinding> = new Map()
  private tableSchemas: Map<string, TableSchema> = new Map()
  
  bindFormToTable(formId: string, tableName: string, bindings: FieldBinding[]): void {
    const tableSchema = this.tableSchemas.get(tableName)
    if (!tableSchema) {
      throw new Error(`表 ${tableName} 不存在`)
    }
    
    // 验证字段绑定
    for (const binding of bindings) {
      const column = tableSchema.columns.find(col => col.name === binding.columnName)
      if (!column) {
        throw new Error(`表 ${tableName} 中不存在字段 ${binding.columnName}`)
      }
      
      // 验证数据类型兼容性
      if (!this.isTypeCompatible(binding.fieldType, column.type)) {
        throw new Error(`字段 ${binding.fieldName} 的类型与数据库字段 ${binding.columnName} 不兼容`)
      }
    }
    
    this.bindings.set(formId, {
      formId,
      tableName,
      bindings
    })
  }
  
  generateFormRule(formId: string): FormRule[] {
    const binding = this.bindings.get(formId)
    if (!binding) {
      throw new Error(`表单 ${formId} 未绑定到任何表`)
    }
    
    const tableSchema = this.tableSchemas.get(binding.tableName)!
    const rules: FormRule[] = []
    
    for (const fieldBinding of binding.bindings) {
      const column = tableSchema.columns.find(col => col.name === fieldBinding.columnName)!
      
      const rule: FormRule = {
        type: this.mapColumnTypeToFormType(column.type),
        field: fieldBinding.fieldName,
        title: fieldBinding.displayName || column.comment || column.name,
        props: this.generateFieldProps(column),
        validate: this.generateFieldValidation(column)
      }
      
      rules.push(rule)
    }
    
    return rules
  }
  
  private generateFieldValidation(column: TableColumn): ValidationRule[] {
    const rules: ValidationRule[] = []
    
    // 必填验证
    if (!column.nullable) {
      rules.push({
        required: true,
        message: `${column.comment || column.name}不能为空`
      })
    }
    
    // 长度验证
    if (column.type === 'VARCHAR' && column.length) {
      rules.push({
        max: column.length,
        message: `${column.comment || column.name}长度不能超过${column.length}个字符`
      })
    }
    
    // 数值范围验证
    if (column.type === 'INTEGER') {
      rules.push({
        type: 'number',
        message: `${column.comment || column.name}必须是数字`
      })
    }
    
    // 自定义验证规则
    if (column.customValidation) {
      rules.push({
        validator: (rule: any, value: any, callback: Function) => {
          try {
            const isValid = new Function('value', column.customValidation)(value)
            if (isValid) {
              callback()
            } else {
              callback(new Error('数据格式不正确'))
            }
          } catch (error) {
            callback(new Error('验证规则执行失败'))
          }
        }
      })
    }
    
    return rules
  }
}
```

#### 9.2.2 表单预览和测试
```vue
<template>
  <div class="form-preview">
    <div class="preview-toolbar">
      <el-button-group>
        <el-button @click="previewMode = 'desktop'" :type="previewMode === 'desktop' ? 'primary' : ''">
          <el-icon><Monitor /></el-icon>
          桌面端
        </el-button>
        <el-button @click="previewMode = 'tablet'" :type="previewMode === 'tablet' ? 'primary' : ''">
          <el-icon><Ipad /></el-icon>
          平板端
        </el-button>
        <el-button @click="previewMode = 'mobile'" :type="previewMode === 'mobile' ? 'primary' : ''">
          <el-icon><Iphone /></el-icon>
          移动端
        </el-button>
      </el-button-group>
      
      <el-button @click="testFormValidation">测试验证</el-button>
      <el-button @click="testFormSubmission">测试提交</el-button>
      <el-button @click="exportFormConfig">导出配置</el-button>
    </div>
    
    <div class="preview-container" :class="`preview-${previewMode}`">
      <form-create
        v-model="formData"
        :rule="formRule"
        :option="formOption"
        @submit="handleFormSubmit"
        @validate="handleFormValidate"
      />
    </div>
    
    <!-- 测试结果面板 -->
    <el-drawer v-model="testResultVisible" title="测试结果" size="400px">
      <div class="test-results">
        <el-timeline>
          <el-timeline-item
            v-for="result in testResults"
            :key="result.id"
            :type="result.success ? 'success' : 'danger'"
            :timestamp="result.timestamp"
          >
            <h4>{{ result.title }}</h4>
            <p>{{ result.description }}</p>
            <pre v-if="result.details">{{ result.details }}</pre>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>
```

## 10. 动作设计器详细规范

### 10.1 动作类型和配置
#### 10.1.1 默认动作配置
```typescript
interface DefaultAction {
  id: string
  name: string
  type: 'approve' | 'reject' | 'delegate' | 'transfer' | 'withdraw'
  icon: string
  color: string
  description: string
  configurable: boolean
  systemAction: boolean
}

class DefaultActionManager {
  private defaultActions: DefaultAction[] = [
    {
      id: 'approve',
      name: '同意',
      type: 'approve',
      icon: 'check',
      color: '#67C23A',
      description: '审批通过，流程继续执行',
      configurable: true,
      systemAction: true
    },
    {
      id: 'reject',
      name: '拒绝',
      type: 'reject',
      icon: 'close',
      color: '#F56C6C',
      description: '审批拒绝，流程结束或回退',
      configurable: true,
      systemAction: true
    },
    {
      id: 'delegate',
      name: '委托',
      type: 'delegate',
      icon: 'user',
      color: '#409EFF',
      description: '委托给其他人处理',
      configurable: true,
      systemAction: true
    },
    {
      id: 'transfer',
      name: '转办',
      type: 'transfer',
      icon: 'share',
      color: '#E6A23C',
      description: '转交给其他人处理',
      configurable: true,
      systemAction: true
    },
    {
      id: 'withdraw',
      name: '撤回',
      type: 'withdraw',
      icon: 'back',
      color: '#909399',
      description: '撤回已提交的申请',
      configurable: false,
      systemAction: true
    }
  ]
  
  getDefaultActions(): DefaultAction[] {
    return this.defaultActions
  }
  
  configureAction(actionId: string, config: Partial<DefaultAction>): void {
    const action = this.defaultActions.find(a => a.id === actionId)
    if (!action) {
      throw new Error(`默认动作 ${actionId} 不存在`)
    }
    
    if (!action.configurable) {
      throw new Error(`默认动作 ${actionId} 不允许配置`)
    }
    
    Object.assign(action, config)
  }
}
```

#### 10.1.2 自定义动作配置
```vue
<template>
  <div class="custom-action-designer">
    <el-form :model="actionConfig" :rules="actionRules" ref="actionFormRef" label-width="120px">
      <el-form-item label="动作名称" prop="name">
        <el-input v-model="actionConfig.name" />
      </el-form-item>
      
      <el-form-item label="动作类型" prop="type">
        <el-select v-model="actionConfig.type" @change="handleTypeChange">
          <el-option label="API调用" value="api" />
          <el-option label="表单弹窗" value="form" />
          <el-option label="脚本执行" value="script" />
          <el-option label="外部链接" value="link" />
        </el-select>
      </el-form-item>
      
      <el-form-item label="动作图标">
        <icon-selector v-model="actionConfig.icon" />
      </el-form-item>
      
      <el-form-item label="按钮颜色">
        <el-color-picker v-model="actionConfig.color" />
      </el-form-item>
      
      <!-- API调用配置 -->
      <template v-if="actionConfig.type === 'api'">
        <el-form-item label="API地址" prop="apiUrl">
          <el-input v-model="actionConfig.apiUrl" placeholder="https://api.example.com/endpoint" />
        </el-form-item>
        
        <el-form-item label="请求方法" prop="method">
          <el-select v-model="actionConfig.method">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="请求头">
          <key-value-editor v-model="actionConfig.headers" />
        </el-form-item>
        
        <el-form-item label="请求参数">
          <parameter-editor v-model="actionConfig.parameters" />
        </el-form-item>
      </template>
      
      <!-- 表单弹窗配置 -->
      <template v-if="actionConfig.type === 'form'">
        <el-form-item label="关联表单" prop="formId">
          <form-selector v-model="actionConfig.formId" :forms="availableForms" />
        </el-form-item>
        
        <el-form-item label="弹窗标题">
          <el-input v-model="actionConfig.dialogTitle" />
        </el-form-item>
        
        <el-form-item label="弹窗大小">
          <el-select v-model="actionConfig.dialogSize">
            <el-option label="小" value="small" />
            <el-option label="中" value="medium" />
            <el-option label="大" value="large" />
            <el-option label="全屏" value="fullscreen" />
          </el-select>
        </el-form-item>
      </template>
      
      <!-- 脚本执行配置 -->
      <template v-if="actionConfig.type === 'script'">
        <el-form-item label="执行脚本">
          <code-editor 
            v-model="actionConfig.script"
            language="javascript"
            :height="200"
          />
        </el-form-item>
      </template>
      
      <!-- 外部链接配置 -->
      <template v-if="actionConfig.type === 'link'">
        <el-form-item label="链接地址" prop="url">
          <el-input v-model="actionConfig.url" />
        </el-form-item>
        
        <el-form-item label="打开方式">
          <el-select v-model="actionConfig.target">
            <el-option label="当前窗口" value="_self" />
            <el-option label="新窗口" value="_blank" />
            <el-option label="弹窗" value="popup" />
          </el-select>
        </el-form-item>
      </template>
      
      <el-form-item label="权限控制">
        <role-selector v-model="actionConfig.allowedRoles" multiple />
      </el-form-item>
      
      <el-form-item label="显示条件">
        <condition-editor v-model="actionConfig.displayCondition" />
      </el-form-item>
    </el-form>
    
    <div class="action-preview">
      <h4>预览效果</h4>
      <el-button 
        :type="getButtonType(actionConfig.color)"
        :style="{ backgroundColor: actionConfig.color }"
      >
        <el-icon v-if="actionConfig.icon">
          <component :is="actionConfig.icon" />
        </el-icon>
        {{ actionConfig.name || '动作名称' }}
      </el-button>
    </div>
  </div>
</template>
```

### 10.2 动作执行引擎
#### 10.2.1 动作执行器
```typescript
interface ActionContext {
  processInstanceId: string
  taskId?: string
  userId: string
  variables: Record<string, any>
  formData?: Record<string, any>
}

interface ActionResult {
  success: boolean
  message?: string
  data?: any
  nextAction?: string
  variables?: Record<string, any>
}

class ActionExecutor {
  private apiClient: ApiClient
  private scriptEngine: ScriptEngine
  private formRenderer: FormRenderer
  
  async executeAction(action: ActionConfig, context: ActionContext): Promise<ActionResult> {
    try {
      switch (action.type) {
        case 'api':
          return await this.executeApiAction(action, context)
        case 'form':
          return await this.executeFormAction(action, context)
        case 'script':
          return await this.executeScriptAction(action, context)
        case 'link':
          return this.executeLinkAction(action, context)
        default:
          throw new Error(`不支持的动作类型: ${action.type}`)
      }
    } catch (error) {
      return {
        success: false,
        message: `动作执行失败: ${error.message}`
      }
    }
  }
  
  private async executeApiAction(action: ActionConfig, context: ActionContext): Promise<ActionResult> {
    // 构建请求参数
    const requestData = this.buildRequestData(action.parameters, context)
    
    // 构建请求头
    const headers = this.buildHeaders(action.headers, context)
    
    try {
      const response = await this.apiClient.request({
        url: action.apiUrl,
        method: action.method,
        data: requestData,
        headers
      })
      
      return {
        success: true,
        message: '动作执行成功',
        data: response.data
      }
    } catch (error) {
      return {
        success: false,
        message: `API调用失败: ${error.message}`
      }
    }
  }
  
  private async executeFormAction(action: ActionConfig, context: ActionContext): Promise<ActionResult> {
    // 显示表单对话框
    const formData = await this.formRenderer.showDialog({
      formId: action.formId,
      title: action.dialogTitle,
      size: action.dialogSize,
      initialData: context.formData
    })
    
    if (formData) {
      return {
        success: true,
        message: '表单提交成功',
        data: formData,
        variables: formData
      }
    } else {
      return {
        success: false,
        message: '用户取消了操作'
      }
    }
  }
  
  private async executeScriptAction(action: ActionConfig, context: ActionContext): Promise<ActionResult> {
    try {
      const result = await this.scriptEngine.execute(action.script, {
        context,
        console: {
          log: (...args: any[]) => console.log('[Script]', ...args),
          error: (...args: any[]) => console.error('[Script]', ...args)
        },
        // 提供安全的API
        api: {
          getVariable: (name: string) => context.variables[name],
          setVariable: (name: string, value: any) => {
            context.variables[name] = value
          },
          callService: async (serviceName: string, params: any) => {
            return await this.apiClient.callService(serviceName, params)
          }
        }
      })
      
      return {
        success: true,
        message: '脚本执行成功',
        data: result,
        variables: context.variables
      }
    } catch (error) {
      return {
        success: false,
        message: `脚本执行失败: ${error.message}`
      }
    }
  }
  
  private executeLinkAction(action: ActionConfig, context: ActionContext): ActionResult {
    const url = this.interpolateUrl(action.url, context)
    
    if (action.target === 'popup') {
      window.open(url, '_blank', 'width=800,height=600,scrollbars=yes,resizable=yes')
    } else {
      window.open(url, action.target)
    }
    
    return {
      success: true,
      message: '链接已打开'
    }
  }
}
```

#### 10.2.2 动作权限控制
```typescript
class ActionPermissionManager {
  private roleManager: RoleManager
  private conditionEvaluator: ConditionEvaluator
  
  async checkActionPermission(
    action: ActionConfig,
    user: UserInfo,
    context: ActionContext
  ): Promise<PermissionCheckResult> {
    const results: PermissionCheck[] = []
    
    // 检查角色权限
    if (action.allowedRoles && action.allowedRoles.length > 0) {
      const hasRole = await this.roleManager.userHasAnyRole(user.id, action.allowedRoles)
      results.push({
        type: 'role',
        passed: hasRole,
        message: hasRole ? '角色权限检查通过' : '用户没有执行此动作的角色权限'
      })
    }
    
    // 检查显示条件
    if (action.displayCondition) {
      try {
        const conditionMet = await this.conditionEvaluator.evaluate(
          action.displayCondition,
          context
        )
        results.push({
          type: 'condition',
          passed: conditionMet,
          message: conditionMet ? '显示条件满足' : '不满足显示条件'
        })
      } catch (error) {
        results.push({
          type: 'condition',
          passed: false,
          message: `条件评估失败: ${error.message}`
        })
      }
    }
    
    // 检查业务规则
    const businessRuleResult = await this.checkBusinessRules(action, user, context)
    results.push(...businessRuleResult)
    
    const allPassed = results.every(r => r.passed)
    
    return {
      allowed: allPassed,
      checks: results,
      message: allPassed ? '权限检查通过' : '权限检查失败'
    }
  }
  
  private async checkBusinessRules(
    action: ActionConfig,
    user: UserInfo,
    context: ActionContext
  ): Promise<PermissionCheck[]> {
    const checks: PermissionCheck[] = []
    
    // 检查任务分配
    if (context.taskId) {
      const task = await this.getTask(context.taskId)
      if (task.assignee && task.assignee !== user.id) {
        checks.push({
          type: 'assignment',
          passed: false,
          message: '任务未分配给当前用户'
        })
      }
    }
    
    // 检查流程状态
    const processInstance = await this.getProcessInstance(context.processInstanceId)
    if (processInstance.suspended) {
      checks.push({
        type: 'process_state',
        passed: false,
        message: '流程已暂停，无法执行动作'
      })
    }
    
    return checks
  }
}
```

继续完善开发人员工作站的详细需求规范，包括错误处理、性能优化和部署配置等方面。
## 11. 错误处理和验证系统

### 11.1 全局错误处理
#### 11.1.1 错误分类和处理策略
```typescript
enum ErrorType {
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  NETWORK_ERROR = 'NETWORK_ERROR',
  PERMISSION_ERROR = 'PERMISSION_ERROR',
  BUSINESS_ERROR = 'BUSINESS_ERROR',
  SYSTEM_ERROR = 'SYSTEM_ERROR'
}

interface ErrorHandler {
  type: ErrorType
  handler: (error: Error, context?: any) => ErrorResponse
  retry?: boolean
  maxRetries?: number
}

class GlobalErrorManager {
  private errorHandlers: Map<ErrorType, ErrorHandler> = new Map()
  private errorLog: ErrorLogEntry[] = []
  
  constructor() {
    this.setupDefaultHandlers()
  }
  
  private setupDefaultHandlers(): void {
    // 验证错误处理
    this.registerHandler({
      type: ErrorType.VALIDATION_ERROR,
      handler: (error: ValidationError) => ({
        title: '数据验证失败',
        message: error.message,
        details: error.validationErrors,
        actions: ['修正数据', '取消操作']
      })
    })
    
    // 网络错误处理
    this.registerHandler({
      type: ErrorType.NETWORK_ERROR,
      handler: (error: NetworkError) => ({
        title: '网络连接失败',
        message: '请检查网络连接后重试',
        actions: ['重试', '离线模式']
      }),
      retry: true,
      maxRetries: 3
    })
    
    // 权限错误处理
    this.registerHandler({
      type: ErrorType.PERMISSION_ERROR,
      handler: (error: PermissionError) => ({
        title: '权限不足',
        message: '您没有执行此操作的权限',
        actions: ['申请权限', '联系管理员']
      })
    })
    
    // 业务错误处理
    this.registerHandler({
      type: ErrorType.BUSINESS_ERROR,
      handler: (error: BusinessError) => ({
        title: '业务规则验证失败',
        message: error.message,
        details: error.businessRules,
        actions: ['修正数据', '查看帮助']
      })
    })
  }
  
  async handleError(error: Error, context?: any): Promise<void> {
    const errorType = this.classifyError(error)
    const handler = this.errorHandlers.get(errorType)
    
    if (handler) {
      const response = handler.handler(error, context)
      
      // 记录错误日志
      this.logError({
        type: errorType,
        error,
        context,
        timestamp: new Date(),
        handled: true
      })
      
      // 显示错误信息
      await this.showErrorDialog(response)
      
      // 自动重试逻辑
      if (handler.retry && context?.retryCount < (handler.maxRetries || 1)) {
        setTimeout(() => {
          context.retryFunction?.()
        }, 2000)
      }
    } else {
      // 未处理的错误
      this.logError({
        type: ErrorType.SYSTEM_ERROR,
        error,
        context,
        timestamp: new Date(),
        handled: false
      })
      
      this.showGenericError(error)
    }
  }
}
```

#### 11.1.2 用户友好的错误提示
```vue
<template>
  <el-dialog
    v-model="errorDialogVisible"
    :title="errorInfo.title"
    width="500px"
    :close-on-click-modal="false"
  >
    <div class="error-content">
      <div class="error-icon">
        <el-icon size="48" color="#F56C6C">
          <WarningFilled />
        </el-icon>
      </div>
      
      <div class="error-message">
        <p class="primary-message">{{ errorInfo.message }}</p>
        
        <el-collapse v-if="errorInfo.details" accordion>
          <el-collapse-item title="查看详细信息" name="details">
            <div class="error-details">
              <pre>{{ formatErrorDetails(errorInfo.details) }}</pre>
            </div>
          </el-collapse-item>
        </el-collapse>
        
        <div v-if="errorInfo.suggestions" class="error-suggestions">
          <h4>建议解决方案：</h4>
          <ul>
            <li v-for="suggestion in errorInfo.suggestions" :key="suggestion">
              {{ suggestion }}
            </li>
          </ul>
        </div>
      </div>
    </div>
    
    <template #footer>
      <div class="error-actions">
        <el-button
          v-for="action in errorInfo.actions"
          :key="action.code"
          :type="action.type"
          @click="handleErrorAction(action)"
        >
          {{ action.label }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>
```

### 11.2 实时验证系统
#### 11.2.1 表单字段验证
```typescript
interface FieldValidator {
  field: string
  rules: ValidationRule[]
  dependencies?: string[]
  async?: boolean
}

class RealTimeValidator {
  private validators: Map<string, FieldValidator> = new Map()
  private validationCache: Map<string, ValidationResult> = new Map()
  
  registerValidator(validator: FieldValidator): void {
    this.validators.set(validator.field, validator)
  }
  
  async validateField(field: string, value: any, formData: any): Promise<ValidationResult> {
    const validator = this.validators.get(field)
    if (!validator) {
      return { valid: true }
    }
    
    // 检查缓存
    const cacheKey = `${field}:${JSON.stringify(value)}`
    if (this.validationCache.has(cacheKey)) {
      return this.validationCache.get(cacheKey)!
    }
    
    const errors: string[] = []
    const warnings: string[] = []
    
    for (const rule of validator.rules) {
      try {
        const result = await this.executeValidationRule(rule, value, formData)
        if (!result.valid) {
          if (result.severity === 'error') {
            errors.push(result.message)
          } else {
            warnings.push(result.message)
          }
        }
      } catch (error) {
        errors.push(`验证规则执行失败: ${error.message}`)
      }
    }
    
    const result: ValidationResult = {
      valid: errors.length === 0,
      errors,
      warnings
    }
    
    // 缓存结果
    this.validationCache.set(cacheKey, result)
    
    return result
  }
  
  private async executeValidationRule(
    rule: ValidationRule,
    value: any,
    formData: any
  ): Promise<RuleResult> {
    switch (rule.type) {
      case 'required':
        return {
          valid: value !== null && value !== undefined && value !== '',
          message: rule.message || '此字段为必填项',
          severity: 'error'
        }
        
      case 'length':
        const length = String(value || '').length
        return {
          valid: length >= (rule.min || 0) && length <= (rule.max || Infinity),
          message: rule.message || `长度必须在 ${rule.min} 到 ${rule.max} 之间`,
          severity: 'error'
        }
        
      case 'pattern':
        const regex = new RegExp(rule.pattern)
        return {
          valid: regex.test(String(value || '')),
          message: rule.message || '格式不正确',
          severity: 'error'
        }
        
      case 'custom':
        return await rule.validator(value, formData)
        
      case 'async':
        return await this.executeAsyncValidation(rule, value, formData)
        
      default:
        return { valid: true, severity: 'info' }
    }
  }
}
```

#### 11.2.2 业务规则验证
```typescript
interface BusinessRule {
  id: string
  name: string
  description: string
  condition: string
  action: 'block' | 'warn' | 'log'
  message: string
  priority: number
}

class BusinessRuleEngine {
  private rules: Map<string, BusinessRule> = new Map()
  private ruleEvaluator: RuleEvaluator
  
  constructor() {
    this.ruleEvaluator = new RuleEvaluator()
    this.loadBusinessRules()
  }
  
  async validateBusinessRules(
    context: string,
    data: any
  ): Promise<BusinessRuleResult[]> {
    const applicableRules = this.getApplicableRules(context)
    const results: BusinessRuleResult[] = []
    
    for (const rule of applicableRules) {
      try {
        const satisfied = await this.ruleEvaluator.evaluate(rule.condition, data)
        
        if (!satisfied) {
          results.push({
            ruleId: rule.id,
            ruleName: rule.name,
            action: rule.action,
            message: rule.message,
            priority: rule.priority,
            satisfied: false
          })
        }
      } catch (error) {
        results.push({
          ruleId: rule.id,
          ruleName: rule.name,
          action: 'block',
          message: `规则评估失败: ${error.message}`,
          priority: 999,
          satisfied: false,
          error: true
        })
      }
    }
    
    return results.sort((a, b) => b.priority - a.priority)
  }
  
  private loadBusinessRules(): void {
    // 功能单元命名规则
    this.addRule({
      id: 'function_unit_name_unique',
      name: '功能单元名称唯一性',
      description: '功能单元名称在系统中必须唯一',
      condition: 'isUniqueInSystem(name, "function_units")',
      action: 'block',
      message: '功能单元名称已存在，请使用其他名称',
      priority: 100
    })
    
    // 表结构规则
    this.addRule({
      id: 'table_primary_key_required',
      name: '主表必须有主键',
      description: '主表必须定义至少一个主键字段',
      condition: 'hasPrimaryKey(columns) && tableType === "main"',
      action: 'block',
      message: '主表必须定义主键字段',
      priority: 90
    })
    
    // 流程设计规则
    this.addRule({
      id: 'workflow_start_end_required',
      name: '流程必须有开始和结束节点',
      description: '每个流程必须包含开始事件和结束事件',
      condition: 'hasStartEvent(nodes) && hasEndEvent(nodes)',
      action: 'block',
      message: '流程必须包含开始事件和结束事件',
      priority: 95
    })
  }
}
```

## 12. 性能优化和缓存策略

### 12.1 前端性能优化
#### 12.1.1 组件懒加载和代码分割
```typescript
// 路由懒加载配置
const routes = [
  {
    path: '/function-units',
    component: () => import('@/views/FunctionUnits.vue'),
    meta: { preload: true }
  },
  {
    path: '/workflow-designer',
    component: () => import('@/views/WorkflowDesigner.vue'),
    meta: { 
      preload: false,
      chunkName: 'workflow-designer'
    }
  },
  {
    path: '/table-designer',
    component: () => import('@/views/TableDesigner.vue'),
    meta: { 
      preload: false,
      chunkName: 'table-designer'
    }
  }
]

// 组件预加载管理器
class ComponentPreloader {
  private preloadedComponents: Set<string> = new Set()
  
  async preloadComponent(componentPath: string): Promise<void> {
    if (this.preloadedComponents.has(componentPath)) {
      return
    }
    
    try {
      await import(componentPath)
      this.preloadedComponents.add(componentPath)
    } catch (error) {
      console.warn(`Failed to preload component: ${componentPath}`, error)
    }
  }
  
  preloadCriticalComponents(): void {
    // 预加载关键组件
    const criticalComponents = [
      '@/components/IconSelector.vue',
      '@/components/FormDesigner.vue',
      '@/components/TableDesigner.vue'
    ]
    
    criticalComponents.forEach(component => {
      this.preloadComponent(component)
    })
  }
}
```

#### 12.1.2 虚拟滚动和分页优化
```vue
<template>
  <div class="virtual-list-container">
    <el-virtual-list
      :data="listData"
      :height="containerHeight"
      :item-size="itemHeight"
      :buffer="bufferSize"
      @scroll="handleScroll"
    >
      <template #default="{ item, index }">
        <div class="list-item" :key="item.id">
          <function-unit-card 
            :unit="item"
            :index="index"
            @edit="handleEdit"
            @delete="handleDelete"
          />
        </div>
      </template>
    </el-virtual-list>
    
    <!-- 无限滚动加载 -->
    <div v-if="hasMore && !loading" class="load-more-trigger" ref="loadTrigger">
      <el-button @click="loadMore" text>加载更多</el-button>
    </div>
    
    <div v-if="loading" class="loading-indicator">
      <el-skeleton :rows="3" animated />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  pageSize?: number
  bufferSize?: number
  itemHeight?: number
}

const props = withDefaults(defineProps<Props>(), {
  pageSize: 50,
  bufferSize: 10,
  itemHeight: 120
})

const listData = ref<FunctionUnit[]>([])
const loading = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)

// 使用 Intersection Observer 实现无限滚动
const { stop } = useIntersectionObserver(
  loadTrigger,
  ([{ isIntersecting }]) => {
    if (isIntersecting && hasMore.value && !loading.value) {
      loadMore()
    }
  },
  { threshold: 0.1 }
)

const loadMore = async () => {
  if (loading.value || !hasMore.value) return
  
  loading.value = true
  try {
    const response = await functionUnitApi.getList({
      page: currentPage.value,
      pageSize: props.pageSize
    })
    
    listData.value.push(...response.data)
    currentPage.value++
    hasMore.value = response.hasMore
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}
</script>
```

### 12.2 缓存策略
#### 12.2.1 多级缓存架构
```typescript
interface CacheConfig {
  ttl: number // 生存时间（秒）
  maxSize: number // 最大缓存条目数
  strategy: 'LRU' | 'LFU' | 'FIFO'
}

class MultiLevelCache {
  private memoryCache: Map<string, CacheEntry> = new Map()
  private localStorageCache: LocalStorageCache
  private sessionCache: SessionStorageCache
  
  constructor(private config: CacheConfig) {
    this.localStorageCache = new LocalStorageCache('app_cache')
    this.sessionCache = new SessionStorageCache('session_cache')
  }
  
  async get<T>(key: string): Promise<T | null> {
    // 1. 检查内存缓存
    const memoryEntry = this.memoryCache.get(key)
    if (memoryEntry && !this.isExpired(memoryEntry)) {
      return memoryEntry.value as T
    }
    
    // 2. 检查会话缓存
    const sessionEntry = await this.sessionCache.get(key)
    if (sessionEntry && !this.isExpired(sessionEntry)) {
      // 提升到内存缓存
      this.memoryCache.set(key, sessionEntry)
      return sessionEntry.value as T
    }
    
    // 3. 检查本地存储缓存
    const localEntry = await this.localStorageCache.get(key)
    if (localEntry && !this.isExpired(localEntry)) {
      // 提升到上级缓存
      this.sessionCache.set(key, localEntry)
      this.memoryCache.set(key, localEntry)
      return localEntry.value as T
    }
    
    return null
  }
  
  async set<T>(key: string, value: T, ttl?: number): Promise<void> {
    const entry: CacheEntry = {
      value,
      timestamp: Date.now(),
      ttl: ttl || this.config.ttl,
      accessCount: 1
    }
    
    // 存储到所有级别
    this.memoryCache.set(key, entry)
    await this.sessionCache.set(key, entry)
    await this.localStorageCache.set(key, entry)
    
    // 检查缓存大小限制
    this.evictIfNecessary()
  }
  
  private evictIfNecessary(): void {
    if (this.memoryCache.size > this.config.maxSize) {
      const entries = Array.from(this.memoryCache.entries())
      
      // 根据策略排序
      switch (this.config.strategy) {
        case 'LRU':
          entries.sort((a, b) => a[1].timestamp - b[1].timestamp)
          break
        case 'LFU':
          entries.sort((a, b) => a[1].accessCount - b[1].accessCount)
          break
        case 'FIFO':
          // 已经是插入顺序
          break
      }
      
      // 删除最旧的条目
      const toRemove = entries.slice(0, entries.length - this.config.maxSize)
      toRemove.forEach(([key]) => {
        this.memoryCache.delete(key)
      })
    }
  }
}
```

#### 12.2.2 API响应缓存
```typescript
class ApiCacheManager {
  private cache: MultiLevelCache
  private pendingRequests: Map<string, Promise<any>> = new Map()
  
  constructor() {
    this.cache = new MultiLevelCache({
      ttl: 300, // 5分钟
      maxSize: 1000,
      strategy: 'LRU'
    })
  }
  
  async cachedRequest<T>(
    url: string,
    options: RequestOptions = {},
    cacheOptions: CacheOptions = {}
  ): Promise<T> {
    const cacheKey = this.generateCacheKey(url, options)
    
    // 检查缓存
    if (!cacheOptions.skipCache) {
      const cached = await this.cache.get<T>(cacheKey)
      if (cached) {
        return cached
      }
    }
    
    // 检查是否有相同的请求正在进行
    if (this.pendingRequests.has(cacheKey)) {
      return await this.pendingRequests.get(cacheKey)
    }
    
    // 发起新请求
    const requestPromise = this.executeRequest<T>(url, options)
    this.pendingRequests.set(cacheKey, requestPromise)
    
    try {
      const result = await requestPromise
      
      // 缓存结果
      if (cacheOptions.cacheable !== false) {
        await this.cache.set(cacheKey, result, cacheOptions.ttl)
      }
      
      return result
    } finally {
      this.pendingRequests.delete(cacheKey)
    }
  }
  
  private generateCacheKey(url: string, options: RequestOptions): string {
    const keyData = {
      url,
      method: options.method || 'GET',
      params: options.params,
      data: options.data
    }
    
    return btoa(JSON.stringify(keyData))
  }
  
  invalidateCache(pattern?: string): void {
    if (pattern) {
      // 根据模式清除特定缓存
      this.cache.clear(pattern)
    } else {
      // 清除所有缓存
      this.cache.clearAll()
    }
  }
}
```

## 13. 部署和配置管理

### 13.1 环境配置
#### 13.1.1 多环境配置管理
```typescript
interface EnvironmentConfig {
  name: string
  apiBaseUrl: string
  wsUrl: string
  features: FeatureFlags
  performance: PerformanceConfig
  security: SecurityConfig
  logging: LoggingConfig
}

class ConfigManager {
  private config: EnvironmentConfig
  private readonly configMap: Map<string, EnvironmentConfig> = new Map([
    ['development', {
      name: 'development',
      apiBaseUrl: 'http://localhost:8080/api',
      wsUrl: 'ws://localhost:8080/ws',
      features: {
        debugMode: true,
        mockData: true,
        performanceMonitoring: true,
        errorReporting: false
      },
      performance: {
        cacheEnabled: false,
        virtualScrolling: false,
        lazyLoading: true
      },
      security: {
        csrfProtection: false,
        httpsOnly: false,
        sessionTimeout: 3600
      },
      logging: {
        level: 'debug',
        console: true,
        remote: false
      }
    }],
    ['testing', {
      name: 'testing',
      apiBaseUrl: 'https://test-api.hsbc.com/workflow/api',
      wsUrl: 'wss://test-api.hsbc.com/workflow/ws',
      features: {
        debugMode: true,
        mockData: false,
        performanceMonitoring: true,
        errorReporting: true
      },
      performance: {
        cacheEnabled: true,
        virtualScrolling: true,
        lazyLoading: true
      },
      security: {
        csrfProtection: true,
        httpsOnly: true,
        sessionTimeout: 1800
      },
      logging: {
        level: 'info',
        console: true,
        remote: true
      }
    }],
    ['production', {
      name: 'production',
      apiBaseUrl: 'https://api.hsbc.com/workflow/api',
      wsUrl: 'wss://api.hsbc.com/workflow/ws',
      features: {
        debugMode: false,
        mockData: false,
        performanceMonitoring: true,
        errorReporting: true
      },
      performance: {
        cacheEnabled: true,
        virtualScrolling: true,
        lazyLoading: true
      },
      security: {
        csrfProtection: true,
        httpsOnly: true,
        sessionTimeout: 900
      },
      logging: {
        level: 'warn',
        console: false,
        remote: true
      }
    }]
  ])
  
  constructor() {
    this.loadConfig()
  }
  
  private loadConfig(): void {
    const env = process.env.NODE_ENV || 'development'
    this.config = this.configMap.get(env) || this.configMap.get('development')!
    
    // 从环境变量覆盖配置
    this.overrideFromEnv()
  }
  
  private overrideFromEnv(): void {
    if (process.env.VITE_API_BASE_URL) {
      this.config.apiBaseUrl = process.env.VITE_API_BASE_URL
    }
    
    if (process.env.VITE_WS_URL) {
      this.config.wsUrl = process.env.VITE_WS_URL
    }
    
    if (process.env.VITE_SESSION_TIMEOUT) {
      this.config.security.sessionTimeout = parseInt(process.env.VITE_SESSION_TIMEOUT)
    }
  }
  
  getConfig(): EnvironmentConfig {
    return this.config
  }
  
  isFeatureEnabled(feature: keyof FeatureFlags): boolean {
    return this.config.features[feature] || false
  }
}
```

#### 13.1.2 构建配置优化
```javascript
// vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    // 自动导入
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: true
    }),
    // 组件自动导入
    Components({
      resolvers: [ElementPlusResolver()],
      dts: true
    })
  ],
  
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@components': resolve(__dirname, 'src/components'),
      '@views': resolve(__dirname, 'src/views'),
      '@utils': resolve(__dirname, 'src/utils'),
      '@api': resolve(__dirname, 'src/api')
    }
  },
  
  build: {
    target: 'es2015',
    outDir: 'dist',
    assetsDir: 'assets',
    
    // 代码分割配置
    rollupOptions: {
      output: {
        manualChunks: {
          // 第三方库分离
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-ui': ['element-plus'],
          'vendor-utils': ['axios', 'dayjs', 'lodash-es'],
          
          // 业务模块分离
          'workflow-designer': [
            './src/views/WorkflowDesigner.vue',
            './src/components/workflow/BpmnModeler.vue'
          ],
          'table-designer': [
            './src/views/TableDesigner.vue',
            './src/components/table/HandsontableEditor.vue'
          ],
          'form-designer': [
            './src/views/FormDesigner.vue',
            './src/components/form/FormCreator.vue'
          ]
        }
      }
    },
    
    // 压缩配置
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    }
  },
  
  // 开发服务器配置
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})
```

### 13.2 监控和日志
#### 13.2.1 性能监控
```typescript
class PerformanceMonitor {
  private metrics: PerformanceMetric[] = []
  private observers: PerformanceObserver[] = []
  
  constructor() {
    this.setupObservers()
  }
  
  private setupObservers(): void {
    // 页面加载性能监控
    if ('PerformanceObserver' in window) {
      const navigationObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          this.recordMetric({
            name: 'page_load',
            value: entry.loadEventEnd - entry.loadEventStart,
            timestamp: Date.now(),
            tags: {
              page: window.location.pathname
            }
          })
        }
      })
      
      navigationObserver.observe({ entryTypes: ['navigation'] })
      this.observers.push(navigationObserver)
      
      // 资源加载监控
      const resourceObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (entry.duration > 1000) { // 只记录超过1秒的资源
            this.recordMetric({
              name: 'resource_load',
              value: entry.duration,
              timestamp: Date.now(),
              tags: {
                resource: entry.name,
                type: entry.initiatorType
              }
            })
          }
        }
      })
      
      resourceObserver.observe({ entryTypes: ['resource'] })
      this.observers.push(resourceObserver)
    }
  }
  
  // 自定义性能标记
  markStart(name: string): void {
    performance.mark(`${name}_start`)
  }
  
  markEnd(name: string): void {
    performance.mark(`${name}_end`)
    performance.measure(name, `${name}_start`, `${name}_end`)
    
    const measure = performance.getEntriesByName(name, 'measure')[0]
    this.recordMetric({
      name: 'custom_timing',
      value: measure.duration,
      timestamp: Date.now(),
      tags: {
        operation: name
      }
    })
  }
  
  private recordMetric(metric: PerformanceMetric): void {
    this.metrics.push(metric)
    
    // 发送到监控服务
    this.sendToMonitoringService(metric)
    
    // 清理旧数据
    if (this.metrics.length > 1000) {
      this.metrics = this.metrics.slice(-500)
    }
  }
  
  private async sendToMonitoringService(metric: PerformanceMetric): Promise<void> {
    try {
      await fetch('/api/monitoring/metrics', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(metric)
      })
    } catch (error) {
      console.warn('Failed to send metric to monitoring service:', error)
    }
  }
}
```

#### 13.2.2 错误监控和上报
```typescript
class ErrorReporter {
  private errorQueue: ErrorReport[] = []
  private isOnline: boolean = navigator.onLine
  
  constructor() {
    this.setupGlobalErrorHandlers()
    this.setupNetworkStatusListener()
  }
  
  private setupGlobalErrorHandlers(): void {
    // JavaScript错误捕获
    window.addEventListener('error', (event) => {
      this.reportError({
        type: 'javascript',
        message: event.message,
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno,
        stack: event.error?.stack,
        timestamp: Date.now(),
        url: window.location.href,
        userAgent: navigator.userAgent
      })
    })
    
    // Promise拒绝捕获
    window.addEventListener('unhandledrejection', (event) => {
      this.reportError({
        type: 'promise_rejection',
        message: event.reason?.message || 'Unhandled Promise Rejection',
        stack: event.reason?.stack,
        timestamp: Date.now(),
        url: window.location.href,
        userAgent: navigator.userAgent
      })
    })
    
    // Vue错误捕获
    app.config.errorHandler = (error, instance, info) => {
      this.reportError({
        type: 'vue',
        message: error.message,
        stack: error.stack,
        componentInfo: info,
        timestamp: Date.now(),
        url: window.location.href,
        userAgent: navigator.userAgent
      })
    }
  }
  
  private setupNetworkStatusListener(): void {
    window.addEventListener('online', () => {
      this.isOnline = true
      this.flushErrorQueue()
    })
    
    window.addEventListener('offline', () => {
      this.isOnline = false
    })
  }
  
  reportError(error: ErrorReport): void {
    // 添加用户上下文信息
    error.userId = getCurrentUser()?.id
    error.sessionId = getSessionId()
    
    if (this.isOnline) {
      this.sendErrorReport(error)
    } else {
      this.errorQueue.push(error)
    }
  }
  
  private async sendErrorReport(error: ErrorReport): Promise<void> {
    try {
      await fetch('/api/monitoring/errors', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(error)
      })
    } catch (err) {
      // 发送失败，加入队列
      this.errorQueue.push(error)
    }
  }
  
  private async flushErrorQueue(): Promise<void> {
    while (this.errorQueue.length > 0 && this.isOnline) {
      const error = this.errorQueue.shift()!
      await this.sendErrorReport(error)
    }
  }
}
```

这样我们就完成了开发人员工作站的详细需求规范。现在让我继续细化管理员中心的需求文档。