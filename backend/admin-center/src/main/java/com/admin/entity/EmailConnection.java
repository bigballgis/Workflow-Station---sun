package com.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "sys_email_connections")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class EmailConnection {

    @Id
    @Column(length = 64)
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "connection_type", nullable = false, length = 30)
    @Builder.Default
    private String connectionType = "SMTP";

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "username")
    private String username;

    @JsonIgnore
    @Column(name = "password_encrypted", columnDefinition = "TEXT")
    private String passwordEncrypted;

    @Column(name = "from_email", nullable = false)
    private String fromEmail;

    @Column(name = "from_name", length = 100)
    private String fromName;

    @Column(name = "use_tls")
    @Builder.Default
    private Boolean useTls = true;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "synced_at")
    private Instant syncedAt;
}
