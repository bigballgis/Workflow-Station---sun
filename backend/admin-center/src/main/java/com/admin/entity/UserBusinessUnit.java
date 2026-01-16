package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 用户业务单元成员关系实体
 * 表示用户是某个业务单元的成员（不包含角色信息）
 * 用户通过加入虚拟组获取角色，通过加入业务单元激活 BU-Bounded 角色
 */
@Entity
@Table(name = "sys_user_business_units", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "business_unit_id"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserBusinessUnit {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @Column(name = "business_unit_id", nullable = false, length = 64)
    private String businessUnitId;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id", insertable = false, updatable = false)
    private BusinessUnit businessUnit;
}
