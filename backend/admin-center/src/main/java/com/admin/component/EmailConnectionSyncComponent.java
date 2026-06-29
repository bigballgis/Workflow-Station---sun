package com.admin.component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EmailConnectionSyncComponent {

    void syncConnections(String functionUnitId, List<Map<String, Object>> connections);

    Optional<Map<String, Object>> getCredentials(String functionUnitId, String connectionId);

    Optional<String> resolveFunctionUnitIdByCode(String functionUnitCode);
}
