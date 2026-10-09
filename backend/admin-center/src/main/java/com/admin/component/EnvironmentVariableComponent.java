package com.admin.component;

import com.admin.dto.EnvironmentVariableRequest;
import com.admin.dto.EnvironmentVariableResponse;
import com.admin.enums.EnvironmentValueKind;

import java.util.List;

public interface EnvironmentVariableComponent {

    List<EnvironmentVariableResponse> list(EnvironmentValueKind kind);

    EnvironmentVariableResponse getById(String id);

    EnvironmentVariableResponse create(EnvironmentVariableRequest request, String userId);

    EnvironmentVariableResponse update(String id, EnvironmentVariableRequest request, String userId);

    void delete(String id);

    /** Resolved TEXT value or Vault {@code data.password} for the current deploy_env. */
    String resolveSecretOrText(String varKey);

    /** Mailbox password: {@code varKey} must be VAULT in the current deploy_env. */
    String resolveVaultPassword(String varKey);

    /** Current or default TEXT value. Rejects VAULT keys so mail cannot read secrets. */
    String resolveTextValue(String varKey);
}
