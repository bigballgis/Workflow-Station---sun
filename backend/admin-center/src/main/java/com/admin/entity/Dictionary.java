package com.admin.entity;

import com.admin.enums.DataSourceType;
import com.admin.enums.DictionaryStatus;
import com.admin.enums.DictionaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据字典实体
 */
@Entity
@Table(name = "admin_dictionaries", indexes = {
        @Index(name = "idx_dict_code", columnList = "code", unique = true),
        @Index(name = "idx_dict_type", columnList = "type"),
        @Index(name = "idx_dict_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Dictionary {
    
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 字典代码（唯一标识）
     */
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    
    /**
     * 字典名称
     */
    @Column(nullable = false, length = 100)
    private String name;
    
    /**
     * 字典描述
     */
    @Column(length = 500)
    private String description;
    
    /**
     * 字典类型
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DictionaryType type;
    
    /**
     * 字典状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DictionaryStatus status;

    
    /**
     * 关联数据源类型
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DataSourceType dataSourceType;
    
    /**
     * 关联数据源配置（JSON格式）
     */
    @Column(columnDefinition = "TEXT")
    private String dataSourceConfig;
    
    /**
     * 缓存策略（秒，0表示不缓存）
     */
    @Column
    @Builder.Default
    private Integer cacheTtl = 0;
    
    /**
     * 当前版本号
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;
    
    /**
     * 排序号
     */
    @Column
    @Builder.Default
    private Integer sortOrder = 0;
    
    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    /**
     * 创建者
     */
    @Column(length = 36)
    private String createdBy;
    
    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
    
    /**
     * 更新者
     */
    @Column(length = 36)
    private String updatedBy;
    
    /**
     * 字典项列表
     */
    @OneToMany(mappedBy = "dictionary", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DictionaryItem> items = new ArrayList<>();
    
    /**
     * 增加版本号
     */
    public void incrementVersion() {
        this.version++;
    }
    
    /**
     * 是否为系统字典
     */
    public boolean isSystemDictionary() {
        return this.type == DictionaryType.SYSTEM;
    }
    
    /**
     * 是否可编辑
     */
    public boolean isEditable() {
        return this.type != DictionaryType.SYSTEM;
    }
    
    /**
     * 是否可删除
     */
    public boolean isDeletable() {
        return this.type != DictionaryType.SYSTEM;
    }
}
