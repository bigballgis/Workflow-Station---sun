package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.MiOverlaySupport.MiRowProgress;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.ProcessAssigneeSnapshot;
import com.portal.service.UserDisplayNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * "My applications" list / process detail queries and participant checks.
 * Extracted from {@link ProcessComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessApplicationQueryComponent {

    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final EngineSubTableHydrator engineSubTableHydrator;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final MiOverlayComponent miOverlayComponent;
    private final SubTableEnrichmentComponent subTableEnrichmentComponent;
    private final RequestIdEnricher requestIdEnricher;

    /**
     * For running processes with incomplete local assignee data, backfill user/candidate ids from engine and persist.
     *
     * <p>The per-instance engine task lookup is a network round-trip; doing them sequentially makes the
     * "my applications" list O(N) HTTP calls in series (slow when a page has many running rows). We fan the
     * read-only fetches out concurrently, then apply the mutations + saves on the calling thread so JPA
     * writes keep their original single-threaded semantics.</p>
     */
    private void enrichRunningAssigneesFromEngine(List<ProcessInstance> instances) {
        if (instances == null || instances.isEmpty() || !workflowEngineClient.isAvailable()) {
            return;
        }
        List<ProcessInstance> needEnrich = instances.stream()
                .filter(inst -> inst != null && "RUNNING".equals(inst.getStatus()))
                .filter(this::needsAssigneeEnrichment)
                .toList();
        if (needEnrich.isEmpty()) {
            return;
        }

        // Fan out the read-only engine lookups concurrently.
        Map<ProcessInstance, CompletableFuture<Optional<ProcessAssigneeSnapshot>>> futures = new LinkedHashMap<>();
        for (ProcessInstance instance : needEnrich) {
            futures.put(instance, CompletableFuture.supplyAsync(() -> fetchAssigneeSnapshot(instance.getId())));
        }

        // Apply mutations + persist on the calling thread (preserves prior write ordering / tx behavior).
        for (Map.Entry<ProcessInstance, CompletableFuture<Optional<ProcessAssigneeSnapshot>>> entry : futures.entrySet()) {
            ProcessInstance instance = entry.getKey();
            try {
                Optional<ProcessAssigneeSnapshot> snapshotOpt = entry.getValue().join();
                if (snapshotOpt.isEmpty()) {
                    continue;
                }
                ProcessAssigneeSnapshot snapshot = snapshotOpt.get();
                instance.setCurrentAssignee(snapshot.getAssigneeUserId());
                instance.setCandidateUsers(snapshot.getCandidateUserIds());
                processInstanceRepository.save(instance);
                log.debug("Enriched assignee snapshot for process {}: assignee={}, candidates={}",
                        instance.getId(), snapshot.getAssigneeUserId(), snapshot.getCandidateUserIds());
            } catch (Exception e) {
                log.warn("Failed to enrich assignee from engine for process {}: {}",
                        instance.getId(), e.getMessage());
            }
        }
    }

    /**
     * Read-only engine lookup of the current task assignee snapshot for a process instance.
     * Returns empty if the engine has no usable assignee/candidate data (caller skips the row).
     */
    private Optional<ProcessAssigneeSnapshot> fetchAssigneeSnapshot(String processInstanceId) {
        try {
            Optional<Map<String, Object>> tasksResult =
                    workflowEngineClient.getProcessInstanceTasks(processInstanceId);
            if (tasksResult.isEmpty() || tasksResult.get() == null) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksResult.get().get("tasks");
            if (tasks == null || tasks.isEmpty()) {
                return Optional.empty();
            }
            ProcessAssigneeSnapshot snapshot = ProcessAssigneeSnapshot.fromEngineTask(tasks.get(0));
            if (snapshot.getAssigneeUserId() == null && snapshot.getCandidateUserIds() == null) {
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (Exception e) {
            log.warn("Failed to fetch assignee snapshot from engine for process {}: {}",
                    processInstanceId, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean needsAssigneeEnrichment(ProcessInstance instance) {
        String assignee = instance.getCurrentAssignee();
        String candidates = instance.getCandidateUsers();
        if (candidates != null && !candidates.isBlank()) {
            return true;
        }
        if (assignee == null || assignee.isBlank()) {
            return true;
        }
        Map<String, String> probe = userDisplayNameResolver.resolveBatch(
                userDisplayNameResolver.collectAssigneeUserKeys(assignee, candidates));
        String display = userDisplayNameResolver.resolveCurrentAssigneeDisplay(assignee, candidates, probe);
        return display != null && display.equals(assignee.trim());
    }

    /**
     * Returns my applications list
     */
    public Page<ProcessInstanceInfo> getMyApplications(String userId, String status, Pageable pageable) {
        log.info("Getting applications for user: {}, status: {}", userId, status);

        Page<ProcessInstance> instancePage;
        if (status != null && !status.isEmpty()) {
            instancePage = processInstanceRepository.findByStartUserIdAndStatusOrderByStartTimeDesc(userId, status, pageable);
        } else {
            instancePage = processInstanceRepository.findByStartUserIdOrderByStartTimeDesc(userId, pageable);
        }

        List<ProcessInstance> pageContent = instancePage.getContent();
        enrichRunningAssigneesFromEngine(pageContent);

        Set<String> assigneeKeys = pageContent.stream()
                .flatMap(inst -> userDisplayNameResolver.collectAssigneeUserKeys(
                        inst.getCurrentAssignee(), inst.getCandidateUsers()).stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> userNameCache = userDisplayNameResolver.resolveBatch(assigneeKeys);

        // Resolve Request ID config once per function unit (no per-row DB hit).
        Set<String> functionUnitCodes = pageContent.stream()
                .map(ProcessInstance::getFunctionUnitCode)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        RequestIdEnricher.SpecCache requestIdSpecs = requestIdEnricher.resolveSpecs(functionUnitCodes);

        // List API omits variables: JSONB may contain Jackson-unfriendly nesting → HttpMessageNotWritableException → SYS_INTERNAL_ERROR
        // Request ID is computed from the (still present) variables before they are nulled.
        List<ProcessInstanceInfo> instances = pageContent.stream()
                .map(inst -> toProcessInstanceInfoForList(inst, userNameCache))
                .peek(info -> info.setRequestId(
                        requestIdEnricher.buildRequestId(requestIdSpecs, info.getFunctionUnitCode(), info.getVariables())))
                .peek(info -> info.setVariables(null))
                .toList();

        return new PageImpl<>(instances, pageable, instancePage.getTotalElements());
    }

    /**
     * Entity to DTO without cache (single process query)
     */
    private ProcessInstanceInfo toProcessInstanceInfo(ProcessInstance instance) {
        return toProcessInstanceInfo(instance, new HashMap<>());
    }

    /**
     * My requests list: do not call workflow-engine per row for runtime tasks (one HTTP per row is too slow).
     * Assignee display name from {@link ProcessInstance#getCurrentAssignee()} / {@link ProcessInstance#getCandidateUsers()}
     * via {@link UserDisplayNameResolver} (single name; BU/Role OR pool {@code name1, name2, name3}).
     */
    private ProcessInstanceInfo toProcessInstanceInfoForList(ProcessInstance instance, Map<String, String> userNameCache) {
        String currentAssigneeName = userDisplayNameResolver.resolveCurrentAssigneeDisplay(
                instance.getCurrentAssignee(), instance.getCandidateUsers(), userNameCache);

        log.debug("toProcessInstanceInfoForList: processId={}, status={}, assignee={}, candidates={}, display={}",
                instance.getId(), instance.getStatus(),
                instance.getCurrentAssignee(), instance.getCandidateUsers(), currentAssigneeName);

        String currentNode = instance.getCurrentNode();
        if ("COMPLETED".equals(instance.getStatus())) {
            currentNode = null;
        }

        return ProcessInstanceInfo.builder()
                .id(instance.getId())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionKey(instance.getProcessDefinitionKey())
                .processDefinitionName(instance.getProcessDefinitionName())
                .businessKey(instance.getBusinessKey())
                .startTime(instance.getStartTime())
                .endTime(instance.getEndTime())
                .completedAt(instance.getCompletedAt())
                .title(instance.getTitle())
                .status(instance.getStatus())
                .startUserId(instance.getStartUserId())
                .startUserName(instance.getStartUserName())
                .currentNode(currentNode)
                .currentAssignee(currentAssigneeName)
                .candidateUsers(instance.getCandidateUsers())
                .variables(instance.getVariables())
                .functionUnitCatalogId(instance.getFunctionUnitCatalogId())
                .functionUnitCode(instance.getFunctionUnitCode())
                .functionUnitVersionLabel(instance.getFunctionUnitVersionLabel())
                .build();
    }

    /**
     * Entity to DTO with method-level display-name cache (avoids list N+1)
     */
    private ProcessInstanceInfo toProcessInstanceInfo(ProcessInstance instance, Map<String, String> userNameCache) {
        String currentAssigneeName = userDisplayNameResolver.resolveCurrentAssigneeDisplay(
                instance.getCurrentAssignee(), instance.getCandidateUsers(), userNameCache);

        log.debug("toProcessInstanceInfo: processId={}, status={}, assignee={}, candidates={}, display={}",
                instance.getId(), instance.getStatus(),
                instance.getCurrentAssignee(), instance.getCandidateUsers(), currentAssigneeName);

        String currentNode = instance.getCurrentNode();
        if ("COMPLETED".equals(instance.getStatus())) {
            currentNode = null;
        }

        return ProcessInstanceInfo.builder()
                .id(instance.getId())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionKey(instance.getProcessDefinitionKey())
                .processDefinitionName(instance.getProcessDefinitionName())
                .businessKey(instance.getBusinessKey())
                .startTime(instance.getStartTime())
                .endTime(instance.getEndTime())
                .completedAt(instance.getCompletedAt())
                .title(instance.getTitle())
                .status(instance.getStatus())
                .startUserId(instance.getStartUserId())
                .startUserName(instance.getStartUserName())
                .currentNode(currentNode)
                .currentAssignee(currentAssigneeName)
                .candidateUsers(instance.getCandidateUsers())
                .variables(instance.getVariables())
                .functionUnitCatalogId(instance.getFunctionUnitCatalogId())
                .functionUnitCode(instance.getFunctionUnitCode())
                .functionUnitVersionLabel(instance.getFunctionUnitVersionLabel())
                .build();
    }

    /**
     * Returns process detail
     * When local DB lacks current node, fetch live from Flowable
     */
    /**
     * Gap-fills {@code __subTables__} slices from the live Flowable engine into the portal store when a
     * service task (e.g. Activepieces) produced rows the portal-written store never received. Only fills
     * slices the store is missing or has empty — user-submitted rows always win. Persists so a subsequent
     * task completion carries the rows rather than overwriting them with an empty grid. Best-effort.
     */
    @SuppressWarnings("unchecked")
    private void hydrateEngineSubTablesIntoStore(String processId, ProcessInstance instance, ProcessInstanceInfo info) {
        if (workflowEngineClient == null || !workflowEngineClient.isAvailable()) {
            return; // engine known-down: skip the read-path round-trip
        }
        Map<String, Object> vars = info.getVariables() != null
                ? new HashMap<>(info.getVariables()) : new HashMap<>();
        Map<String, Object> current = vars.get("__subTables__") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        engineSubTableHydrator.mergeFromEngine(processId, current).ifPresent(result -> {
            vars.put("__subTables__", result.mergedSubTables());
            if (result.rowCount() != null && vars.get("rowCount") == null) {
                vars.put("rowCount", result.rowCount());
            }
            info.setVariables(vars);
            instance.setVariables(vars);
            processInstanceRepository.save(instance);
            log.info("getProcessDetail: hydrated __subTables__ from engine for running process {}", processId);
        });
    }

    public ProcessInstanceInfo getProcessDetail(String processId) {
        Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processId);
        if (optInstance.isEmpty()) {
            return null;
        }

        ProcessInstance instance = optInstance.get();
        ProcessInstanceInfo info = toProcessInstanceInfo(instance);

        // Service-task outputs (e.g. an Activepieces task's __subTables__) live only in the Flowable
        // engine and are absent from the portal's up_process_instance store (written only by form
        // submissions). For a running instance, hydrate them so My Applications renders the rows and a
        // later completion persists them instead of overwriting with an empty grid.
        if ("RUNNING".equals(instance.getStatus())) {
            hydrateEngineSubTablesIntoStore(processId, instance, info);
        }

        // If the process is still running but the local DB has no currentNode stored,
        // try to fetch the live state from Flowable — this self-heals processes where
        // the auto-complete path set currentNode=null (e.g. no next task after initiator task).
        if ("RUNNING".equals(instance.getStatus()) &&
            (info.getCurrentNode() == null || info.getCurrentNode().isEmpty())) {
            try {
                if (workflowEngineClient.isAvailable()) {
                    Optional<Map<String, Object>> tasksResult = workflowEngineClient.getProcessInstanceTasks(processId);
                    if (tasksResult.isPresent()) {
                        Map<String, Object> tasksData = tasksResult.get();
                        if (tasksData != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
                            if (tasks != null && !tasks.isEmpty()) {
                                Map<String, Object> currentTask = tasks.get(0);
                                String currentNodeName = (String) currentTask.get("taskName");
                                ProcessAssigneeSnapshot snapshot = ProcessAssigneeSnapshot.fromEngineTask(currentTask);
                                Map<String, String> assigneeCache = userDisplayNameResolver.resolveBatch(
                                        userDisplayNameResolver.collectAssigneeUserKeys(
                                                snapshot.getAssigneeUserId(), snapshot.getCandidateUserIds()));
                                String currentAssigneeDisplay = userDisplayNameResolver.resolveCurrentAssigneeDisplay(
                                        snapshot.getAssigneeUserId(), snapshot.getCandidateUserIds(), assigneeCache);

                                if (currentNodeName != null && !currentNodeName.isEmpty()) {
                                    info.setCurrentNode(currentNodeName);
                                    info.setCurrentAssignee(currentAssigneeDisplay);
                                    info.setCandidateUsers(snapshot.getCandidateUserIds());
                                    instance.setCurrentNode(currentNodeName);
                                    instance.setCurrentAssignee(snapshot.getAssigneeUserId());
                                    instance.setCandidateUsers(snapshot.getCandidateUserIds());
                                    processInstanceRepository.save(instance);
                                    log.info("getProcessDetail: refreshed currentNode={}, assignee={}, candidates={} from Flowable for process {}",
                                            currentNodeName, snapshot.getAssigneeUserId(),
                                            snapshot.getCandidateUserIds(), processId);
                                }
                            } else {
                                log.debug("getProcessDetail: no active tasks in Flowable for process {}, keeping null currentNode", processId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("getProcessDetail: failed to refresh currentNode from Flowable for process {}: {}", processId, e.getMessage());
            }
        }

        Map<String, Object> vars = info.getVariables();
        boolean hasSubTables =
                vars != null
                        && vars.get("__subTables__") instanceof Map<?, ?> subMap
                        && !subMap.isEmpty();
        Map<String, Map<String, MiRowProgress>> miProgress = Collections.emptyMap();
        if (hasSubTables || "RUNNING".equals(instance.getStatus())) {
            miProgress = miOverlayComponent.resolveMiRowProgress(processId, instance.getStatus());
        }
        if ("RUNNING".equals(instance.getStatus())) {
            miOverlayComponent.reconcileCurrentNodeWithMiOverlay(info, miProgress);
        }
        if (hasSubTables) {
            subTableEnrichmentComponent.enrichSubTablesWithAssignmentData(info, miProgress);
        }

        // Request ID for the detail Basic Info (same derivation as the list); computed from the
        // process variables which are still present on the detail DTO.
        if (info.getFunctionUnitCode() != null) {
            RequestIdEnricher.SpecCache specs = requestIdEnricher.resolveSpecs(
                    java.util.Set.of(info.getFunctionUnitCode()));
            info.setRequestId(requestIdEnricher.buildRequestId(
                    specs, info.getFunctionUnitCode(), info.getVariables()));
        }

        return info;
    }

    /**
     * Check whether a portal userId is a participant of the given process.
     * Quick local checks first; falls back to querying the workflow-engine
     * process history to see if the user was ever a task assignee.
     */
    public boolean isProcessParticipant(String userId, ProcessInstanceInfo detail) {
        if (detail == null || userId == null) return false;

        // 1. Process initiator
        if (userId.equals(detail.getStartUserId())) return true;

        // 2. Current assignee (may be display-name OR userId depending on data flow)
        if (userId.equals(detail.getCurrentAssignee())) return true;

        // 3. Candidate users (or-sign scenario, comma-separated)
        String candidates = detail.getCandidateUsers();
        if (candidates != null && !candidates.isBlank()) {
            for (String c : candidates.split(",")) {
                if (userId.equals(c.trim())) return true;
            }
        }

        // 4. Resolve userId to display name and compare against currentAssignee
        //    (currentAssignee is often stored as display name)
        try {
            String displayName = resolveUserDisplayName(userId);
            if (displayName != null && displayName.equals(detail.getCurrentAssignee())) return true;
        } catch (Exception e) {
            log.debug("Could not resolve display name for user {}: {}", userId, e.getMessage());
        }

        // 5. Fall back: check workflow-engine process history for any task
        //    where the user was ever an assignee (covers completed MI sub-tasks, etc.)
        try {
            if (workflowEngineClient.isAvailable()) {
                var historyOpt = workflowEngineClient.getProcessInstanceHistory(detail.getId());
                if (historyOpt.isPresent()) {
                    for (Map<String, Object> record : historyOpt.get()) {
                        Object operatorId = record.get("operatorId");
                        if (operatorId != null && userId.equals(String.valueOf(operatorId).trim())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check process history participation for user {} in process {}: {}",
                    userId, detail.getId(), e.getMessage());
        }

        return false;
    }

    /**
     * Cached display-name resolution to avoid duplicate DB hits in one batch
     */
    private String resolveUserDisplayNameCached(String userId, Map<String, String> cache) {
        return userDisplayNameResolver.resolveCached(userId, cache);
    }

    /**
     * Resolves user display name
     */
    private String resolveUserDisplayName(String userId) {
        return userDisplayNameResolver.resolve(userId);
    }
}
