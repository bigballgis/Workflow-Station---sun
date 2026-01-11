package com.admin.entity;

import com.admin.enums.FunctionUnitStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 功能单元实体
 * 表示从开发工作站导入的功能包
 */
@Entity
@Table(name = "sys_function_units")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FunctionUnit {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "code", nullable = false, length = 50)
    private String code;
    
    @Column(name = "version", nullable = false, length = 20)
    private String version;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "package_path", length = 500)
    private String packagePath;
    
    @Column(name = "package_size")
    private Long packageSize;
    
    @Column(name = "checksum", length = 64)
    private String checksum;
    
    @Column(name = "digital_signature", columnDefinition = "TEXT")
    private String digitalSignature;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FunctionUnitStatus status = FunctionUnitStatus.DRAFT;
    
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    @Column(name = "imported_at")
    private Instant importedAt;
    
    @Column(name = "imported_by", length = 64)
    private String importedBy;
    
    @Column(name = "validated_at")
    private Instant validatedAt;
    
    @Column(name = "validated_by", length = 64)
    private String validatedBy;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    
    @OneToMany(mappedBy = "functionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<FunctionUnitDeployment> deployments = new HashSet<>();
    
    @OneToMany(mappedBy = "functionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<FunctionUnitDependency> dependencies = new HashSet<>();
    
    @OneToMany(mappedBy = "functionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<FunctionUnitContent> contents = new HashSet<>();
    
    /**
     * 检查功能单元是否可以部署
     */
    public boolean isDeployable() {
        return status == FunctionUnitStatus.VALIDATED || status == FunctionUnitStatus.DEPLOYED;
    }
    
    /**
     * 检查功能单元是否已废弃
     */
    public boolean isDeprecated() {
        return status == FunctionUnitStatus.DEPRECATED;
    }
    
    /**
     * 检查功能单元是否启用
     */
    public boolean isEnabled() {
        return enabled != null && enabled;
    }
    
    /**
     * 检查功能单元是否对用户可用（已部署且启用）
     */
    public boolean isAvailableToUsers() {
        return status == FunctionUnitStatus.DEPLOYED && isEnabled();
    }
    
    /**
     * 获取完整版本标识
     */
    public String getFullVersionId() {
        return code + ":" + version;
    }
    
    /**
     * 标记为已验证
     */
    public void markAsValidated(String validatorId) {
        this.status = FunctionUnitStatus.VALIDATED;
        this.validatedAt = Instant.now();
        this.validatedBy = validatorId;
    }
    
    /**
     * 标记为已部署
     */
    public void markAsDeployed() {
        this.status = FunctionUnitStatus.DEPLOYED;
    }
    
    /**
     * 标记为已废弃
     */
    public void markAsDeprecated() {
        this.status = FunctionUnitStatus.DEPRECATED;
    }
    
    /**
     * 添加部署记录
     */
    public void addDeployment(FunctionUnitDeployment deployment) {
        if (deployments == null) {
            deployments = new HashSet<>();
        }
        deployment.setFunctionUnit(this);
        deployments.add(deployment);
    }
    
    /**
     * 添加依赖
     */
    public void addDependency(FunctionUnitDependency dependency) {
        if (dependencies == null) {
            dependencies = new HashSet<>();
        }
        dependency.setFunctionUnit(this);
        dependencies.add(dependency);
    }
    
    /**
     * 添加内容
     */
    public void addContent(FunctionUnitContent content) {
        if (contents == null) {
            contents = new HashSet<>();
        }
        content.setFunctionUnit(this);
        contents.add(content);
    }
}
