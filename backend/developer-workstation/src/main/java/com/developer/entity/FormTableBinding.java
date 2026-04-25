package com.developer.entity;

import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 表单表绑定实体
 * 管理表单与数据表的多对多绑定关系
 */
@Entity
@Table(name = "dw_form_table_bindings")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FormTableBinding {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private FormDefinition form;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private TableDefinition table;
    
    /**
     * Relation Table ID（来自 rt_table_definitions）
     * 仅当 bindingType 为 RELATED 时使用
     */
    @Column(name = "relation_table_id")
    private Long relationTableId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "binding_type", nullable = false, length = 20)
    private BindingType bindingType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "binding_mode", nullable = false, length = 20)
    private BindingMode bindingMode;
    
    /**
     * 子表关联主表的外键字段名
     * 仅当 bindingType 为 SUB 或 RELATED 时需要
     */
    @Column(name = "foreign_key_field", length = 100)
    private String foreignKeyField;
    
    /**
     * 排序顺序
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 子表列表视图配置ID
     * 仅当 bindingType 为 SUB 时使用，关联到 dw_sub_table_view_configs 表
     */
    @Column(name = "sub_list_view_id")
    private Long subListViewId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    /**
     * 获取绑定表ID（用于JSON序列化）
     * RELATED 类型返回 relationTableId，其他类型返回 table.id
     */
    public Long getTableId() {
        if (bindingType == BindingType.RELATED && relationTableId != null) {
            return relationTableId;
        }
        return table != null ? table.getId() : null;
    }
    
    /**
     * 获取绑定表名称（用于JSON序列化）
     */
    public String getTableName() {
        return table != null ? table.getTableName() : null;
    }
    
    /**
     * 获取表单ID（用于JSON序列化）
     */
    public Long getFormId() {
        return form != null ? form.getId() : null;
    }
}
