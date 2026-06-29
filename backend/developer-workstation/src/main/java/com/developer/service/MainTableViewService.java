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

    /**
     * Propagate Table Design field changes into this table's view configs: rename a view field when its
     * field name changed, and refresh its column label when the field's display name changed (only when
     * the label still matches the old display name, so manual label tweaks are preserved).
     */
    void propagateFieldChangesToViews(Long tableId, List<FieldLabelChange> changes);

    /** A single field's old/new name + display-name, derived from a Table Design save. */
    record FieldLabelChange(String oldFieldName, String newFieldName,
                            String oldDisplayName, String newDisplayName) {}

    void publishViewsForFunctionUnit(Long functionUnitId);

    List<Map<String, Object>> snapshotViewsForFunctionUnit(Long functionUnitId);

    void cloneViewsForFunctionUnit(Long sourceFunctionUnitId, FunctionUnit targetFunctionUnit,
                                   Map<Long, TableDefinition> tableIdMapping);
}
