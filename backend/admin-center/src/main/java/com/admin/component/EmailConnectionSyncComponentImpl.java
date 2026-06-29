package com.admin.component;

import com.admin.entity.EmailConnection;
import com.admin.entity.FunctionUnit;
import com.admin.repository.EmailConnectionRepository;
import com.admin.repository.FunctionUnitRepository;
import com.platform.security.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConnectionSyncComponentImpl implements EmailConnectionSyncComponent {

    private final EmailConnectionRepository emailConnectionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final EncryptionService encryptionService;

    @Override
    @Transactional
    public void syncConnections(String functionUnitId, List<Map<String, Object>> connections) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Function unit not found: " + functionUnitId));

        emailConnectionRepository.deleteByFunctionUnitId(functionUnitId);

        if (connections == null || connections.isEmpty()) {
            log.info("No email connections to sync for function unit {}", functionUnitId);
            return;
        }

        for (Map<String, Object> conn : connections) {
            String connectionUid = String.valueOf(conn.get("connectionUid"));
            EmailConnection entity = EmailConnection.builder()
                    .id(connectionUid)
                    .functionUnit(functionUnit)
                    .name((String) conn.get("name"))
                    .connectionType(conn.get("connectionType") != null ? conn.get("connectionType").toString() : "SMTP")
                    .host((String) conn.get("host"))
                    .port(conn.get("port") != null ? ((Number) conn.get("port")).intValue() : 587)
                    .username((String) conn.get("username"))
                    .passwordEncrypted((String) conn.get("passwordEncrypted"))
                    .fromEmail((String) conn.get("fromEmail"))
                    .fromName((String) conn.get("fromName"))
                    .useTls(conn.get("useTls") != null ? (Boolean) conn.get("useTls") : true)
                    .enabled(conn.get("enabled") != null ? (Boolean) conn.get("enabled") : true)
                    .syncedAt(Instant.now())
                    .build();
            emailConnectionRepository.save(entity);
        }

        log.info("Synced {} email connections for function unit {}", connections.size(), functionUnitId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getCredentials(String functionUnitId, String connectionId) {
        return emailConnectionRepository.findByFunctionUnitIdAndId(functionUnitId, connectionId)
                .filter(EmailConnection::getEnabled)
                .map(conn -> {
                    Map<String, Object> creds = new HashMap<>();
                    creds.put("connectionId", conn.getId());
                    creds.put("connectionType", conn.getConnectionType());
                    creds.put("host", conn.getHost());
                    creds.put("port", conn.getPort());
                    creds.put("username", conn.getUsername());
                    creds.put("fromEmail", conn.getFromEmail());
                    creds.put("fromName", conn.getFromName());
                    creds.put("useTls", conn.getUseTls());
                    if (conn.getPasswordEncrypted() != null) {
                        creds.put("password", encryptionService.decrypt(conn.getPasswordEncrypted()));
                    }
                    return creds;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> resolveFunctionUnitIdByCode(String functionUnitCode) {
        List<FunctionUnit> units = functionUnitRepository.findByCodeOrderByVersionDesc(functionUnitCode);
        if (units.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(units.get(0).getId());
    }
}
