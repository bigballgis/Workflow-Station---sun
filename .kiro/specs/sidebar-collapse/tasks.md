# Implementation Plan: Sidebar Collapse

## Overview

本实现计划将为开发者工作站的 MainLayout 组件添加侧边栏折叠/展开功能，通过修改现有的 Vue 组件实现。

## Tasks

- [x] 1. 添加侧边栏折叠状态管理
  - [x] 1.1 在 MainLayout.vue 中添加 isCollapsed 状态和 sidebarWidth 计算属性
    - 添加 `isCollapsed` ref 状态，默认值为 false
    - 添加 `sidebarWidth` computed 属性，根据 isCollapsed 返回 '64px' 或 '240px'
    - _Requirements: 1.3, 2.3_

  - [x] 1.2 实现 toggleSidebar 方法
    - 创建切换 isCollapsed 状态的方法
    - 在状态变化时保存到 localStorage
    - _Requirements: 1.1, 2.1, 4.1_

  - [x] 1.3 实现 initSidebarState 方法并在 onMounted 中调用
    - 从 localStorage 读取保存的状态
    - 处理 localStorage 不可用或值损坏的情况
    - _Requirements: 4.2, 4.3_

- [x] 2. 更新模板结构
  - [x] 2.1 修改 el-aside 组件绑定动态宽度
    - 将 `width="240px"` 改为 `:width="sidebarWidth"`
    - _Requirements: 1.3, 2.3_

  - [x] 2.2 为 el-menu 添加 collapse 属性
    - 绑定 `:collapse="isCollapsed"`
    - Element Plus 会自动处理菜单项折叠和 tooltip 显示
    - _Requirements: 1.2, 2.2, 6.1, 6.2, 6.3_

  - [x] 2.3 添加折叠按钮组件
    - 在 el-menu 下方添加折叠按钮 div
    - 导入并使用 DArrowLeft 和 DArrowRight 图标
    - 根据 isCollapsed 状态显示对应图标
    - 绑定 click 事件到 toggleSidebar 方法
    - _Requirements: 3.1, 3.2, 3.3_

- [x] 3. 添加 CSS 样式
  - [x] 3.1 为 sidebar 添加过渡动画和 flex 布局
    - 添加 `transition: width 0.3s ease`
    - 设置 `display: flex; flex-direction: column`
    - 确保 el-menu 使用 `flex: 1` 填充空间
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 3.2 添加折叠按钮样式
    - 设置按钮高度、居中对齐
    - 添加顶部边框分隔线
    - 添加 hover 效果
    - _Requirements: 3.4_

- [x] 4. 添加国际化支持
  - [x] 4.1 在 i18n 文件中添加折叠按钮的 aria-label 翻译（可选）
    - 添加 zh-CN、zh-TW、en 的翻译
    - _Requirements: 3.1_

- [x] 5. Checkpoint - 功能验证
  - 确保侧边栏可以正常折叠和展开
  - 确保状态在页面刷新后保持
  - 确保动画平滑
  - 如有问题请询问用户

- [x] 6. 编写测试
  - [x] 6.1 编写 toggleSidebar 状态切换的属性测试
    - **Property 1: Toggle State Consistency**
    - **Validates: Requirements 1.1, 2.1**

  - [x] 6.2 编写 localStorage 持久化的属性测试
    - **Property 2: State Persistence Round-Trip**
    - **Validates: Requirements 4.1, 4.2**

## Notes

- 所有任务均为必需，包括测试任务
- Element Plus 的 el-menu 组件在 collapse 模式下会自动显示 tooltip，无需额外实现
- 主要修改集中在 `frontend/developer-workstation/src/layouts/MainLayout.vue` 文件
