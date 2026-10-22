package com.portal.component;

import com.platform.common.functionunit.CatalogRuntimeRows;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.portal.dto.ActionTableRowsDTO;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Caller authorizes the task/process; row selection never depends on a live DW binding. */
@Component
@RequiredArgsConstructor
public class ActionTableReadComponent {
    private final ProcessInstanceRepository instances;
    private final CatalogSnapshotReader snapshots;
    private final CatalogRuntimeRows rows;

    public List<ActionTableRowsDTO> getActionTableRows(TaskInfo task) {
        return getActionTableRows(task.getProcessInstanceId());
    }

    public List<ActionTableRowsDTO> getActionTableRows(String processId) {
        var instance = instances.findById(processId)
                .orElseThrow(() -> new PortalException("404", "Process instance not found"));
        String catalogId = ProcessVersionContext.of(instance).catalogId();
        List<ActionTableRowsDTO> result = new ArrayList<>();
        for (var form : snapshots.contents(catalogId, "FORM")) {
            for (var binding : snapshots.formBindings(catalogId, form)) {
                if (!"ACTION".equals(binding.get("bindingType"))) continue;
                var data = rows.rows(catalogId, String.valueOf(binding.get("tableId")), processId);
                if (!data.isEmpty()) result.add(new ActionTableRowsDTO(Long.valueOf(String.valueOf(binding.get("bindingId"))), data));
            }
        }
        return result;
    }
}
