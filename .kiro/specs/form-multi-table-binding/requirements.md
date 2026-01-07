# Requirements Document

## Introduction

本功能扩展表单设计器，支持表单绑定多个数据表（主表、子表、关联表），使表单能够展示和操作关联数据。这对于复杂业务场景（如请假申请需要显示历史调整记录、订单需要显示明细等）至关重要。

## Glossary

- **Form_Designer**: 表单设计器组件，用于可视化设计表单布局和字段
- **Primary_Table**: 主表，表单的主要数据来源，支持增删改查操作
- **Sub_Table**: 子表，与主表存在一对多关系的从属表
- **Related_Table**: 关联表，与主表存在多对多或引用关系的表
- **Table_Binding**: 表绑定配置，定义表单与数据表的关联关系
- **Binding_Mode**: 绑定模式，包括可编辑(EDITABLE)和只读(READONLY)两种

## Requirements

### Requirement 1: 支持绑定多个数据表

**User Story:** 作为表单设计者，我希望能够为一个表单绑定多个数据表，以便在表单中展示和操作关联数据。

#### Acceptance Criteria

1. THE Form_Designer SHALL support binding one Primary_Table and multiple Sub_Tables or Related_Tables to a single form
2. WHEN a form is created, THE Form_Designer SHALL allow selecting a Primary_Table as the main data source
3. WHEN a Primary_Table is bound, THE Form_Designer SHALL allow adding additional Sub_Tables or Related_Tables
4. THE Form_Designer SHALL display all bound tables in a clear list with their binding types

### Requirement 2: 配置表绑定关系

**User Story:** 作为表单设计者，我希望能够配置表之间的关联关系，以便系统能正确查询和显示关联数据。

#### Acceptance Criteria

1. WHEN binding a Sub_Table, THE Form_Designer SHALL require specifying the foreign key field that references the Primary_Table
2. WHEN binding a Related_Table, THE Form_Designer SHALL allow specifying the join condition or reference field
3. THE Form_Designer SHALL validate that the specified foreign key or reference field exists in the bound table
4. IF an invalid foreign key is specified, THEN THE Form_Designer SHALL display an error message

### Requirement 3: 配置绑定模式

**User Story:** 作为表单设计者，我希望能够为每个绑定的表设置访问模式（可编辑或只读），以便控制用户对数据的操作权限。

#### Acceptance Criteria

1. THE Form_Designer SHALL support two Binding_Modes: EDITABLE and READONLY
2. WHEN binding a table, THE Form_Designer SHALL default to READONLY mode for Sub_Tables and Related_Tables
3. THE Form_Designer SHALL allow changing the Binding_Mode for any bound table
4. WHEN Binding_Mode is READONLY, THE generated form SHALL display data without edit controls

### Requirement 4: 从绑定表导入字段

**User Story:** 作为表单设计者，我希望能够从任意绑定的表导入字段到表单，以便快速构建表单布局。

#### Acceptance Criteria

1. WHEN multiple tables are bound, THE Form_Designer SHALL allow importing fields from any bound table
2. THE Form_Designer SHALL clearly indicate which table each imported field belongs to
3. WHEN importing fields from a Sub_Table, THE Form_Designer SHALL generate appropriate list/table components
4. THE Form_Designer SHALL prevent importing duplicate fields from the same table

### Requirement 5: 子表数据展示组件

**User Story:** 作为表单设计者，我希望能够在表单中添加子表数据展示组件，以便用户查看和编辑一对多关联数据。

#### Acceptance Criteria

1. THE Form_Designer SHALL provide a Sub_Table component for displaying one-to-many data
2. WHEN a Sub_Table component is added, THE Form_Designer SHALL allow selecting which bound Sub_Table to display
3. THE Sub_Table component SHALL support configuring which columns to display
4. WHEN Binding_Mode is EDITABLE, THE Sub_Table component SHALL support inline add/edit/delete operations

### Requirement 6: 持久化多表绑定配置

**User Story:** 作为系统，我需要持久化表单的多表绑定配置，以便在表单加载时正确获取关联数据。

#### Acceptance Criteria

1. THE System SHALL store multiple Table_Bindings for each form in the database
2. WHEN a form is loaded, THE System SHALL retrieve all bound tables and their configurations
3. THE System SHALL support updating and deleting individual Table_Bindings
4. THE System SHALL validate referential integrity when deleting a table that is bound to forms
