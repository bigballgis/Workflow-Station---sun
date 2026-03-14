package com.developer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;

/**
 * 公共表数据实体
 * 以 JSONB 格式存储实际数据行
 */
@Entity
@Table(name = "dw_common_table_data")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getCommonTableId() {
        return commonTable != null ? commonTable.getId() : null;
    }
}
