# Design Document: Form Multi-Table Binding

## Overview

本设计扩展现有的表单设计器，支持一个表单绑定多个数据表。通过引入 `FormTableBinding` 实体来管理表单与表的多对多关系，并在前端提供直观的绑定管理界面和子表数据展示组件。

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Form Designer (Frontend)                  │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ Table Binding   │  │ Field Import    │  │ SubTable    │ │
│  │ Manager         │  │ Dialog          │  │ Component   │ │
│  └────────┬────────┘  └────────┬────────┘  └──────┬──────┘ │
│           │                    │                   │        │
│           └────────────────────┼───────────────────┘        │
│                                │                            │
└────────────────────────────────┼────────────────────────────┘
                                 │ REST API
┌────────────────────────────────┼────────────────────────────┐
│                    Backend Services                          │
├────────────────────────────────┼────────────────────────────┤
│  ┌─────────────────────────────┴─────────────────────────┐  │
│  │              FormDesignComponent                       │  │
│  │  - createBinding()                                     │  │
│  │  - updateBinding()                                     │  │
│  │  - deleteBinding()                                     │  │
│  │  - getBindings()                                       │  │
│  └───────────────────────────────────────────────────────┘  │
│                                │                            │
│  ┌─────────────────────────────┴─────────────────────────┐  │
│  │              FormTableBindingRepository               │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                                 │
┌────────────────────────────────┼────────────────────────────┐
│                    Database                                  │
├─────────────────────────────────────────────────────────────┤
│  dw_form_definitions          dw_form_table_bindings        │
│  ┌─────────────────┐          ┌─────────────────────────┐   │
│  │ id              │──────────│ form_id                 │   │
│  │ form_name       │          │ table_id                │───┤
│  │ bound_table_id  │          │ binding_type (PRIMARY/  │   │
│  │ ...             │          │              SUB/RELATED)│   │
│  └─────────────────┘          │ binding_mode (EDITABLE/ │   │
│                               │              READONLY)   │   │
│  dw_table_definitions         │ foreign_key_field       │   │
│  ┌─────────────────┐          │ sort_order              │   │
│  │ id              │──────────│ created_at              │   │
│  │ table_name      │          └─────────────────────────┘   │
│  │ ...             │                                        │
│  └─────────────────┘                                        │
└─────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### Backend Components

#### 1. FormTableBinding Entity

```java
@Entity
@Table(name = "dw_form_table_bindings")
public class FormTableBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private FormDefinition form;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private TableDefinition table;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "binding_type", nullable = false)
    private BindingType bindingType; // PRIMARY, SUB, RELATED
    
    @Enumerated(EnumType.STRING)
    @Column(name = "binding_mode", nullable = false)
    private BindingMode bindingMode; // EDITABLE, READONLY
    
    @Column(name = "foreign_key_field")
    private String foreignKeyField; // 子表关联主表的外键字段
    
    @Column(name = "sort_order")
    private Integer sortOrder;
}
```

#### 2. BindingType Enum

```java
public enum BindingType {
    PRIMARY,  // 主表，表单的主要数据来源
    SUB,      // 子表，一对多关系
    RELATED   // 关联表，多对多或引用关系
}
```

#### 3. BindingMode Enum

```java
public enum BindingMode {
    EDITABLE, // 可编辑
    READONLY  // 只读
}
```

#### 4. FormTableBindingRepository

```java
public interface FormTableBindingRepository extends JpaRepository<FormTableBinding, Long> {
    List<FormTableBinding> findByFormIdOrderBySortOrder(Long formId);
    Optional<FormTableBinding> findByFormIdAndBindingType(Long formId, BindingType type);
    boolean existsByFormIdAndTableId(Long formId, Long tableId);
    boolean existsByTableId(Long tableId);
    void deleteByFormId(Long formId);
}
```

#### 5. FormDesignComponent Extensions

```java
public interface FormDesignComponent {
    // 现有方法...
    
    // 新增绑定管理方法
    FormTableBinding createBinding(Long formId, FormTableBindingRequest request);
    FormTableBinding updateBinding(Long bindingId, FormTableBindingRequest request);
    void deleteBinding(Long bindingId);
    List<FormTableBinding> getBindings(Long formId);
}
```

### Frontend Components

#### 1. TableBindingManager Component

管理表单的多表绑定，显示绑定列表，支持添加/编辑/删除绑定。

```typescript
interface TableBinding {
  id?: number
  tableId: number
  tableName: string
  bindingType: 'PRIMARY' | 'SUB' | 'RELATED'
  bindingMode: 'EDITABLE' | 'READONLY'
  foreignKeyField?: string
  sortOrder: number
}
```

#### 2. SubTableComponent

在表单中展示子表数据的组件，支持配置数据源、显示列、编辑模式等。

```typescript
interface SubTableConfig {
  bindingId: number
  columns: string[]
  editable: boolean
  pageSize: number
}
```

### REST API Endpoints

```
POST   /api/function-units/{fuId}/forms/{formId}/bindings
GET    /api/function-units/{fuId}/forms/{formId}/bindings
PUT    /api/function-units/{fuId}/forms/{formId}/bindings/{bindingId}
DELETE /api/function-units/{fuId}/forms/{formId}/bindings/{bindingId}
```

## Data Models

### Database Schema

```sql
-- 表单表绑定关系表
CREATE TABLE dw_form_table_bindings (
    id BIGSERIAL PRIMARY KEY,
    form_id BIGINT NOT NULL REFERENCES dw_form_definitions(id) ON DELETE CASCADE,
    table_id BIGINT NOT NULL REFERENCES dw_table_definitions(id),
    binding_type VARCHAR(20) NOT NULL, -- PRIMARY, SUB, RELATED
    binding_mode VARCHAR(20) NOT NULL DEFAULT 'READONLY', -- EDITABLE, READONLY
    foreign_key_field VARCHAR(100), -- 子表关联主表的外键字段名
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE(form_id, table_id)
);

CREATE INDEX idx_form_table_bindings_form ON dw_form_table_bindings(form_id);
CREATE INDEX idx_form_table_bindings_table ON dw_form_table_bindings(table_id);
```

### Migration Strategy

为了向后兼容，现有的 `bound_table_id` 字段将被迁移到新的 `dw_form_table_bindings` 表：

```sql
-- 迁移现有绑定数据
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order)
SELECT id, bound_table_id, 'PRIMARY', 'EDITABLE', 0
FROM dw_form_definitions
WHERE bound_table_id IS NOT NULL;
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Multi-table binding support

*For any* form with a primary table bound, adding sub-tables or related tables should result in all bindings being stored and retrievable, with each binding having the correct type and configuration.

**Validates: Requirements 1.1, 1.3, 1.4**

### Property 2: Foreign key validation

*For any* sub-table or related-table binding with a specified foreign key field, the field must exist in the bound table's field definitions; otherwise, the binding should be rejected with an error.

**Validates: Requirements 2.1, 2.2, 2.3**

### Property 3: Binding mode configuration

*For any* table binding, the binding mode should be configurable as either EDITABLE or READONLY, with sub-tables and related-tables defaulting to READONLY when first created.

**Validates: Requirements 3.1, 3.2, 3.3**

### Property 4: Field import from multiple tables

*For any* form with multiple bound tables, fields can be imported from any bound table, each imported field should indicate its source table, and duplicate fields from the same table should be rejected.

**Validates: Requirements 4.1, 4.2, 4.4**

### Property 5: Sub-table component configuration

*For any* sub-table component in a form, it should be configurable with a data source (bound sub-table), display columns, and when binding mode is EDITABLE, it should support inline CRUD operations.

**Validates: Requirements 5.2, 5.3, 5.4**

### Property 6: Binding persistence round-trip

*For any* set of table bindings created for a form, saving and then loading the form should return all bindings with their original configurations intact; individual bindings should be updatable and deletable.

**Validates: Requirements 6.1, 6.2, 6.3**

### Property 7: Referential integrity on table deletion

*For any* table that is bound to one or more forms, attempting to delete the table should either be prevented or should properly cascade/handle the binding relationships.

**Validates: Requirements 6.4**

## Error Handling

| Error Code | Description | User Message |
|------------|-------------|--------------|
| BINDING_EXISTS | 表已绑定到此表单 | 该表已绑定到此表单，请勿重复绑定 |
| PRIMARY_BINDING_EXISTS | 主表绑定已存在 | 此表单已有主表绑定，请先删除现有主表绑定 |
| INVALID_FOREIGN_KEY | 外键字段不存在 | 指定的外键字段在表中不存在 |
| BINDING_NOT_FOUND | 绑定不存在 | 找不到指定的表绑定 |
| TABLE_IN_USE | 表被表单引用 | 该表被表单引用，无法删除 |

## Testing Strategy

### Unit Tests

- 测试 `FormTableBinding` 实体的创建和验证
- 测试 `FormTableBindingRepository` 的查询方法
- 测试外键字段验证逻辑
- 测试绑定模式默认值

### Property-Based Tests

使用 jqwik 进行属性测试：

- **Property 1**: 生成随机表单和表，测试多表绑定的存储和检索
- **Property 2**: 生成随机外键字段名，测试验证逻辑
- **Property 3**: 生成随机绑定配置，测试模式设置
- **Property 6**: 生成随机绑定集合，测试持久化往返

### Integration Tests

- 测试完整的绑定创建流程
- 测试前端绑定管理界面
- 测试子表组件数据加载
