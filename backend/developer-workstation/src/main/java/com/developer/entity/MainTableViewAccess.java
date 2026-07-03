package com.developer.entity;

import com.developer.enums.MainTableViewAccessTargetType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "dw_main_table_view_access",
        uniqueConstraints = @UniqueConstraint(columnNames = {"view_config_id", "target_type", "target_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class MainTableViewAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "view_config_id", nullable = false)
    private MainTableViewConfig viewConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private MainTableViewAccessTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;
}
