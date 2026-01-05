package com.admin.entity;

import com.admin.enums.DataSourceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 数据字典关联数据源实体
 */
@Entity
@Table(name = "admin_dictionary_data_sources", indexes = {
        @Index(name = "idx_dict_ds_dict_id", columnList = "dictionary_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DictionaryDataSource {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(name = "dictionary_id", nullable = false, length = 36)
    private String dictionaryId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSourceType sourceType;
    
    /** 数据库连接名或API地址 */
    @Column(length = 500)
    private String connectionString;
    
    /** 表名或API端点 */
    @Column(length = 200)
    private String tableName;
    
    /** 代码字段映射 */
    @Column(length = 100)
    private String codeField;
    
    /** 名称字段映射 */
    @Column(length = 100)
    private String nameField;
    
    /** 值字段映射 */
    @Column(length = 100)
    private String valueField;
    
    /** 过滤条件 */
    @Column(length = 500)
    private String filterCondition;
    
    /** 排序字段 */
    @Column(length = 100)
    private String orderByField;
    
    /** 缓存时间(秒) */
    @Column
    @Builder.Default
    private Integer cacheTtl = 300;
    
    /** 是否启用 */
    @Column
    @Builder.Default
    private Boolean enabled = true;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
