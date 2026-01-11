package com.admin.entity;

import com.admin.enums.ContentType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 功能单元内容实体
 * 存储功能包中的各类内容（流程定义、表单、数据表、脚本等）
 */
@Entity
@Table(name = "sys_function_unit_contents")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FunctionUnitContent {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;
    
    @Column(name = "content_name", nullable = false, length = 200)
    private String contentName;
    
    @Column(name = "content_path", length = 500)
    private String contentPath;
    
    @Column(name = "content_data", columnDefinition = "TEXT")
    private String contentData;
    
    @Column(name = "checksum", length = 64)
    private String checksum;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    /**
     * 检查是否是流程定义
     */
    public boolean isProcess() {
        return contentType == ContentType.PROCESS;
    }
    
    /**
     * 检查是否是表单
     */
    public boolean isForm() {
        return contentType == ContentType.FORM;
    }
    
    /**
     * 检查是否是数据表
     */
    public boolean isDataTable() {
        return contentType == ContentType.DATA_TABLE;
    }
    
    /**
     * 检查是否是脚本
     */
    public boolean isScript() {
        return contentType == ContentType.SCRIPT;
    }
}
