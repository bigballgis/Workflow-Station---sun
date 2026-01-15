# Requirements Document

## Introduction

本功能旨在为工作流平台的三个前端应用（Admin Center、Developer Workstation、User Portal）添加系统名称和浏览器标签页标题的国际化支持。目前这些标题是硬编码在 HTML 文件中的中文，需要改为根据用户语言设置动态切换。

## Glossary

- **System_Title**: 显示在页面头部或登录页面的系统名称
- **Page_Title**: 浏览器标签页显示的标题
- **I18n_Module**: Vue I18n 国际化模块
- **Locale**: 语言区域设置（如 zh-CN、en）

## Requirements

### Requirement 1: 系统名称国际化

**User Story:** As a user, I want to see the system name in my preferred language, so that I can better understand the application context.

#### Acceptance Criteria

1. WHEN the user's locale is set to Chinese (zh-CN), THE System_Title SHALL display "管理中心"、"开发工作站"、"工作流平台" respectively for each application
2. WHEN the user's locale is set to English (en), THE System_Title SHALL display "Admin Center"、"Developer Workstation"、"Workflow Platform" respectively
3. WHEN the user changes the language setting, THE System_Title SHALL update immediately without page refresh

### Requirement 2: 浏览器标签页标题国际化

**User Story:** As a user, I want the browser tab title to match my language preference, so that I can easily identify the application in my browser tabs.

#### Acceptance Criteria

1. WHEN the application loads, THE Page_Title SHALL be set according to the current locale
2. WHEN the user's locale is set to Chinese (zh-CN), THE Page_Title SHALL display:
   - Admin Center: "管理中心 - 工作流平台"
   - Developer Workstation: "开发工作站 - 工作流平台"
   - User Portal: "用户门户 - 工作流平台"
3. WHEN the user's locale is set to English (en), THE Page_Title SHALL display:
   - Admin Center: "Admin Center - Workflow Platform"
   - Developer Workstation: "Developer Workstation - Workflow Platform"
   - User Portal: "User Portal - Workflow Platform"
4. WHEN the user changes the language setting, THE Page_Title SHALL update immediately

### Requirement 3: I18n 配置扩展

**User Story:** As a developer, I want the i18n configuration to include system-level titles, so that I can easily maintain and extend translations.

#### Acceptance Criteria

1. THE I18n_Module SHALL include a new "app" namespace for application-level translations
2. THE "app" namespace SHALL contain "name" and "title" keys for system name and page title
3. WHEN adding new languages in the future, THE I18n_Module SHALL support the same structure for consistency

### Requirement 4: 初始加载标题处理

**User Story:** As a user, I want to see a proper title even before the Vue app fully loads, so that I have a good first impression.

#### Acceptance Criteria

1. THE index.html SHALL contain a default title that matches the primary locale (Chinese)
2. WHEN the Vue application mounts, THE Page_Title SHALL be updated to match the user's locale preference
3. IF the user's locale preference is stored in localStorage, THE Page_Title SHALL reflect that preference after app initialization
