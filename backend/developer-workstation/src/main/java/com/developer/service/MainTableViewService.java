package com.developer.service;

import com.developer.dto.MainTableViewDtos.CreateMainTableViewRequest;
import com.developer.dto.MainTableViewDtos.MainTableViewDTO;
import com.developer.dto.MainTableViewDtos.UpdateMainTableViewRequest;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;

import java.util.List;
import java.util.Map;

public interface MainTableViewService {

    List<MainTableViewDTO> listViews(Long functionUnitId);

    MainTableViewDTO getView(Long functionUnitId, Long viewId);

    MainTableViewDTO createView(Long functionUnitId, CreateMainTableViewRequest request);

    MainTableViewDTO updateView(Long functionUnitId, Long viewId, UpdateMainTableViewRequest request);

    void deleteView(Long functionUnitId, Long viewId);

    void seedDefaultViewIfAbsent(Long functionUnitId, Long tableId);

    void seedDefaultViewsForFunctionUnit(Long functionUnitId);

    void publishViewsForFunctionUnit(Long functionUnitId);

    List<Map<String, Object>> snapshotViewsForFunctionUnit(Long functionUnitId);

    void cloneViewsForFunctionUnit(Long sourceFunctionUnitId, FunctionUnit targetFunctionUnit,
                                   Map<Long, TableDefinition> tableIdMapping);
}
