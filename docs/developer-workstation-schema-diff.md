# Developer Workstation Schema 差异报告

## 概述

对比 Flyway 脚本与数据库实际结构的差异。数据库中的表结构与 Flyway 脚本存在较大差异，说明数据库是通过其他方式创建的（可能是 JPA/Hibernate 自动生成）。

## 差异详情

### 1. dw_function_units

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | VARCHAR(64) | **类型不同** |
| name | VARCHAR(100) | VARCHAR(100) | ✓ 一致 |
| code | - | VARCHAR(50) NOT NULL | **数据库多出** |
| description | VARCHAR(500) | TEXT | 类型不同 |
| type | - | VARCHAR(50) | **数据库多出** |
| status | VARCHAR(20) | VARCHAR(20) | ✓ 一致 |
| version | - | INTEGER | **数据库多出** |
| current_version | VARCHAR(20) | VARCHAR(20) | ✓ 一致 |
| icon_id | BIGINT | VARCHAR(64) | **类型不同** |
| created_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| created_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |
| updated_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| updated_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |

### 2. dw_process_definitions

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | VARCHAR(64) | **类型不同** |
| function_unit_id | BIGINT NOT NULL UNIQUE | VARCHAR(64) | **类型不同** |
| name | - | VARCHAR(100) NOT NULL | **数据库多出** |
| process_key | - | VARCHAR(100) NOT NULL | **数据库多出** |
| bpmn_xml | TEXT | TEXT | ✓ 一致 |
| version | - | INTEGER | **数据库多出** |
| status | - | VARCHAR(20) | **数据库多出** |
| created_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| created_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |
| updated_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| updated_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |

### 3. dw_table_definitions

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | VARCHAR(64) | **类型不同** |
| function_unit_id | BIGINT NOT NULL | VARCHAR(64) | **类型不同** |
| name | - | VARCHAR(100) NOT NULL | **数据库多出** |
| table_name | VARCHAR(100) NOT NULL | VARCHAR(100) NOT NULL | ✓ 一致 |
| table_type | VARCHAR(20) DEFAULT 'MAIN' | VARCHAR(20) NOT NULL | ✓ 基本一致 |
| description | VARCHAR(500) | TEXT | 类型不同 |
| created_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| created_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |
| updated_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| updated_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |

### 4. dw_field_definitions

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | VARCHAR(64) | **类型不同** |
| table_definition_id | BIGINT NOT NULL | - | **Flyway有，数据库无** |
| table_id | - | VARCHAR(64) NOT NULL | **数据库多出** |
| name | - | VARCHAR(100) NOT NULL | **数据库多出** |
| column_name | - | VARCHAR(100) NOT NULL | **数据库多出** |
| field_name | VARCHAR(100) NOT NULL | VARCHAR(100) NOT NULL | ✓ 一致 |
| data_type | VARCHAR(30) NOT NULL | VARCHAR(50) NOT NULL | 长度不同 |
| length | INTEGER | INTEGER | ✓ 一致 |
| precision_val | INTEGER | - | **Flyway有，数据库无** |
| precision_value | - | INTEGER | **数据库多出** |
| scale_val | INTEGER | - | **Flyway有，数据库无** |
| scale | - | INTEGER | **数据库多出** |
| nullable | BOOLEAN DEFAULT TRUE | BOOLEAN | ✓ 一致 |
| is_nullable | - | BOOLEAN | **数据库多出** |
| default_value | VARCHAR(200) | TEXT | 类型不同 |
| is_primary_key | BOOLEAN DEFAULT FALSE | BOOLEAN | ✓ 一致 |
| is_unique | BOOLEAN DEFAULT FALSE | BOOLEAN | ✓ 一致 |
| description | VARCHAR(500) | TEXT | 类型不同 |
| sort_order | INTEGER DEFAULT 0 | INTEGER | ✓ 一致 |
| created_at | TIMESTAMP | - | **Flyway有，数据库无** |
| updated_at | TIMESTAMP | - | **Flyway有，数据库无** |

### 5. dw_form_definitions

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | VARCHAR(64) | **类型不同** |
| function_unit_id | BIGINT NOT NULL | VARCHAR(64) | **类型不同** |
| name | - | VARCHAR(100) NOT NULL | **数据库多出** |
| form_key | - | VARCHAR(100) NOT NULL | **数据库多出** |
| form_name | VARCHAR(100) NOT NULL | VARCHAR(100) NOT NULL | ✓ 一致 |
| form_type | VARCHAR(20) DEFAULT 'MAIN' | VARCHAR(20) NOT NULL | ✓ 基本一致 |
| schema_json | - | TEXT | **数据库多出** |
| layout_json | - | TEXT | **数据库多出** |
| version | - | INTEGER | **数据库多出** |
| status | - | VARCHAR(20) | **数据库多出** |
| bound_table_id | BIGINT | BIGINT | ✓ 一致 |
| config_json | JSONB | JSONB NOT NULL | ✓ 基本一致 |
| description | VARCHAR(500) | TEXT | 类型不同 |
| created_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| created_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |
| updated_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| updated_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |

### 6. dw_action_definitions

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | VARCHAR(64) | **类型不同** |
| function_unit_id | BIGINT NOT NULL | VARCHAR(64) | **类型不同** |
| name | - | VARCHAR(100) NOT NULL | **数据库多出** |
| action_key | - | VARCHAR(100) NOT NULL | **数据库多出** |
| action_name | VARCHAR(100) NOT NULL | VARCHAR(100) NOT NULL | ✓ 一致 |
| action_type | VARCHAR(30) NOT NULL | VARCHAR(20) NOT NULL | 长度不同 |
| type | - | VARCHAR(50) | **数据库多出** |
| icon_id | BIGINT | - | **Flyway有，数据库无** |
| icon | - | VARCHAR(50) | **数据库多出** |
| button_color | VARCHAR(20) | VARCHAR(20) | ✓ 一致 |
| config_json | JSONB | TEXT | **类型不同** |
| description | VARCHAR(500) | TEXT | 类型不同 |
| is_default | BOOLEAN DEFAULT FALSE | BOOLEAN | ✓ 一致 |
| process_step_id | VARCHAR(100) | - | **Flyway有，数据库无** |
| created_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| created_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |
| updated_by | VARCHAR(50) | VARCHAR(64) | 长度不同 |
| updated_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |

### 7. dw_icons

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | BIGINT | ✓ 一致 |
| name | VARCHAR(100) NOT NULL UNIQUE | VARCHAR(100) NOT NULL | ✓ 一致 |
| category | VARCHAR(30) NOT NULL | VARCHAR(30) NOT NULL | ✓ 一致 |
| svg_content | TEXT NOT NULL | TEXT NOT NULL | ✓ 一致 |
| file_size | INTEGER | INTEGER | ✓ 一致 |
| description | VARCHAR(500) | VARCHAR(500) | ✓ 一致 |
| created_by | VARCHAR(50) | VARCHAR(50) | ✓ 一致 |
| created_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |
| updated_by | VARCHAR(50) | VARCHAR(50) | ✓ 一致 |
| updated_at | TIMESTAMP | TIMESTAMP | ✓ 一致 |

**dw_icons 表完全一致！**

### 8. dw_versions

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | VARCHAR(64) | **类型不同** |
| function_unit_id | BIGINT NOT NULL | VARCHAR(64) NOT NULL | **类型不同** |
| version_number | VARCHAR(20) NOT NULL | VARCHAR(20) NOT NULL | ✓ 一致 |
| status | - | VARCHAR(20) | **数据库多出** |
| change_log | TEXT | TEXT | ✓ 一致 |
| release_notes | - | TEXT | **数据库多出** |
| snapshot_data | BYTEA | OID | **类型不同** |
| published_by | VARCHAR(50) | VARCHAR(50) NOT NULL | ✓ 基本一致 |
| published_at | TIMESTAMP | TIMESTAMP WITH TIME ZONE NOT NULL | 类型略有不同 |
| created_at | - | TIMESTAMP | **数据库多出** |
| created_by | - | VARCHAR(64) | **数据库多出** |

### 9. dw_operation_logs

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | VARCHAR(64) | **类型不同** |
| operator | VARCHAR(50) NOT NULL | VARCHAR(50) NOT NULL | ✓ 一致 |
| user_id | - | VARCHAR(64) | **数据库多出** |
| operation | - | VARCHAR(50) NOT NULL | **数据库多出** |
| operation_type | VARCHAR(50) NOT NULL | VARCHAR(50) NOT NULL | ✓ 一致 |
| target_type | VARCHAR(50) NOT NULL | VARCHAR(50) NOT NULL | ✓ 一致 |
| target_id | BIGINT | BIGINT | ✓ 一致 |
| resource_type | - | VARCHAR(50) | **数据库多出** |
| resource_id | - | VARCHAR(64) | **数据库多出** |
| description | VARCHAR(500) | VARCHAR(500) | ✓ 一致 |
| details | TEXT | TEXT | ✓ 一致 |
| ip_address | VARCHAR(50) | VARCHAR(50) | ✓ 一致 |
| operation_time | TIMESTAMP NOT NULL | TIMESTAMP NOT NULL | ✓ 一致 |
| created_at | - | TIMESTAMP | **数据库多出** |

### 10. dw_foreign_keys

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | BIGINT | ✓ 一致 |
| source_table_id | BIGINT NOT NULL | - | **Flyway有，数据库无** |
| source_field_id | BIGINT NOT NULL | - | **Flyway有，数据库无** |
| target_table_id | BIGINT NOT NULL | - | **Flyway有，数据库无** |
| target_field_id | BIGINT NOT NULL | - | **Flyway有，数据库无** |
| table_id | - | BIGINT NOT NULL | **数据库多出** |
| field_id | - | BIGINT NOT NULL | **数据库多出** |
| ref_table_id | - | BIGINT NOT NULL | **数据库多出** |
| ref_field_id | - | BIGINT NOT NULL | **数据库多出** |
| on_delete | - | VARCHAR(20) | **数据库多出** |
| on_update | - | VARCHAR(20) | **数据库多出** |
| created_at | TIMESTAMP | - | **Flyway有，数据库无** |

### 11. dw_form_table_bindings

| 字段 | Flyway 脚本 | 数据库实际 | 差异 |
|------|-------------|-----------|------|
| id | BIGSERIAL | BIGINT | ✓ 一致 |
| form_id | BIGINT NOT NULL | BIGINT NOT NULL | ✓ 一致 |
| table_id | BIGINT NOT NULL | BIGINT NOT NULL | ✓ 一致 |
| binding_type | VARCHAR(20) NOT NULL | VARCHAR(20) NOT NULL | ✓ 一致 |
| binding_mode | VARCHAR(20) NOT NULL DEFAULT 'READONLY' | VARCHAR(20) NOT NULL | ✓ 一致 |
| foreign_key_field | VARCHAR(100) | VARCHAR(100) | ✓ 一致 |
| sort_order | INTEGER DEFAULT 0 | INTEGER | ✓ 一致 |
| created_at | TIMESTAMP NOT NULL | TIMESTAMP WITH TIME ZONE NOT NULL | 类型略有不同 |
| updated_at | TIMESTAMP | TIMESTAMP WITH TIME ZONE | 类型略有不同 |

**dw_form_table_bindings 表基本一致！**

## 主要问题总结

1. **ID 类型不一致**: Flyway 使用 `BIGSERIAL`，数据库使用 `VARCHAR(64)` (除了 dw_icons 和 dw_form_table_bindings)
2. **外键类型不一致**: Flyway 使用 `BIGINT`，数据库使用 `VARCHAR(64)`
3. **数据库有额外字段**: 很多表有 Flyway 脚本中没有的字段（如 code, name, version, status 等）
4. **字段命名不一致**: 如 `precision_val` vs `precision_value`, `scale_val` vs `scale`

## 建议

由于差异较大，建议以数据库实际结构为准，重新生成 Flyway V1 脚本。这样可以确保：
1. 新环境部署时表结构与现有数据库一致
2. 代码中的 JPA Entity 与数据库结构匹配
