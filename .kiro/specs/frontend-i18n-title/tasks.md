# Implementation Plan: Frontend I18n Title

## Overview

为三个前端应用（Admin Center、Developer Workstation、User Portal）实现系统名称和浏览器标签页标题的国际化支持。

## Tasks

- [x] 1. 更新 Admin Center 的 i18n 配置
  - [x] 1.1 在 zh-CN.ts 中添加 app 命名空间
    - 添加 `app.name: '管理中心'`
    - 添加 `app.title: '工作流平台'`
    - _Requirements: 1.1, 3.1, 3.2_
  - [x] 1.2 在 en.ts 中添加 app 命名空间
    - 添加 `app.name: 'Admin Center'`
    - 添加 `app.title: 'Workflow Platform'`
    - _Requirements: 1.2, 3.1, 3.2_
  - [x] 1.3 更新 router/index.ts 使用 i18n 设置标题
    - 导入 i18n 实例
    - 修改 beforeEach 中的 document.title 设置逻辑
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 2. 更新 Developer Workstation 的 i18n 配置
  - [x] 2.1 在 zh-CN.ts 中添加 app 命名空间
    - 添加 `app.name: '开发工作站'`
    - 添加 `app.title: '工作流平台'`
    - _Requirements: 1.1, 3.1, 3.2_
  - [x] 2.2 在 en.ts 中添加 app 命名空间
    - 添加 `app.name: 'Developer Workstation'`
    - 添加 `app.title: 'Workflow Platform'`
    - _Requirements: 1.2, 3.1, 3.2_
  - [x] 2.3 更新 router/index.ts 使用 i18n 设置标题
    - 导入 i18n 实例
    - 修改 beforeEach 中的 document.title 设置逻辑
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 3. 更新 User Portal 的 i18n 配置
  - [x] 3.1 在 zh-CN.ts 中添加 app 命名空间
    - 添加 `app.name: '用户门户'`
    - 添加 `app.title: '工作流平台'`
    - _Requirements: 1.1, 3.1, 3.2_
  - [x] 3.2 在 en.ts 中添加 app 命名空间
    - 添加 `app.name: 'User Portal'`
    - 添加 `app.title: 'Workflow Platform'`
    - _Requirements: 1.2, 3.1, 3.2_
  - [x] 3.3 更新 router/index.ts 使用 i18n 设置标题
    - 导入 i18n 实例
    - 修改 beforeEach 中的 document.title 设置逻辑
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 4. 更新登录页面使用 i18n 系统名称
  - [x] 4.1 确认 Admin Center 登录页面使用 t('app.name')
    - _Requirements: 1.1, 1.2, 1.3_
  - [x] 4.2 确认 Developer Workstation 登录页面使用 t('app.name')
    - _Requirements: 1.1, 1.2, 1.3_
  - [x] 4.3 确认 User Portal 登录页面使用 t('app.name')
    - _Requirements: 1.1, 1.2, 1.3_

- [x] 5. Checkpoint - 验证功能
  - 手动测试各应用的标题国际化
  - 切换语言验证标题更新
  - 确保所有测试通过，如有问题请告知

## Notes

- 任务按应用分组，便于逐个完成和测试
- 每个应用的修改相对独立，可以分别验证
- 登录页面已经使用了 `t('login.title')`，需要确认是否需要改为 `t('app.name')`
