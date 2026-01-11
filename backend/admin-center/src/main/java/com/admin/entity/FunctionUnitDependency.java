package com.admin.entity;

import com.admin.enums.DependencyType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 功能单元依赖关系实体
 */
@Entity
@Table(name = "sys_function_unit_dependencies")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FunctionUnitDependency {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;
    
    @Column(name = "dependency_code", nullable = false, length = 50)
    private String dependencyCode;
    
    @Column(name = "dependency_version", nullable = false, length = 20)
    private String dependencyVersion;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false, length = 20)
    @Builder.Default
    private DependencyType dependencyType = DependencyType.REQUIRED;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    /**
     * 获取完整依赖标识
     */
    public String getFullDependencyId() {
        return dependencyCode + ":" + dependencyVersion;
    }
    
    /**
     * 检查是否是必需依赖
     */
    public boolean isRequired() {
        return dependencyType == DependencyType.REQUIRED;
    }
    
    /**
     * 检查是否是可选依赖
     */
    public boolean isOptional() {
        return dependencyType == DependencyType.OPTIONAL;
    }
}
