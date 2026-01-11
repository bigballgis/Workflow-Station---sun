package com.admin.entity;

import com.admin.enums.DictionaryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据字典项实体
 */
@Entity
@Table(name = "sys_dictionary_items", indexes = {
        @Index(name = "idx_dict_item_dict_id", columnList = "dictionary_id"),
        @Index(name = "idx_dict_item_code", columnList = "dictionary_id, item_code"),
        @Index(name = "idx_dict_item_parent", columnList = "parent_id"),
        @Index(name = "idx_dict_item_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DictionaryItem {
    
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 所属字典
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dictionary_id", nullable = false)
    private Dictionary dictionary;
    
    /**
     * 父级字典项（支持层级结构）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private DictionaryItem parent;
    
    /**
     * 子级字典项
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<DictionaryItem> children = new ArrayList<>();
    
    /**
     * 字典项代码
     */
    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    
    /**
     * 字典项名称（默认语言）
     */
    @Column(nullable = false, length = 200)
    private String name;
    
    /**
     * 英文名称
     */
    @Column(length = 200)
    private String nameEn;
    
    /**
     * 中文简体名称
     */
    @Column(length = 200)
    private String nameZhCn;
    
    /**
     * 中文繁体名称
     */
    @Column(length = 200)
    private String nameZhTw;
    
    /**
     * 字典项值
     */
    @Column(length = 500)
    private String value;
    
    /**
     * 字典项描述
     */
    @Column(length = 500)
    private String description;
    
    /**
     * 状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DictionaryStatus status;
    
    /**
     * 排序号
     */
    @Column
    @Builder.Default
    private Integer sortOrder = 0;
    
    /**
     * 有效期开始时间
     */
    @Column
    private Instant validFrom;
    
    /**
     * 有效期结束时间
     */
    @Column
    private Instant validTo;
    
    /**
     * 扩展属性（JSON格式）
     */
    @Column(columnDefinition = "TEXT")
    private String extAttributes;
    
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
    
    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
    
    /**
     * 更新者
     */
    @Column(length = 36)
    private String updatedBy;
    
    /**
     * 获取指定语言的名称
     */
    public String getNameByLanguage(String language) {
        return switch (language) {
            case "en" -> nameEn != null ? nameEn : name;
            case "zh-CN" -> nameZhCn != null ? nameZhCn : name;
            case "zh-TW" -> nameZhTw != null ? nameZhTw : name;
            default -> name;
        };
    }
    
    /**
     * 是否在有效期内
     */
    public boolean isValid() {
        if (status != DictionaryStatus.ACTIVE) {
            return false;
        }
        Instant now = Instant.now();
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        if (validTo != null && now.isAfter(validTo)) {
            return false;
        }
        return true;
    }
    
    /**
     * 是否有子项
     */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
}
