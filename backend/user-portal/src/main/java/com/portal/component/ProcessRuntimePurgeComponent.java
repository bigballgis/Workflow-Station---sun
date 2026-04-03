package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按功能单元目录 ID 清理门户运行数据并请求引擎删除运行中/历史实例
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessRuntimePurgeComponent {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final WorkflowEngineClient workflowEngineClient;

    @Transactional
    public Map<String, Object> purgeByCatalogId(String catalogId) {
        List<ProcessInstance> instances = processInstanceRepository.findByFunctionUnitCatalogId(catalogId);
        int engineOk = 0;
        for (ProcessInstance pi : instances) {
            String engineId = pi.getProcessInstanceId() != null && !pi.getProcessInstanceId().isEmpty()
                    ? pi.getProcessInstanceId()
                    : pi.getId();
            try {
                if (workflowEngineClient.purgeProcessInstance(engineId)) {
                    engineOk++;
                }
            } catch (Exception e) {
                log.warn("Engine purge failed for instance {}: {}", engineId, e.getMessage());
            }
            changeHistoryRepository.deleteByProcessInstanceId(engineId);
            processHistoryRepository.deleteByProcessInstanceId(engineId);
        }
        processInstanceRepository.deleteAll(instances);
        Map<String, Object> result = new HashMap<>();
        result.put("catalogId", catalogId);
        result.put("portalInstancesRemoved", instances.size());
        result.put("enginePurged", engineOk);
        log.info("Purge by catalog {}: portalRows={}, enginePurged={}", catalogId, instances.size(), engineOk);
        return result;
    }
}
