package com.portal.service;

import com.portal.dto.MainTableViewPortalDtos.FunctionUnitViewMenuItem;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewDataPage;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewSummary;
import com.portal.dto.MainTableViewQueryRequest;

import java.util.List;

public interface PortalMainTableViewService {

    List<FunctionUnitViewMenuItem> listAccessibleFunctionUnits(String userId);

    List<MainTableViewSummary> listPublishedViews(String userId, String functionUnitCode);

    MainTableViewDataPage queryViewData(String userId, Long viewId, MainTableViewQueryRequest request);

    /** Whether this accessible View contains a row of the target request, using its designed scope. */
    boolean containsProcess(String userId, Long viewId, String processInstanceId);

    /** Exports the rows the same request would list, ignoring only its paging. */
    byte[] exportViewCsv(String userId, Long viewId, int maxRows, MainTableViewQueryRequest request);

}
