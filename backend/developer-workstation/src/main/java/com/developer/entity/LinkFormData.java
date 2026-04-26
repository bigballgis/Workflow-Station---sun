package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 链接表单数据实体
 * 存储每个子表行关联的表单数据
 */
@Entity
@Table(name = "dw_link_form_data")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class LinkFormData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "component_id", nullable = false)
    private Long componentId;
    
    @Column(name = "sub_table_row_id", nullable = false)
    private Long subTableRowId;
    
    /**
     * 表单数据JSON
     */
    @Column(name = "form_data", nullable = false, columnDefinition = "jsonb")
    private String formData;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
