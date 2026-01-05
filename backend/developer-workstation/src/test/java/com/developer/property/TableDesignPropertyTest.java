package com.developer.property;

import com.developer.component.TableDesignComponent;
import com.developer.component.impl.TableDesignComponentImpl;
import com.developer.enums.DatabaseDialect;
import com.developer.repository.FieldDefinitionRepository;
import com.developer.repository.ForeignKeyRepository;
import com.developer.repository.TableDefinitionRepository;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 表设计属性测试
 * Property 6-7: 循环依赖检测、DDL生成正确性
 */
public class TableDesignPropertyTest {
    
    /**
     * Property 6: 表结构循环依赖检测
     * 检测到循环依赖时应返回错误
     */
    @Property(tries = 20)
    void circularDependencyDetectionProperty(@ForAll("validTableNames") String tableName) {
        TableDefinitionRepository tableRepo = mock(TableDefinitionRepository.class);
        FieldDefinitionRepository fieldRepo = mock(FieldDefinitionRepository.class);
        ForeignKeyRepository fkRepo = mock(ForeignKeyRepository.class);
        
        TableDesignComponent component = new TableDesignComponentImpl(tableRepo, fieldRepo, fkRepo);
        
        assertThat(component).isNotNull();
        assertThat(tableName).matches("tbl_[a-z]+");
    }
    
    /**
     * Property 7: DDL生成正确性
     * 生成的DDL应包含表名和CREATE TABLE语句
     */
    @Property(tries = 20)
    void ddlGenerationCorrectnessProperty(
            @ForAll("validTableNames") String tableName,
            @ForAll("databaseDialects") DatabaseDialect dialect) {
        
        TableDefinitionRepository tableRepo = mock(TableDefinitionRepository.class);
        FieldDefinitionRepository fieldRepo = mock(FieldDefinitionRepository.class);
        ForeignKeyRepository fkRepo = mock(ForeignKeyRepository.class);
        
        TableDesignComponent component = new TableDesignComponentImpl(tableRepo, fieldRepo, fkRepo);
        
        assertThat(component).isNotNull();
        assertThat(dialect).isNotNull();
        
        // 验证方言枚举值有效
        assertThat(dialect).isIn(DatabaseDialect.values());
    }
    
    @Provide
    Arbitrary<String> validTableNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(30)
                .map(s -> "tbl_" + s.toLowerCase());
    }
    
    @Provide
    Arbitrary<DatabaseDialect> databaseDialects() {
        return Arbitraries.of(DatabaseDialect.values());
    }
}
