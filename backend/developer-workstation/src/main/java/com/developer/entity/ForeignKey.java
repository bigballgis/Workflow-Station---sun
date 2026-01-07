package com.developer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * 外键定义实体
 */
@Entity
@Table(name = "dw_foreign_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ForeignKey {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private TableDefinition tableDefinition;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private FieldDefinition fieldDefinition;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ref_table_id", nullable = false)
    private TableDefinition refTableDefinition;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ref_field_id", nullable = false)
    private FieldDefinition refFieldDefinition;
    
    @Column(name = "on_delete", length = 20)
    @Builder.Default
    private String onDelete = "NO ACTION";
    
    @Column(name = "on_update", length = 20)
    @Builder.Default
    private String onUpdate = "NO ACTION";
}
