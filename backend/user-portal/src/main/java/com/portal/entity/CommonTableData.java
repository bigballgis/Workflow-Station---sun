package com.portal.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 公共表数据实体（JSONB 行存储）
 */
@Entity
@Table(name = "dw_common_table_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class CommonTableData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "common_table_id", nullable = false)
    private CommonTableDefinition commonTable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> dataJson;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getCommonTableId() {
        return commonTable != null ? commonTable.getId() : null;
    }
}
