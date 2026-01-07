package com.developer.entity;

import com.developer.enums.FormType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表单定义实体
 */
@Entity
@Table(name = "dw_form_definitions")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FormDefinition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;
    
    @Column(name = "form_name", nullable = false, length = 100)
    private String formName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "form_type", nullable = false, length = 20)
    private FormType formType;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configJson;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bound_table_id")
    private TableDefinition boundTable;
    
    /**
     * 表绑定列表（多表绑定）
     */
    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<FormTableBinding> tableBindings = new ArrayList<>();
    
    /**
     * 获取绑定表ID（用于JSON序列化，向后兼容）
     */
    public Long getBoundTableId() {
        return boundTable != null ? boundTable.getId() : null;
    }
    
    /**
     * 获取绑定表名称（用于JSON序列化，向后兼容）
     */
    public String getBoundTableName() {
        return boundTable != null ? boundTable.getTableName() : null;
    }
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
