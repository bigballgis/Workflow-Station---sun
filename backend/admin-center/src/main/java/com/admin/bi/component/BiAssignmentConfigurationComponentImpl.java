package com.admin.bi.component;

import com.admin.bi.dto.response.*;
import com.admin.bi.service.BiDashboardAssignmentService;
import com.admin.bi.service.BiDataViewAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BiAssignmentConfigurationComponentImpl implements BiAssignmentConfigurationComponent {
    private final BiAdminAccessGuard guard;
    private final BiDashboardAssignmentService audience;
    private final BiDataViewAssignmentService dataView;

    public DashboardAssignmentResponse setAudienceEnabled(String id, boolean enabled) {
        guard.requireAdminUserId();
        return audience.updateAssignmentEnabled(id, enabled);
    }

    public DataViewAssignmentResponse setDataViewEnabled(String id, boolean enabled) {
        guard.requireAdminUserId();
        return dataView.updateEnabled(id, enabled);
    }

    public List<DataViewOptionResponse> listViews(Long functionUnitId, Long tableId) {
        guard.requireAdminUserId();
        return dataView.listViews(functionUnitId, tableId);
    }
}
