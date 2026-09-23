package com.admin.component;

import com.admin.dto.EnvironmentVariableRequest;
import com.admin.dto.EnvironmentVariableResponse;
import com.admin.entity.EnvironmentVariable;
import com.admin.enums.EnvironmentValueKind;
import com.admin.environment.DeployProfileMapper;
import com.admin.environment.EnvironmentVariablePayloads;
import com.admin.repository.EnvironmentVariableRepository;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ResourceNotFoundException;
import com.platform.security.vault.VaultSecretClient;
import com.platform.security.vault.VaultSecretNotFoundException;
import com.platform.security.vault.VaultSecretUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnvironmentVariableComponentImpl implements EnvironmentVariableComponent {

    private final EnvironmentVariableRepository repository;
    private final VaultSecretClient vaultSecretClient;
    private final Environment springEnvironment;

    @Override
    @Transactional(readOnly = true)
    public List<EnvironmentVariableResponse> list(EnvironmentValueKind kind) {
        String env = currentDeployEnv();
        List<EnvironmentVariable> rows = kind == null
                ? repository.findByDeployEnvOrderByVarKeyAsc(env)
                : repository.findByDeployEnvAndValueKindOrderByVarKeyAsc(env, kind);
        return rows.stream().map(EnvironmentVariableResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EnvironmentVariableResponse getById(String id) {
        return EnvironmentVariableResponse.fromEntity(requireInCurrentDeployEnv(id));
    }

    @Override
    @Transactional
    public EnvironmentVariableResponse create(EnvironmentVariableRequest request, String userId) {
        EnvironmentVariablePayloads.validate(request);
        String env = currentDeployEnv();
        String key = request.getVarKey().trim();
        if (repository.existsByVarKeyAndDeployEnv(key, env)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Environment variable key already exists");
        }
        boolean wroteVault = writeVaultOnCreate(request);
        EnvironmentVariable entity = EnvironmentVariable.builder()
                .id(UUID.randomUUID().toString())
                .varKey(key)
                .deployEnv(env)
                .valueKind(request.getValueKind())
                .displayName(request.getDisplayName().trim())
                .description(blankToNull(request.getDescription()))
                .updatedBy(userId)
                .build();
        applyPayload(entity, request);
        return persistNew(entity, key, EnvironmentVariablePayloads.vaultPath(request), wroteVault);
    }

    @Override
    @Transactional
    public EnvironmentVariableResponse update(String id, EnvironmentVariableRequest request, String userId) {
        EnvironmentVariablePayloads.validate(request);
        EnvironmentVariable entity = requireInCurrentDeployEnv(id);
        String env = currentDeployEnv();
        String key = request.getVarKey().trim();
        repository.findByVarKeyAndDeployEnv(key, env)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Environment variable key already exists");
                });
        entity.setVarKey(key);
        entity.setValueKind(request.getValueKind());
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setDescription(blankToNull(request.getDescription()));
        entity.setUpdatedBy(userId);
        applyPayload(entity, request);
        return EnvironmentVariableResponse.fromEntity(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(String id) {
        repository.delete(requireInCurrentDeployEnv(id));
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveSecretOrText(String varKey) {
        EnvironmentVariable entity = requireByKey(varKey);
        if (entity.getValueKind() == EnvironmentValueKind.TEXT) {
            return EnvironmentVariablePayloads.resolvedText(entity.getCurrentValue(), entity.getDefaultValue());
        }
        return readVaultPassword(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveVaultPassword(String varKey) {
        EnvironmentVariable entity = requireByKey(varKey);
        if (entity.getValueKind() != EnvironmentValueKind.VAULT) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                    "Email passwords must reference a VAULT environment variable");
        }
        return readVaultPassword(entity);
    }

    private String readVaultPassword(EnvironmentVariable entity) {
        String varKey = entity.getVarKey();
        String path = entity.getVaultSecretPath();
        try {
            return vaultSecretClient.readPassword(path);
        } catch (VaultSecretNotFoundException ex) {
            log.warn("Vault secret not found varKey={} path={}", varKey, path);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Vault secret not found for environment variable " + varKey);
        } catch (VaultSecretUnavailableException ex) {
            log.warn("Vault unavailable varKey={} path={}: {}", varKey, path, ex.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Vault is unavailable. Check Vault connectivity and permissions.", ex);
        }
    }

    private boolean writeVaultOnCreate(EnvironmentVariableRequest request) {
        if (request.getValueKind() != EnvironmentValueKind.VAULT) {
            return false;
        }
        String path = EnvironmentVariablePayloads.vaultPath(request);
        String varKey = request.getVarKey().trim();
        try {
            return writeNewVaultSecret(varKey, path, request.getVaultPassword());
        } catch (BusinessException ex) {
            throw ex;
        } catch (VaultSecretNotFoundException ex) {
            log.warn("Vault path invalid varKey={} path={}", varKey, path);
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                    "Vault secret path is invalid", ex);
        } catch (VaultSecretUnavailableException ex) {
            log.warn("Vault write failed varKey={} path={}: {}", varKey, path, ex.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Could not write the secret to Vault. Check Vault connectivity and permissions.", ex);
        }
    }

    private boolean writeNewVaultSecret(String varKey, String path, String password) {
        if (vaultSecretClient.secretExists(path)) {
            log.info("Reusing existing Vault secret varKey={} path={}", varKey, path);
            return false;
        }
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED,
                    "VAULT environment variables require vaultPassword");
        }
        vaultSecretClient.writePassword(path, password);
        log.info("Wrote Vault secret varKey={} path={}", varKey, path);
        return true;
    }

    private EnvironmentVariableResponse persistNew(
            EnvironmentVariable entity, String varKey, String path, boolean wroteVault) {
        try {
            return EnvironmentVariableResponse.fromEntity(repository.save(entity));
        } catch (RuntimeException ex) {
            if (wroteVault) {
                log.error("Catalog save failed after Vault write varKey={} path={}", varKey, path);
            }
            throw ex;
        }
    }

    private EnvironmentVariable require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EnvironmentVariable", id));
    }

    private EnvironmentVariable requireInCurrentDeployEnv(String id) {
        EnvironmentVariable entity = require(id);
        if (!currentDeployEnv().equals(entity.getDeployEnv())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot access an environment variable for another deploy_env");
        }
        return entity;
    }

    private EnvironmentVariable requireByKey(String varKey) {
        if (!StringUtils.hasText(varKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED,
                    "Environment variable key is required");
        }
        return repository.findByVarKeyAndDeployEnv(varKey.trim(), currentDeployEnv())
                .orElseThrow(() -> new ResourceNotFoundException("EnvironmentVariable", varKey));
    }

    private String currentDeployEnv() {
        return DeployProfileMapper.toDeployEnv(String.join(",", springEnvironment.getActiveProfiles()));
    }

    private static void applyPayload(EnvironmentVariable entity, EnvironmentVariableRequest request) {
        if (request.getValueKind() == EnvironmentValueKind.TEXT) {
            entity.setDefaultValue(request.getDefaultValue().trim());
            entity.setCurrentValue(StringUtils.hasText(request.getCurrentValue())
                    ? request.getCurrentValue().trim() : null);
            entity.setVaultSecretPath(null);
            return;
        }
        entity.setDefaultValue(null);
        entity.setCurrentValue(null);
        entity.setVaultSecretPath(EnvironmentVariablePayloads.vaultPath(request));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
