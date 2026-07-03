package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 链接表单组件实体
 * 用于在子表列表视图中显示可点击的链接，点击后弹出表单
 */
@Entity
@Table(name = "dw_link_form_components")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class LinkFormComponent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "function_unit_id", nullable = false)
    private Long functionUnitId;
    
    @Column(name = "component_name", nullable = false, length = 200)
    private String componentName;
    
    @Column(name = "linked_form_id", nullable = false)
    private Long linkedFormId;
    
    /**
     * 显示字段名（从表单数据中读取作为链接显示内容）
     */
    @Column(name = "display_field", length = 100)
    private String displayField;
    
    /**
     * 链接显示文字
     */
    @Column(name = "link_text", length = 200)
    private String linkText;
    
    /**
     * 列标签（在列表视图中的列名）
     */
    @Column(name = "column_label", length = 200)
    private String columnLabel;
    
    @Column(name = "sort_order")
    private Integer sortOrder;
    
    /**
     * 组件配置JSON（包含其他配置）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "jsonb")
    private String configJson;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
