package com.portal.component;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Administrator identity bridge for general process access. View grants are owned by Admin Center. */
@Component
@RequiredArgsConstructor
public class MainTableViewAccessResolver {
    private final com.portal.client.PortalBiClient dataViewClient;

    public boolean isSystemAdministrator(String userId) {
        return userId != null && !userId.isBlank() && dataViewClient.isDataViewAdministrator(userId);
    }
}
