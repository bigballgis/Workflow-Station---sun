# Relation Tables 功能需求文档

## 介绍

Relation Tables 是一个公共的数据表管理功能模块，允许管理员在 Admin Center 中可视化地创建和管理数据库表结构及表数据。Admin Center 仅限 admin 角色访问，无需额外权限管理。该模块支持版本管理和部署机制，表结构修改需经过部署后才能生效。在 Developer Workstation 中，开发者可通过 Manage Table Bindings 功能绑定 Relation Tables；绑定完成后，系统会在 subtable 的标签页旁边自动生成一个 view 页面，该 view 页面支持添加或删除绑定的 Relation Table 的字段。开发者还可以自定义扩展一个 Lookup 字段，并在该 Lookup 字段中配置对应 Relation Table 的 view 信息；点击 Lookup 字段后，可跳转到对应的 view 页面中设计该 view 应展示的字段。Relation Tables 在 User Portal 中仅供业务用户查看数据，不支持直接修改、新增或删除操作；User Portal 中 Relation Table 的可见性通过 Admin Center 的 Business Role 配置控制，权限体系与现有 Function Unit 保持一致。

## 术语表

- **Admin_Center**: 管理中心，仅限 admin 角色访问，管理员管理系统的主要界面，无需额外权限管理
- **User_Portal**: 用户门户，业务用户访问系统的主要界面，Relation Table 的可见性通过 Admin Center 的 Business Role 配置控制，与 Function Unit 权限配置一致
- **Developer_Workstation**: 开发者工作站，用于设计 Function Unit、Form、Table 等
- **Relation_Table_Module**: Relation Tables 功能模块，包含表结构管理和表数据管理
- **Table_Structure_Manager**: 表结构管理器，用于创建、修改、部署表结构定义
- **Table_Data_Manager**: 表数据管理器，用于管理 Relation Table 中的业务数据
- **Table_Definition**: 表定义，包含表名、显示名、描述、字段定义等元数据
- **Field_Definition**: 字段定义，包含字段名、数据类型、长度、是否允许为空、是否主键、默认值、注释
- **Table_Version**: 表版本，每次部署生成的表结构快照
- **Deploy_Action**: 部署操作，将表结构修改应用到实际数据库的过程
- **Lookup_Component**: 查找组件，form-create 的扩展组件，用于在表单中关联 Relation Table 数据，组件内部可配置关联的 Relation Table 和 View 展示信息
- **Lookup_View_Config**: 查找视图配置，Lookup_Component 内部的视图配置项，用于定义展示关联 Relation Table 的哪些列
- **Search_Field_Config**: 搜索字段配置，Lookup_Component 的配置项，用于指定在 User Portal 中搜索时使用 Relation Table 的哪些字段（支持多字段）进行模糊匹配
- **Display_Field_Config**: 展示字段配置，Lookup_Component 的配置项，用于指定在 User Portal 中搜索结果列表中使用 Relation Table 的哪个字段来展示匹配到的数据
- **Table_Binding_Manager**: 表绑定管理器，Manage Table Bindings 功能模块，用于在 Form Designer 中绑定和管理 Relation Table
- **View_Page**: 视图页面，绑定 Relation Table 后在 subtable 标签页旁边自动生成的页面，用于配置展示绑定表的哪些字段
- **Business_Role**: 业务角色，用于控制 User Portal 中用户对 Relation Table 数据的访问权限，配置方式与 Function Unit 一致
- **Portal_Visibility_Switch**: 门户可见性开关，位于表结构列表的 Enable 列旁边，控制表数据是否在 User Portal 中显示
- **Audit_Log**: 审计日志，记录数据变更操作的日志
- **My_Requests**: 我的申请，User Portal 中用户发起的流程申请
- **To_Do**: 待办事项，User Portal 中待处理的审批任务
- **Completed**: 已完成，User Portal 中已处理完成的任务

## 需求

### 需求 1: Admin Center 菜单结构

**用户故事:** 作为管理员，我希望在 Admin Center 中看到 Relation Tables 菜单及其子菜单，以便分别管理表结构和表数据。

#### 验收标准

1. THE Admin_Center SHALL 在侧边栏菜单中显示 "Relation Tables" 一级菜单项
2. THE Relation_Table_Module SHALL 在 "Relation Tables" 菜单下提供 "Table Structure" 和 "Table Data" 两个子菜单
3. WHEN 管理员点击 "Table Structure" 子菜单 THEN Admin_Center SHALL 导航到表结构管理页面
4. WHEN 管理员点击 "Table Data" 子菜单 THEN Admin_Center SHALL 导航到表数据管理页面
5. THE Admin_Center SHALL 仅允许 admin 角色访问 Relation Tables 功能，无需额外的权限配置

### 需求 2: 表结构列表展示

**用户故事:** 作为管理员，我希望能够查看所有 Relation Table 的列表信息，以便了解当前系统中已定义的表结构。

#### 验收标准

1. WHEN 管理员访问表结构管理页面 THEN Table_Structure_Manager SHALL 以表格形式展示所有 Relation Table 的列表
2. THE Table_Structure_Manager SHALL 在列表中展示以下列：Name、Display Name、Version、Status、Enable、Portal Visibility、Updated At、Updated By、Actions
3. THE Table_Structure_Manager SHALL 将 Status 列的值限定为以下枚举值：DRAFT（草稿，表结构已修改但尚未部署）、DEPLOYED（已部署，表结构已成功部署到实际数据库）、ROLLBACK（已回滚，表结构已回滚到某个历史版本）
4. THE Table_Structure_Manager SHALL 在 Actions 列中提供 Access、Deploy、Versions、Rollback、Delete 五个操作按钮
5. WHEN 管理员点击 Access 按钮 THEN Table_Structure_Manager SHALL 打开 Business Role 访问配置对话框
6. WHEN 管理员点击 Versions 按钮 THEN Table_Structure_Manager SHALL 展示该表的所有历史版本列表
7. THE Table_Structure_Manager SHALL 支持通过 Enable 开关控制表的启用和禁用状态

### 需求 3: 可视化创建表结构

**用户故事:** 作为管理员，我希望通过可视化表单创建数据库表结构，以便无需编写 SQL 即可定义数据表。

#### 验收标准

1. WHEN 管理员点击创建表按钮 THEN Table_Structure_Manager SHALL 显示创建表的表单界面
2. THE Table_Structure_Manager SHALL 在创建表单中提供表名（Table Name）、显示名（Display Name）、表描述（Description）三个基本信息输入项
3. THE Table_Structure_Manager SHALL 提供动态添加和删除字段的功能
4. THE Field_Definition SHALL 包含以下属性：字段名（Field Name）、数据类型（Data Type，下拉选择）、长度（Length）、是否允许为空（Nullable）、是否主键（Primary Key）、默认值（Default Value）、注释（Comment）
5. THE Table_Structure_Manager SHALL 提供数据类型下拉选项，包含 VARCHAR、INTEGER、BIGINT、DECIMAL、BOOLEAN、DATE、TIMESTAMP、TEXT 类型
6. WHEN 管理员提交创建表单 THEN Table_Structure_Manager SHALL 验证表名唯一性并创建表定义记录
7. IF 表名已存在 THEN Table_Structure_Manager SHALL 拒绝创建并显示表名重复的错误提示

### 需求 4: 修改表结构

**用户故事:** 作为管理员，我希望能够修改已有的表结构定义，以便根据业务需求调整字段配置。

#### 验收标准

1. WHEN 管理员在列表中选择某个表 THEN Table_Structure_Manager SHALL 进入该表的编辑界面
2. THE Table_Structure_Manager SHALL 允许修改表的基本信息（表名、显示名、描述）
3. THE Table_Structure_Manager SHALL 允许动态添加新字段、删除已有字段
4. THE Table_Structure_Manager SHALL 允许修改已有字段的属性（数据类型、长度、是否允许为空、是否主键、默认值、注释）
5. WHEN 管理员保存修改 THEN Table_Structure_Manager SHALL 将修改保存为草稿状态，修改内容在部署前不会影响实际数据库和数据列表页面

### 需求 5: 表结构版本管理与部署

**用户故事:** 作为管理员，我希望表结构修改通过版本管理和部署机制生效，以便安全地管理表结构变更。

#### 验收标准

1. WHEN 管理员点击 Deploy 按钮 THEN Table_Structure_Manager SHALL 启动部署流程，将当前表结构修改应用到实际数据库
2. WHEN 部署成功完成 THEN Table_Structure_Manager SHALL 生成新的版本号并记录版本快照
3. WHEN 部署成功完成 THEN Table_Structure_Manager SHALL 将表结构变更（包括增减字段、修改字段类型、字段长度等）同步应用到表数据管理的数据列表页面和详情页面
4. WHEN 管理员点击 Rollback 按钮并选择某个历史版本 THEN Table_Structure_Manager SHALL 生成一个新的版本号，并将表结构内容切换为所选历史版本的表结构内容
5. WHILE 表结构处于未部署的草稿状态 THEN Table_Structure_Manager SHALL 在列表中以 DRAFT 状态标识该表
6. WHEN 部署过程中发生错误 THEN Table_Structure_Manager SHALL 回滚所有变更并显示详细的错误信息

### 需求 6: 表数据管理（Admin Center）

**用户故事:** 作为管理员，我希望在 Admin Center 中管理 Relation Table 的业务数据，以便维护公共数据。

#### 验收标准

1. WHEN 管理员访问表数据管理页面 THEN Table_Data_Manager SHALL 以列表形式展示所有已部署的 Relation Table
2. WHEN 管理员选择某个表 THEN Table_Data_Manager SHALL 根据该表已部署的最新表结构动态渲染数据列表
3. THE Table_Data_Manager SHALL 提供新增数据（Add）、修改数据（Update）、删除数据（Delete）、Active、Inactive 操作功能
4. WHEN 管理员对数据执行任何变更操作 THEN Table_Data_Manager SHALL 记录 Audit_Log，包含操作人、操作时间、操作类型、变更前后的数据内容
5. THE Table_Data_Manager SHALL 支持数据的分页展示和搜索过滤功能

### 需求 7: 门户可见性控制

**用户故事:** 作为管理员，我希望控制哪些 Relation Table 的数据可以在 User Portal 中被业务用户查看，以便灵活管理数据可见范围。

#### 验收标准

1. THE Table_Structure_Manager SHALL 在表结构列表的 Portal Visibility 列中为每个 Relation Table 提供 Portal_Visibility_Switch（门户可见性开关），该开关位于 Enable 列旁边
2. WHEN 管理员开启某个表的 Portal_Visibility_Switch THEN Relation_Table_Module SHALL 将该表的数据列表显示在 User Portal 的 Relation Table 菜单中
3. WHEN 管理员关闭某个表的 Portal_Visibility_Switch THEN Relation_Table_Module SHALL 从 User Portal 中隐藏该表的数据列表
4. THE Admin_Center SHALL 始终显示所有 Relation Table 的数据，不受 Portal_Visibility_Switch 影响

### 需求 8: User Portal Relation Table 数据查看

**用户故事:** 作为业务用户，我希望在 User Portal 中查看被授权的 Relation Table 数据，以便获取业务所需的公共数据。

#### 验收标准

1. THE User_Portal SHALL 在侧边栏菜单中显示 "Relation Tables" 菜单项
2. WHEN 业务用户访问 Relation Tables 页面 THEN User_Portal SHALL 仅展示已开启 Portal_Visibility_Switch 且用户拥有对应 Business_Role 权限的表列表
3. WHEN 业务用户选择某个表 THEN User_Portal SHALL 根据已部署的表结构动态渲染只读数据列表
4. THE User_Portal SHALL 提供数据搜索和过滤功能
5. THE User_Portal SHALL 提供数据导出功能，支持将当前表数据导出为 CSV 格式文件
6. THE User_Portal SHALL 仅提供数据查看功能，不提供数据新增、修改或删除操作入口
7. IF 业务用户尝试通过 API 对 Relation Table 数据执行新增、修改或删除操作 THEN User_Portal SHALL 拒绝请求并返回无权限的错误提示

### 需求 9: Manage Table Bindings 绑定 Relation Table 与 View 页面管理

**用户故事:** 作为开发者，我希望通过 Manage Table Bindings 绑定 Relation Table，系统自动在 subtable 标签页旁边生成 view 页面，并在 view 页面中添加或删除绑定表的字段来设计数据展示，同时 Lookup_Component 配置 view 时仅允许选择已绑定的 Relation Table 的 view。

#### 验收标准

1. WHEN 开发者在 Form Designer 中打开 Manage Table Bindings 并点击 Add Binding THEN Table_Binding_Manager SHALL 在可选表列表中包含所有已部署的 Relation Table（common table）
2. THE Table_Binding_Manager SHALL 对 Relation Table 的绑定类型标记为 RELATED
3. WHEN 开发者成功绑定一个 Relation Table THEN Developer_Workstation SHALL 在 subtable 标签页旁边自动生成一个对应的 view 页面
4. THE Developer_Workstation SHALL 在自动生成的 view 页面中展示已绑定 Relation Table 的可用字段列表
5. THE Developer_Workstation SHALL 允许开发者在 view 页面中添加或删除已绑定 Relation Table 的字段，以自定义 view 的展示内容
6. WHEN 开发者在 view 页面中修改字段配置后保存 THEN Developer_Workstation SHALL 持久化 view 的字段配置信息
7. WHEN 开发者解除某个 Relation Table 的绑定 THEN Developer_Workstation SHALL 同步移除对应的 view 页面
8. WHEN 开发者在 Lookup_Component 中配置 view 信息 THEN Developer_Workstation SHALL 仅允许选择已通过 Manage Table Bindings 绑定的 Relation Table 的 view

### 需求 10: Lookup 扩展组件与 View 跳转配置

**用户故事:** 作为开发者，我希望自定义扩展一个 Lookup 字段，并在该字段中配置对应 Relation Table 的 view 信息、搜索字段和展示字段，点击 Lookup 字段后可跳转到对应的 view 页面来设计展示字段。

#### 验收标准

1. THE Developer_Workstation SHALL 在 Form Designer 的 form-create 组件库中提供 Lookup_Component 自定义扩展组件
2. WHEN 开发者将 Lookup_Component 拖入表单设计区域 THEN Developer_Workstation SHALL 在组件配置面板中仅允许选择已通过 Manage Table Bindings 绑定的 Relation Table
3. THE Lookup_Component SHALL 在组件配置面板中提供 Lookup_View_Config 配置区域，允许开发者配置对应 Relation Table 的 view 信息
4. WHEN 开发者点击 Lookup_Component 的 view 配置入口 THEN Developer_Workstation SHALL 跳转到对应 Relation Table 的 view 页面，供开发者设计该 view 应展示的字段
5. WHEN 开发者在 view 页面中完成字段设计并保存 THEN Developer_Workstation SHALL 将 view 的字段配置与 Lookup_Component 关联持久化
6. WHEN 开发者在 Developer_Workstation 的 Preview 或 Form Preview 页面中预览包含 Lookup_Component 的表单 THEN Developer_Workstation SHALL 在 Lookup 字段下方展示被 lookup 的 Relation Table 对应的 view 视图
7. THE Lookup_Component SHALL 在组件配置面板中提供 Search_Field_Config 配置项，允许开发者选择关联 Relation Table 的多个字段用于在 User Portal 中搜索数据
8. THE Lookup_Component SHALL 在组件配置面板中提供 Display_Field_Config 配置项，允许开发者选择关联 Relation Table 的哪个字段用于在 User Portal 中展示搜索结果

### 需求 11: User Portal Lookup 字段搜索与 View 展示

**用户故事:** 作为业务用户，我希望在 User Portal 的 My Requests、To_Do、Completed 中使用 Lookup 字段时，能够搜索 Relation Table 数据并查看关联的 view 视图。

#### 验收标准

1. WHEN 业务用户在 My Requests 中新建数据 THEN User_Portal SHALL 将 Form 中的 Lookup_Component 关联字段渲染为搜索框
2. WHEN 业务用户在搜索框中输入关键字 THEN Lookup_Component SHALL 按照开发者配置的 Search_Field_Config 指定的字段对关联 Relation Table 数据进行模糊匹配，并展示搜索结果列表
3. THE Lookup_Component SHALL 在搜索结果列表中按照开发者配置的 Display_Field_Config 指定的字段展示每条匹配到的数据
4. WHEN 业务用户从搜索结果列表中选中某条数据 THEN Lookup_Component SHALL 在该 Lookup 字段下方按照开发者在 Developer_Workstation 中设计的 Lookup_View_Config 展示选中数据对应的 view 视图，view 视图的字段和布局与开发者在 Developer_Workstation 中设计的 view 页面配置一致
5. WHEN 业务用户在 To_Do 中使用 Lookup_Component THEN User_Portal SHALL 支持搜索 Relation Table 数据，选中后在 Lookup 字段下方展示对应的 view 视图，view 视图的字段和布局与开发者在 Developer_Workstation 中设计的 view 页面配置一致
6. WHEN 业务用户在 Completed 中查看包含 Lookup_Component 的表单 THEN User_Portal SHALL 在 Lookup 字段下方以只读模式展示对应的 view 视图，不支持搜索和编辑操作

### 需求 12: 数据权限控制

**用户故事:** 作为管理员，我希望通过 Business Role 控制 User Portal 中用户对 Relation Table 数据的访问权限，以便实现细粒度的数据权限管理。

#### 验收标准

1. THE Relation_Table_Module SHALL 在 Admin Center 中为每个 Relation Table 提供 Business Role 访问配置功能（与 Function Unit 的 Access 配置一致）
2. WHEN 管理员为某个 Relation Table 分配 Business_Role THEN Relation_Table_Module SHALL 记录该角色对该表的权限配置
3. THE Relation_Table_Module SHALL 支持为每个 Business_Role 配置 Read 数据操作权限，User Portal 中仅支持 Read 操作
4. WHEN 业务用户在 User Portal 访问 Relation Table 数据 THEN Relation_Table_Module SHALL 根据用户所拥有的 Business_Role 判断是否允许查看该表数据
5. THE User_Portal SHALL 仅允许业务用户执行 Read 操作，不允许执行 Update、Delete、Add 操作
6. THE Admin_Center SHALL 仅限 admin 角色访问，admin 可执行所有表结构管理、字段修改和数据管理操作，无需额外的 Business_Role 配置

### 需求 13: Audit Log 集成

**用户故事:** 作为管理员，我希望 Relation Table 的数据变更操作都有审计日志记录，以便追踪数据变更历史。

#### 验收标准

1. WHEN 数据被新增 THEN Audit_Log SHALL 记录操作人、操作时间、操作类型（ADD）和新增的数据内容
2. WHEN 数据被修改 THEN Audit_Log SHALL 记录操作人、操作时间、操作类型（UPDATE）、修改前的数据内容和修改后的数据内容
3. WHEN 数据状态被变更（Active/Inactive） THEN Audit_Log SHALL 记录操作人、操作时间、操作类型（STATUS_CHANGE）和状态变更详情
4. WHEN 数据被删除 THEN Audit_Log SHALL 记录操作人、操作时间、操作类型（DELETE）和被删除的数据内容
5. THE Audit_Log SHALL 支持按操作时间、操作人、操作类型进行查询和过滤
