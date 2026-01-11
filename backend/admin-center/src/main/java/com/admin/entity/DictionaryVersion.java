package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 数据字典版本历史实体
 */
@Entity
@Table(name = "sys_dictionary_versions", indexes = {
        @Index(name = "idx_dict_ver_dict_id", columnList = "dictionary_id"),
        @Index(name = "idx_dict_ver_version", columnList = "dictionary_id, version")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DictionaryVersion {
    
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 所属字典ID
     */
    @Column(name = "dictionary_id", nullable = false, length = 36)
    private String dictionaryId;
    
    /**
     * 版本号
     */
    @Column(nullable = false)
    private Integer version;
    
    /**
     * 快照数据（JSON格式，包含字典和所有字典项）
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String snapshotData;
    
    /**
     * 变更说明
     */
    @Column(length = 500)
    private String changeDescription;
    
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
}
