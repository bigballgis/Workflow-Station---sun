# Implementation Plan: Function Unit Icon Management

## Overview

本实现计划将功能单元图标上传和修改功能分解为可执行的编码任务。主要工作集中在前端，包括创建图标选择器组件、修改功能单元列表和编辑页面，以及添加相应的属性测试。

## Tasks

- [x] 1. 创建 IconPreview 组件
  - 创建 `frontend/developer-workstation/src/components/icon/IconPreview.vue`
  - 支持 small/medium/large 三种尺寸
  - 支持显示图标或默认占位符
  - 支持点击事件
  - _Requirements: 4.1, 4.2, 4.3_

- [x] 2. 创建 IconSelector 组件
  - [x] 2.1 创建基础图标选择器组件
    - 创建 `frontend/developer-workstation/src/components/icon/IconSelector.vue`
    - 实现图标网格展示
    - 实现图标选择和取消选择
    - 支持 v-model 双向绑定
    - _Requirements: 1.2, 1.3, 2.2_
  
  - [x] 2.2 添加搜索和筛选功能
    - 添加搜索输入框
    - 添加分类下拉筛选
    - 实现实时过滤
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  
  - [x] 2.3 集成图标上传功能
    - 添加上传按钮
    - 集成现有的图标上传对话框
    - 上传成功后自动选中新图标
    - _Requirements: 3.1, 3.2, 3.3, 3.5_

- [x] 3. 修改功能单元列表页面
  - [x] 3.1 在列表中显示图标
    - 修改 `FunctionUnitList.vue` 表格，添加图标列
    - 使用 IconPreview 组件显示图标
    - _Requirements: 4.1, 4.2_
  
  - [x] 3.2 在创建对话框中添加图标选择
    - 扩展创建表单，添加图标选择字段
    - 集成 IconSelector 组件
    - 提交时传递 iconId
    - _Requirements: 1.1, 1.4, 1.5_

- [x] 4. 修改功能单元编辑页面
  - [x] 4.1 在页面头部显示当前图标
    - 修改 `FunctionUnitEdit.vue` 头部区域
    - 使用 IconPreview 组件显示当前图标
    - _Requirements: 2.1, 4.3_
  
  - [x] 4.2 添加图标修改功能
    - 点击图标打开 IconSelector
    - 选择新图标后调用更新API
    - 支持清除图标
    - _Requirements: 2.2, 2.3, 2.4_

- [x] 5. 添加国际化支持
  - 在 `zh-CN.ts`、`zh-TW.ts`、`en.ts` 中添加相关翻译
  - _Requirements: All_

- [x] 6. Checkpoint - 确保功能正常工作
  - 确保所有功能可以正常使用，如有问题请询问用户

- [x] 7. 添加属性测试
  - [x] 7.1 编写图标ID持久化往返测试
    - **Property 1: Icon ID Persistence Round-Trip**
    - **Validates: Requirements 1.4, 2.3**
  
  - [x] 7.2 编写可选图标处理测试
    - **Property 2: Optional Icon Handling**
    - **Validates: Requirements 1.5, 2.4**
  
  - [x] 7.3 编写搜索过滤正确性测试
    - **Property 5: Search Filter Correctness**
    - **Validates: Requirements 5.2**
  
  - [x] 7.4 编写分类过滤正确性测试
    - **Property 6: Category Filter Correctness**
    - **Validates: Requirements 5.3**

- [x] 8. Final Checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户

## Notes
- 后端已支持图标关联，本实现主要集中在前端
- 图标上传功能已在 IconLibrary.vue 中实现，可复用其逻辑
- 属性测试使用 jqwik 框架
