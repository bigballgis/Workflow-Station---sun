package com.developer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 版本实体
 */
@Entity
@Table(name = "dw_versions")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Version {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;
    
    @Column(name = "version_number", nullable = false, length = 20)
    private String versionNumber;
    
    @Column(name = "change_log", columnDefinition = "TEXT")
    private String changeLog;
    
    @JsonIgnore
    @Lob
    @Column(name = "snapshot_data", nullable = false)
    private byte[] snapshotData;
    
    @Column(name = "published_by", nullable = false, length = 50)
    private String publishedBy;
    
    @CreatedDate
    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;
}
