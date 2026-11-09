package com.admin.bi.service;

import com.admin.bi.dto.request.DataViewAssignmentRequest;
import com.admin.bi.dto.request.DataViewAssignmentBatchRequest;
import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.bi.dto.response.DataViewDashboardResponse;
import com.admin.bi.dto.response.DataViewFunctionUnitOptionResponse;
import com.admin.bi.dto.response.DataViewTableOptionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BiDataViewAssignmentService {
    DataViewAssignmentResponse create(DataViewAssignmentRequest request);

    List<DataViewAssignmentResponse> createBatch(DataViewAssignmentBatchRequest request);

    Page<DataViewAssignmentResponse> list(String dashboardTitle, String functionUnitCode, Boolean enabled, String viewName, Pageable pageable);

    DataViewAssignmentResponse update(String id, DataViewAssignmentRequest request);

    void delete(String id);

    List<DataViewFunctionUnitOptionResponse> listFunctionUnits();

    List<DataViewTableOptionResponse> listTables(String functionUnitCode);

    List<com.admin.bi.dto.response.DataViewOptionResponse> listViews(String functionUnitCode, String tableName);

    List<DataViewDashboardResponse> getDashboardsForView(String userId, String functionUnitCode, String viewName);

    boolean canAccessDashboardForView(String userId, String dashboardId, String functionUnitCode, String viewName);
    DataViewAssignmentResponse updateEnabled(String id, boolean enabled);
}
