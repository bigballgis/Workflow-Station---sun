package com.admin.bi.component;

import com.admin.bi.dto.response.*;
import java.util.List;

public interface BiAssignmentConfigurationComponent {
    DashboardAssignmentResponse setAudienceEnabled(String id, boolean enabled);
    DataViewAssignmentResponse setDataViewEnabled(String id, boolean enabled);
    List<DataViewOptionResponse> listViews(Long functionUnitId, Long tableId);
}
