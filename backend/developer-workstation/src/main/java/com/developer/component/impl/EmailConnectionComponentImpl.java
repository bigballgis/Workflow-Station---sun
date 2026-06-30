package com.developer.component.impl;

import com.developer.component.EmailConnectionComponent;
import com.developer.dto.EmailConnectionRequest;
import com.developer.dto.EmailConnectionResponse;
import com.developer.entity.EmailConnection;
import com.developer.entity.FunctionUnit;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.EmailConnectionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.util.EmailProviderPreset;
import com.developer.util.SmtpMailSender;
import com.platform.common.security.SsrfProtection;
import com.platform.security.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailConnectionComponentImpl implements EmailConnectionComponent {

    private final EmailConnectionRepository emailConnectionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final EncryptionService encryptionService;

    /** Corporate SMTP relays / internal hosts permitted (same config as webhook SSRF allowlist). */
    @Value("${ssrf.allowed-hosts:localhost,n8n}")
    private List<String> ssrfAllowedHosts;

    @Override
    @Transactional(readOnly = true)
    public List<EmailConnectionResponse> listByFunctionUnitId(Long functionUnitId) {
        ensureFunctionUnitExists(functionUnitId);
        return emailConnectionRepository.findByFunctionUnitIdOrderByNameAsc(functionUnitId).stream()
                .map(EmailConnectionResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmailConnectionResponse getById(Long functionUnitId, Long connectionId) {
        return EmailConnectionResponse.fromEntity(getEntity(functionUnitId, connectionId));
    }

    @Override
    @Transactional
    public EmailConnectionResponse create(Long functionUnitId, EmailConnectionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        if (emailConnectionRepository.existsByFunctionUnitIdAndName(functionUnitId, request.getName())) {
            throw new DeveloperBusinessException("CONFLICT_CONNECTION_NAME", "连接名称已存在: " + request.getName());
        }

        if (!StringUtils.hasText(request.getPassword())) {
            throw new DeveloperBusinessException("VALIDATION_PASSWORD_REQUIRED", "创建连接时必须提供密码");
        }

        ResolvedSmtpEndpoint endpoint = resolveSmtpEndpoint(request, null);
        String emailAddress = request.getName().trim();
        String username = StringUtils.hasText(request.getUsername()) ? request.getUsername().trim() : emailAddress;

        EmailConnection connection = EmailConnection.builder()
                .connectionUid(UUID.randomUUID().toString())
                .functionUnit(functionUnit)
                .name(emailAddress)
                .connectionType(request.getConnectionType())
                .host(endpoint.host())
                .port(endpoint.port())
                .username(username)
                .passwordEncrypted(encryptionService.encrypt(request.getPassword()))
                .fromEmail(emailAddress)
                .fromName(request.getFromName())
                .useTls(endpoint.useTls())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .direction(request.getDirection() != null
                        ? request.getDirection() : com.developer.enums.EmailConnectionDirection.OUTBOUND)
                .mailboxAddress(request.getMailboxAddress())
                .build();

        return EmailConnectionResponse.fromEntity(emailConnectionRepository.save(connection));
    }

    @Override
    @Transactional
    public EmailConnectionResponse update(Long functionUnitId, Long connectionId, EmailConnectionRequest request) {
        EmailConnection connection = getEntity(functionUnitId, connectionId);

        if (emailConnectionRepository.existsByFunctionUnitIdAndNameAndIdNot(
                functionUnitId, request.getName(), connectionId)) {
            throw new DeveloperBusinessException("CONFLICT_CONNECTION_NAME", "连接名称已存在: " + request.getName());
        }

        ResolvedSmtpEndpoint endpoint = resolveSmtpEndpoint(request, connection);
        String emailAddress = request.getName().trim();
        String username = StringUtils.hasText(request.getUsername()) ? request.getUsername().trim() : emailAddress;

        connection.setName(emailAddress);
        connection.setConnectionType(request.getConnectionType());
        connection.setHost(endpoint.host());
        connection.setPort(endpoint.port());
        connection.setUsername(username);
        connection.setFromEmail(emailAddress);
        connection.setFromName(request.getFromName());
        connection.setUseTls(endpoint.useTls());
        if (request.getEnabled() != null) {
            connection.setEnabled(request.getEnabled());
        }
        if (request.getDirection() != null) {
            connection.setDirection(request.getDirection());
        }
        connection.setMailboxAddress(request.getMailboxAddress());
        if (StringUtils.hasText(request.getPassword())) {
            connection.setPasswordEncrypted(encryptionService.encrypt(request.getPassword()));
        }

        return EmailConnectionResponse.fromEntity(emailConnectionRepository.save(connection));
    }

    @Override
    @Transactional
    public void delete(Long functionUnitId, Long connectionId) {
        emailConnectionRepository.delete(getEntity(functionUnitId, connectionId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> testConnection(Long functionUnitId, Long connectionId, String testRecipient) {
        EmailConnection connection = getEntity(functionUnitId, connectionId);
        if (!StringUtils.hasText(testRecipient)) {
            throw new DeveloperBusinessException("VALIDATION_RECIPIENT_REQUIRED", "测试收件人不能为空");
        }

        Map<String, Object> result = new HashMap<>();
        try {
            validateSmtpHost(connection.getHost());
            SmtpMailSender.SmtpConfig config = toSmtpConfig(connection);
            SmtpMailSender.send(config, testRecipient, null,
                    "Workflow Platform - Connection Test",
                    "<p>This is a test email from connection <strong>" + connection.getName() + "</strong>.</p>");
            result.put("success", true);
            result.put("message", "测试邮件发送成功");
        } catch (Exception e) {
            log.error("Connection test failed for {}: {}", connectionId, e.getMessage());
            result.put("success", false);
            result.put("message", "测试失败: " + e.getMessage());
        }
        return result;
    }

    private void validateSmtpHost(String host) {
        try {
            SsrfProtection.validateHostname(host, allowedSmtpHosts());
        } catch (SsrfProtection.SsrfException ex) {
            throw new DeveloperBusinessException("SSRF_SMTP_HOST_BLOCKED", ex.getMessage());
        }
    }

    private Set<String> allowedSmtpHosts() {
        if (ssrfAllowedHosts == null || ssrfAllowedHosts.isEmpty()) {
            return Set.of();
        }
        return ssrfAllowedHosts.stream()
                .filter(h -> h != null && !h.isBlank())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private ResolvedSmtpEndpoint resolveSmtpEndpoint(EmailConnectionRequest request, EmailConnection existing) {
        EmailProviderPreset preset = EmailProviderPreset.forType(request.getConnectionType());

        String host = StringUtils.hasText(request.getHost())
                ? request.getHost().trim()
                : (existing != null ? existing.getHost() : null);
        if (!StringUtils.hasText(host)) {
            host = preset.host();
        }
        if (!StringUtils.hasText(host)) {
            throw new DeveloperBusinessException("VALIDATION_SMTP_HOST_REQUIRED", "SMTP 主机不能为空");
        }

        int port;
        if (request.getPort() != null && request.getPort() > 0) {
            port = request.getPort();
        } else if (existing != null && existing.getPort() != null && existing.getPort() > 0) {
            port = existing.getPort();
        } else {
            port = preset.port();
        }

        boolean useTls;
        if (request.getUseTls() != null) {
            useTls = Boolean.TRUE.equals(request.getUseTls());
        } else if (existing != null && existing.getUseTls() != null) {
            useTls = existing.getUseTls();
        } else {
            useTls = preset.useTls();
        }

        validateSmtpHost(host);
        return new ResolvedSmtpEndpoint(host, port, useTls);
    }

    private void ensureFunctionUnitExists(Long functionUnitId) {
        if (!functionUnitRepository.existsById(functionUnitId)) {
            throw new ResourceNotFoundException("FunctionUnit", functionUnitId);
        }
    }

    private EmailConnection getEntity(Long functionUnitId, Long connectionId) {
        EmailConnection connection = emailConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailConnection", connectionId));
        if (!connection.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new ResourceNotFoundException("EmailConnection", connectionId);
        }
        return connection;
    }

    private record ResolvedSmtpEndpoint(String host, int port, boolean useTls) {}

    private SmtpMailSender.SmtpConfig toSmtpConfig(EmailConnection connection) {
        String password = null;
        if (connection.getPasswordEncrypted() != null) {
            password = encryptionService.decrypt(connection.getPasswordEncrypted());
        }
        return new SmtpMailSender.SmtpConfig(
                connection.getHost(),
                connection.getPort(),
                connection.getUsername(),
                password,
                connection.getFromEmail(),
                connection.getFromName(),
                connection.getUseTls()
        );
    }
}
