package com.admin.entity;

import com.admin.enums.EnvironmentValueKind;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "ac_environment_variables", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ac_env_var_key_env", columnNames = {"var_key", "deploy_env"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class EnvironmentVariable {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "var_key", nullable = false, length = 100)
    private String varKey;

    @Column(name = "deploy_env", nullable = false, length = 16)
    private String deployEnv;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_kind", nullable = false, length = 16)
    private EnvironmentValueKind valueKind;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "current_value", columnDefinition = "TEXT")
    private String currentValue;

    @Column(name = "vault_secret_path", length = 512)
    private String vaultSecretPath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
