package com.developer.component;

import com.developer.component.impl.TableDesignComponentImpl;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.enums.DatabaseDialect;
import com.developer.enums.TableType;
import com.developer.repository.*;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 测试 TableDesignComponent 为 SUB 类型表自动添加 row_version 列
 * 
 * **Validates: Requirements 6.5, 6.6**
 * 
 * 需求 6.5: row_version 用于乐观锁校验，防止并发编辑冲突
 * 需求 6.6: 子任务完成后递增 row_version 值
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TableDesignComponent - row_version Column for SUB Tables")
class TableDesignComponentRowVersionTest {

    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    
    @Mock
    private FieldDefinitionRepository fieldDefinitionRepository;
    
    @Mock
    private ForeignKeyRepository foreignKeyRepository;
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private FormDefinitionRepository formDefinitionRepository;
    
    @Mock
    private FormTableBindingRepository formTableBindingRepository;
    
    @Mock
    private I18nService i18nService;

    @Mock
    private com.developer.util.DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;
    
    private TableDesignComponent tableDesignComponent;
    
    @BeforeEach
    void setUp() {
        tableDesignComponent = new TableDesignComponentImpl(
            tableDefinitionRepository,
            fieldDefinitionRepository,
            foreignKeyRepository,
            functionUnitRepository,
            formDefinitionRepository,
            formTableBindingRepository,
            i18nService,
            sequenceSynchronizer
        );
    }
    
    @Test
    @DisplayName("Should add row_version column to SUB table DDL for PostgreSQL")
    void shouldAddRowVersionToSubTablePostgreSQL() {
        // Given: 一个 SUB 类型的表定义
        TableDefinition subTable = createSubTableDefinition();
        
        when(tableDefinitionRepository.findByIdWithFields(anyLong()))
            .thenReturn(Optional.of(subTable));
        
        // When: 生成 PostgreSQL DDL
        String ddl = tableDesignComponent.generateDDL(1L, DatabaseDialect.POSTGRESQL);
        
        // Then: DDL 应包含 row_version 列
        assertThat(ddl)
            .as("DDL should contain row_version column for SUB table")
            .contains("row_version BIGINT NOT NULL DEFAULT 1");
        
        assertThat(ddl)
            .as("DDL should contain CREATE TABLE statement")
            .contains("CREATE TABLE participants");
    }
    
    @Test
    @DisplayName("Should add row_version column to SUB table DDL for MySQL")
    void shouldAddRowVersionToSubTableMySQL() {
        // Given: 一个 SUB 类型的表定义
        TableDefinition subTable = createSubTableDefinition();
        
        when(tableDefinitionRepository.findByIdWithFields(anyLong()))
            .thenReturn(Optional.of(subTable));
        
        // When: 生成 MySQL DDL
        String ddl = tableDesignComponent.generateDDL(1L, DatabaseDialect.MYSQL);
        
        // Then: DDL 应包含 row_version 列
        assertThat(ddl)
            .as("DDL should contain row_version column for SUB table")
            .contains("row_version BIGINT NOT NULL DEFAULT 1");
    }
    
    @Test
    @DisplayName("Should NOT add row_version column to MAIN table DDL")
    void shouldNotAddRowVersionToMainTable() {
        // Given: 一个 MAIN 类型的表定义
        TableDefinition mainTable = createMainTableDefinition();
        
        when(tableDefinitionRepository.findByIdWithFields(anyLong()))
            .thenReturn(Optional.of(mainTable));
        
        // When: 生成 PostgreSQL DDL
        String ddl = tableDesignComponent.generateDDL(1L, DatabaseDialect.POSTGRESQL);
        
        // Then: DDL 不应包含 row_version 列
        assertThat(ddl)
            .as("DDL should NOT contain row_version column for MAIN table")
            .doesNotContain("row_version");
        
        assertThat(ddl)
            .as("DDL should contain CREATE TABLE statement")
            .contains("CREATE TABLE meetings");
    }
    
    @Test
    @DisplayName("Should add row_version column to SUB table DDL for Oracle")
    void shouldAddRowVersionToSubTableOracle() {
        // Given: 一个 SUB 类型的表定义
        TableDefinition subTable = createSubTableDefinition();
        
        when(tableDefinitionRepository.findByIdWithFields(anyLong()))
            .thenReturn(Optional.of(subTable));
        
        // When: 生成 Oracle DDL
        String ddl = tableDesignComponent.generateDDL(1L, DatabaseDialect.ORACLE);
        
        // Then: DDL 应包含 row_version 列（Oracle 使用 NUMBER(19)）
        assertThat(ddl)
            .as("DDL should contain row_version column for SUB table with Oracle type")
            .contains("row_version NUMBER(19) NOT NULL DEFAULT 1");
    }
    
    @Test
    @DisplayName("Should add row_version column to SUB table DDL for SQL Server")
    void shouldAddRowVersionToSubTableSQLServer() {
        // Given: 一个 SUB 类型的表定义
        TableDefinition subTable = createSubTableDefinition();
        
        when(tableDefinitionRepository.findByIdWithFields(anyLong()))
            .thenReturn(Optional.of(subTable));
        
        // When: 生成 SQL Server DDL
        String ddl = tableDesignComponent.generateDDL(1L, DatabaseDialect.SQLSERVER);
        
        // Then: DDL 应包含 row_version 列
        assertThat(ddl)
            .as("DDL should contain row_version column for SUB table")
            .contains("row_version BIGINT NOT NULL DEFAULT 1");
    }
    
    @Test
    @DisplayName("Should place row_version column after user-defined fields")
    void shouldPlaceRowVersionAfterUserFields() {
        // Given: 一个 SUB 类型的表定义，包含多个字段
        TableDefinition subTable = createSubTableDefinition();
        
        when(tableDefinitionRepository.findByIdWithFields(anyLong()))
            .thenReturn(Optional.of(subTable));
        
        // When: 生成 PostgreSQL DDL
        String ddl = tableDesignComponent.generateDDL(1L, DatabaseDialect.POSTGRESQL);
        
        // Then: row_version 应在用户定义的字段之后
        int nameIndex = ddl.indexOf("name VARCHAR");
        int emailIndex = ddl.indexOf("email VARCHAR");
        int assigneeIndex = ddl.indexOf("assignee_id VARCHAR");
        int rowVersionIndex = ddl.indexOf("row_version BIGINT");
        
        assertThat(rowVersionIndex)
            .as("row_version should appear after user-defined fields")
            .isGreaterThan(nameIndex)
            .isGreaterThan(emailIndex)
            .isGreaterThan(assigneeIndex);
    }
    
    // Helper methods
    
    private TableDefinition createSubTableDefinition() {
        FunctionUnit functionUnit = FunctionUnit.builder()
            .id(1L)
            .name("test_function_unit")
            .build();
        
        TableDefinition table = TableDefinition.builder()
            .id(1L)
            .functionUnit(functionUnit)
            .tableName("participants")
            .tableDisplayName("Participants")
            .tableType(TableType.SUB)
            .fieldDefinitions(new ArrayList<>())
            .build();
        
        // 添加字段定义
        List<FieldDefinition> fields = new ArrayList<>();
        
        fields.add(FieldDefinition.builder()
            .id(1L)
            .tableDefinition(table)
            .fieldName("id")
            .dataType(DataType.BIGINT)
            .nullable(false)
            .isPrimaryKey(true)
            .sortOrder(0)
            .build());
        
        fields.add(FieldDefinition.builder()
            .id(2L)
            .tableDefinition(table)
            .fieldName("name")
            .dataType(DataType.VARCHAR)
            .length(100)
            .nullable(false)
            .sortOrder(1)
            .build());
        
        fields.add(FieldDefinition.builder()
            .id(3L)
            .tableDefinition(table)
            .fieldName("email")
            .dataType(DataType.VARCHAR)
            .length(255)
            .nullable(true)
            .sortOrder(2)
            .build());
        
        fields.add(FieldDefinition.builder()
            .id(4L)
            .tableDefinition(table)
            .fieldName("assignee_id")
            .dataType(DataType.VARCHAR)
            .length(64)
            .nullable(true)
            .sortOrder(3)
            .build());
        
        table.getFieldDefinitions().addAll(fields);
        
        return table;
    }
    
    private TableDefinition createMainTableDefinition() {
        FunctionUnit functionUnit = FunctionUnit.builder()
            .id(1L)
            .name("test_function_unit")
            .build();
        
        TableDefinition table = TableDefinition.builder()
            .id(1L)
            .functionUnit(functionUnit)
            .tableName("meetings")
            .tableDisplayName("Meetings")
            .tableType(TableType.MAIN)
            .fieldDefinitions(new ArrayList<>())
            .build();
        
        // 添加字段定义
        List<FieldDefinition> fields = new ArrayList<>();
        
        fields.add(FieldDefinition.builder()
            .id(1L)
            .tableDefinition(table)
            .fieldName("id")
            .dataType(DataType.BIGINT)
            .nullable(false)
            .isPrimaryKey(true)
            .sortOrder(0)
            .build());
        
        fields.add(FieldDefinition.builder()
            .id(2L)
            .tableDefinition(table)
            .fieldName("title")
            .dataType(DataType.VARCHAR)
            .length(200)
            .nullable(false)
            .sortOrder(1)
            .build());
        
        table.getFieldDefinitions().addAll(fields);
        
        return table;
    }
}
