package com.developer.component;

import com.developer.dto.EmailConnectionRequest;
import com.developer.dto.EmailConnectionResponse;
import com.developer.entity.EmailConnection;

import java.util.List;
import java.util.Map;

public interface EmailConnectionComponent {

    List<EmailConnectionResponse> listByFunctionUnitId(Long functionUnitId);

    EmailConnectionResponse getById(Long functionUnitId, Long connectionId);

    EmailConnectionResponse create(Long functionUnitId, EmailConnectionRequest request);

    EmailConnectionResponse update(Long functionUnitId, Long connectionId, EmailConnectionRequest request);

    void delete(Long functionUnitId, Long connectionId);

    Map<String, Object> testConnection(Long functionUnitId, Long connectionId, String testRecipient);
}
