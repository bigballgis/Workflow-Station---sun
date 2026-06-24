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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

        List<String> engineIds = new ArrayList<>(instances.size());
        for (ProcessInstance pi : instances) {
            engineIds.add(pi.getProcessInstanceId() != null && !pi.getProcessInstanceId().isEmpty()
                    ? pi.getProcessInstanceId()
                    : pi.getId());
        }

        // Fan out the engine purges concurrently (external HTTP); previously one serial round-trip per
        // instance made purging N instances an O(N) blocking call. DB deletes stay below on the @Transactional
        // thread (JPA writes must not run on a pool thread that lacks the transaction context).
        int engineOk = 0;
        if (!engineIds.isEmpty()) {
            List<CompletableFuture<Boolean>> purgeFutures = new ArrayList<>(engineIds.size());
            for (String engineId : engineIds) {
                purgeFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return workflowEngineClient.purgeProcessInstance(engineId);
                    } catch (Exception e) {
                        log.warn("Engine purge failed for instance {}: {}", engineId, e.getMessage());
                        return false;
                    }
                }));
            }
            for (CompletableFuture<Boolean> f : purgeFutures) {
                if (Boolean.TRUE.equals(f.join())) {
                    engineOk++;
                }
            }
        }

        // Batch the portal-side history deletes (one statement each instead of one per instance).
        if (!engineIds.isEmpty()) {
            changeHistoryRepository.deleteByProcessInstanceIdIn(engineIds);
            processHistoryRepository.deleteByProcessInstanceIdIn(engineIds);
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
