package com.developer.entity;

import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.enums.BindingLinkMode;
import com.developer.enums.SubMode;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_link_mode", length = 32)
    @Builder.Default
    private BindingLinkMode bindingLinkMode = BindingLinkMode.structuralFk;

    /**
     * 本 binding 按哪个已声明外键过滤自己的行（{@code dw_field_definitions.id}）。
     *
     * <p>存 id 而非列名：列名会被设计器改掉，靠名字匹配的判断已经因此答错过
     * （demo FU 把 {@code sub_task_id} 改成 {@code sub_task_idq} 后运行时两个方向都判错）。
     *
     * <p>{@code null} = 未声明，运行时退回扫描该表的全部外键 —— 而那只在一张表恰好声明
     * 一个外键时无歧义。故意**不加** {@code @Builder.Default}：{@link #bindingLinkMode}
     * 的 {@code @Builder.Default} 正是 Clone / Copy Form 三处静默丢值的成因
     * （见 {@code FunctionUnitCloner} 里的说明），不重复同一个坑。
     */
    @Column(name = "filter_fk_field_id")
    private Long filterFkFieldId;

    /**
     * 排序顺序
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 子表列表视图配置ID
     * 仅当 bindingType 为 SUB 且 subMode 为 FULL 时使用，关联到 dw_sub_table_view_configs 表
     */
    @Column(name = "sub_list_view_id")
    private Long subListViewId;

    /**
     * 子表绑定模式
     * FULL: 完整模式（表单设计 + 列表视图）
     * FORM_ONLY: 仅表单模式（仅表单设计）
     * 仅当 bindingType 为 SUB 时使用
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sub_mode", length = 20)
    private SubMode subMode;

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
