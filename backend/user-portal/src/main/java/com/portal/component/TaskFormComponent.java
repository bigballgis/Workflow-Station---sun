package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.*;
import com.portal.entity.ProcessInstance;
import com.portal.util.SubTableNestingSanitizer;
import com.portal.util.SystemAuditFieldFiller;
import com.portal.service.UserDisplayNameResolver;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Task Form component.
 * Loads Task Form data, submit handling, snapshot capture, and completed-task
 * form queries.
 *
 * <p>
 * 本类为门面：保留全部 public 方法签名与 public 内部类型，方法体委托同包协作类——
 * {@link TaskFormFieldMapper}（字段/快照纯函数）、{@link TaskFormDefinitionLoader}（表单定义加载）、
 * {@link TaskFormSubTableChangeRecorder}（子表变更历史）。协作类以 {@code @Lazy @Autowired}
 * 字段注入，
 * 不拓宽构造器，沿用仓库既定破环模式（见 {@code processComponent}）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskFormComponent {

    private final ProcessFormComponent processFormComponent;
    private final ChangeHistoryComponent changeHistoryComponent;
    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager platformTransactionManager;

    private volatile TransactionTemplate taskFormWriteTxTemplate;

    /**
     * Single write txn for task-form persistence — avoids
     * UnexpectedRollbackException vs nested listeners/history.
     */
    private TransactionTemplate taskFormWriteTx() {
        TransactionTemplate t = taskFormWriteTxTemplate;
        if (t == null) {
            synchronized (this) {
                t = taskFormWriteTxTemplate;
                if (t == null) {
                    t = new TransactionTemplate(platformTransactionManager);
                    taskFormWriteTxTemplate = t;
                }
            }
        }
        return t;
    }

    /**
     * Lazy: merges physical relation-table rows into task-form variable payloads
     * without widening ctor for tests.
     */
    @Lazy
    @Autowired
    private ProcessComponent processComponent;

    /**
     * 协作类以 {@code @Lazy @Autowired} 字段注入——保持 8 参 {@code @RequiredArgsConstructor}
     * 不变，不破坏测试构造点。
     * 测试以 {@code new TaskFormComponent(...)} 构造时 Spring 不参与注入，这些字段为 null；
     * 故下方 accessor 在为 null 时用门面自身已持有的依赖按需构造一份（协作类无额外状态），行为与注入版本一致。
     */
    @Lazy
    @Autowired
    private TaskFormFieldMapper fieldMapper;

    @Lazy
    @Autowired
    private TaskFormDefinitionLoader formDefinitionLoader;

    @Lazy
    @Autowired
    private TaskFormSubTableChangeRecorder subTableChangeRecorder;

    @Lazy
    @Autowired
    private ChangeHistorySubmissionFilter changeHistorySubmissionFilter;

    /**
     * Lazy: computes the readonly Request ID value so the form field matches DW
     * preview / portal lists.
     */
    @Lazy
    @Autowired
    private RequestIdEnricher requestIdEnricher;

    /**
     * Lazy: resolves updated_by display names for system audit fields (null in
     * `new`-constructed tests).
     */
    @Lazy
    @Autowired
    private UserDisplayNameResolver userDisplayNameResolver;

    /**
     * Lazy: Owner-field validation / display re-resolution / default fill on task-form
     * submits (null in `new`-constructed tests, which do not exercise owner fields).
     */
    @Lazy
    @Autowired
    private OwnerFieldComponent ownerFieldComponent;

    /**
     * Display name for audit fields; falls back to the raw user id when the
     * resolver is unavailable.
     */
    private String resolveAuditUserDisplay(String userId) {
        UserDisplayNameResolver resolver = userDisplayNameResolver;
        if (resolver == null) {
            return userId;
        }
        try {
            String display = resolver.resolve(userId);
            return display != null && !display.isBlank() ? display : userId;
        } catch (RuntimeException ex) {
            log.debug("resolveAuditUserDisplay failed for {}: {}", userId, ex.getMessage());
            return userId;
        }
    }

    @Lazy
    @Autowired
    private ProcessSubTablePrimaryKeyEnricherComponent processSubTablePrimaryKeyEnricherComponent;

    /** Lazy: server-side formula columns; null in {@code new}-constructed tests skips recalculation. */
    @Lazy
    @Autowired
    private ComputedFieldRecalculator computedFieldRecalculator;

    private ProcessSubTablePrimaryKeyEnricherComponent subTablePrimaryKeyEnricher() {
        return processSubTablePrimaryKeyEnricherComponent;
    }

    private void recalculateComputedFields(String functionUnitCode, Map<String, Object> variables) {
        ComputedFieldRecalculator recalculator = computedFieldRecalculator;
        if (recalculator == null || functionUnitCode == null || functionUnitCode.isBlank() || variables == null) {
            return;
        }
        recalculator.recalculate(functionUnitCode, variables);
    }

    private ChangeHistorySubmissionFilter changeHistorySubmissionFilter() {
        ChangeHistorySubmissionFilter filter = changeHistorySubmissionFilter;
        if (filter == null) {
            filter = new ChangeHistorySubmissionFilter(jdbcTemplate, objectMapper);
            changeHistorySubmissionFilter = filter;
        }
        return filter;
    }

    Map<String, Object> filterTaskSubmissionForChangeHistory(String processInstanceId,
            String stageId,
            Map<String, Object> submitted,
            Map<String, Object> enriched) {
        return changeHistorySubmissionFilter().filterTaskSubmission(
                processInstanceId, stageId, submitted, enriched);
    }

    Map<String, Object> copyChangeHistorySubmission(Map<String, Object> submitted) {
        return changeHistorySubmissionFilter().copyPayload(submitted);
    }

    Object filterTaskSubTableBaselineForChangeHistory(String processInstanceId,
            String stageId,
            Object storedSubTables) {
        return changeHistorySubmissionFilter().filterTaskSubTableBaseline(
                processInstanceId, stageId, storedSubTables);
    }

    private RequestIdEnricher requestIdEnricher() {
        RequestIdEnricher r = requestIdEnricher;
        if (r == null) {
            r = new RequestIdEnricher(jdbcTemplate, objectMapper, processInstanceRepository);
            requestIdEnricher = r;
        }
        return r;
    }

    private TaskFormFieldMapper fieldMapper() {
        TaskFormFieldMapper m = fieldMapper;
        if (m == null) {
            m = new TaskFormFieldMapper();
            fieldMapper = m;
        }
        return m;
    }

    private TaskFormDefinitionLoader formDefinitionLoader() {
        TaskFormDefinitionLoader l = formDefinitionLoader;
        if (l == null) {
            l = new TaskFormDefinitionLoader(restTemplate, objectMapper, jdbcTemplate);
            formDefinitionLoader = l;
        }
        return l;
    }

    private TaskFormSubTableChangeRecorder subTableChangeRecorder() {
        TaskFormSubTableChangeRecorder r = subTableChangeRecorder;
        if (r == null) {
            r = new TaskFormSubTableChangeRecorder(changeHistoryComponent);
            subTableChangeRecorder = r;
        }
        return r;
    }

    /**
     * Lazy: hydrates {@code up_process_instance} for engine-only starts (email
     * monitor).
     */
    @Lazy
    @Autowired
    private ProcessInstanceHydrationComponent processInstanceHydration;

    private ProcessInstance requireProcessInstance(String processInstanceId) {
        ProcessInstanceHydrationComponent hydration = processInstanceHydration;
        if (hydration != null) {
            return hydration.requireProcessInstance(processInstanceId);
        }
        return processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new PortalException("404",
                        "Process instance not found: " + processInstanceId));
    }

    /**
     * Fills {@code target} with process variables that live only in the Flowable
     * engine (e.g. a service
     * task's {@code __subTables__} output) and are absent from the portal's own
     * {@code up_process_instance}
     * store. Gap-fill only: values already present win, so portal form submissions
     * and user edits are never
     * overwritten. Best-effort — a failed engine round-trip leaves the portal-store
     * values untouched.
     */
    private void mergeEngineOnlyVariables(String processInstanceId, Map<String, Object> target) {
        if (workflowEngineClient == null || processInstanceId == null) {
            return;
        }
        try {
            workflowEngineClient.getProcessInstance(processInstanceId).ifPresent(row -> {
                Object raw = row.get("variables");
                if (raw instanceof Map<?, ?> engineVars) {
                    engineVars.forEach((k, v) -> {
                        if (k != null && v != null && !target.containsKey(String.valueOf(k))) {
                            target.put(String.valueOf(k), v);
                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            log.debug("mergeEngineOnlyVariables skipped for {}: {}", processInstanceId, e.getMessage());
        }
    }

    @Value("${developer-workstation.url:http://localhost:8091}")
    private String developerWorkstationUrl;

    /**
     * Returns Task Form layout and current process variable values (field subset).
     * Resolves FormStageBinding by taskDefinitionKey; falls back to read-only
     * Process Form when unbound.
     *
     * @param taskId task instance ID
     * @return TaskFormData DTO
     */
    public TaskFormData getTaskFormData(String taskId) {
        log.debug("Getting task form data for task: {}", taskId);

        long __t = System.nanoTime();
        TaskInfo taskInfo = getTaskInfo(taskId);
        log.info("[PERF] form-data.getTaskInfo(engine) took {} ms", (System.nanoTime() - __t) / 1_000_000L);

        // Find FormStageBinding by taskDefinitionKey
        __t = System.nanoTime();
        Map<String, Object> formDefinition = fetchTaskFormByStageId(taskInfo.taskDefinitionKey, taskInfo.processInstanceId);
        log.info("[PERF] form-data.fetchTaskFormByStageId took {} ms", (System.nanoTime() - __t) / 1_000_000L);

        // Get process instance for variables
        ProcessInstance processInstance = requireProcessInstance(taskInfo.processInstanceId);

        Map<String, Object> allVariables = processInstance.getVariables() != null
                ? processInstance.getVariables()
                : Collections.emptyMap();
        Map<String, Object> hydratedVariables = new HashMap<>(allVariables);
        // Service-task outputs (e.g. an Activepieces task that sets __subTables__) live
        // in the Flowable
        // engine but never reach the portal's up_process_instance store, which is only
        // written on portal
        // form submissions. Gap-fill from the live engine variables so task forms
        // render those results.
        mergeEngineOnlyVariables(taskInfo.processInstanceId, hydratedVariables);
        if (ownerFieldComponent != null) {
            ownerFieldComponent.projectForRead(
                    processInstance.getFunctionUnitCode(),
                    new OwnerFieldComponent.OwnerWriteContext(
                            processInstance.getStartUserId(),
                            processInstance.getStartUserId(),
                            processInstance.getCurrentAssignee(),
                            processInstance.getCandidateUsers(),
                            null),
                    hydratedVariables);
        }
        if (processComponent != null) {
            __t = System.nanoTime();
            processComponent.enrichSubTablesVariablesFromPhysicalTables(taskInfo.processInstanceId, hydratedVariables);
            log.info("[PERF] form-data.enrichSubTables took {} ms", (System.nanoTime() - __t) / 1_000_000L);
        }

        // Get Process Form reference data
        __t = System.nanoTime();
        ProcessFormData processFormRef = processFormComponent.getProcessFormData(taskInfo.processInstanceId);
        log.info("[PERF] form-data.nested.getProcessFormData took {} ms", (System.nanoTime() - __t) / 1_000_000L);

        if (formDefinition == null) {
            // Fallback: no Task Form binding, return only ProcessFormData in read-only
            // mode.
            // Still surface the readonly Request ID so the rendered field isn't blank.
            log.info("No Task Form binding found for stage '{}', falling back to Process Form",
                    taskInfo.taskDefinitionKey);
            Map<String, Object> fallbackFieldValues = null;
            String fallbackRequestId = requestIdEnricher()
                    .buildRequestId(processInstance.getFunctionUnitCode(), hydratedVariables);
            if (fallbackRequestId != null) {
                fallbackFieldValues = new HashMap<>();
                fallbackFieldValues.put(RequestIdEnricher.REQUEST_ID_FIELD, fallbackRequestId);
            }
            return TaskFormData.builder()
                    .taskId(taskId)
                    .taskDefinitionKey(taskInfo.taskDefinitionKey)
                    .formName(null)
                    .configJson(null)
                    .fieldPermissions(null)
                    .fieldValues(fallbackFieldValues)
                    .subTableBindings(null)
                    .processFormRef(processFormRef)
                    .requestIdConfig(processFormRef != null ? processFormRef.getRequestIdConfig() : null)
                    .build();
        }

        // Extract Task Form layout and field permissions
        Map<String, Object> configJson = fieldMapper().extractMapField(formDefinition, "configJson");
        Map<String, String> fieldPermissions = fieldMapper().extractFieldPermissions(formDefinition);
        String formName = formDefinition.get("formName") != null
                ? (String) formDefinition.get("formName")
                : "Task Form";
        Boolean formReadOnly = formDefinition.get("readOnly") instanceof Boolean
                ? (Boolean) formDefinition.get("readOnly")
                : false;

        // Get field values from process variables (subset based on fieldPermissions
        // keys)
        Map<String, Object> fieldValues = fieldMapper().extractFieldSubset(hydratedVariables,
                fieldPermissions.keySet());
        // Mirror persistTaskFormSnapshot: always attach live __subTables__ when present
        // so nested /
        // copied-task bindings hydrate even if fieldPermissions omits or carries a
        // stale __subTables__ entry.
        if (hydratedVariables.containsKey("__subTables__")) {
            fieldValues.put("__subTables__", hydratedVariables.get("__subTables__"));
        }
        // Readonly Request ID synthetic field: render the same value as DW preview /
        // portal lists.
        applyRequestIdFieldValue(fieldValues, processInstance, hydratedVariables);

        return TaskFormData.builder()
                .taskId(taskId)
                .taskDefinitionKey(taskInfo.taskDefinitionKey)
                .formName(formName)
                .configJson(configJson)
                .fieldPermissions(fieldPermissions)
                .fieldValues(fieldValues)
                .subTableBindings(Collections.emptyList())
                .processFormRef(processFormRef)
                .formReadOnly(formReadOnly)
                .requestIdConfig(processFormRef != null ? processFormRef.getRequestIdConfig() : null)
                .build();
    }

    /**
     * Fill the synthetic {@code __request_id} field value when the main table
     * configures a Request ID.
     * No-op (leaves the field absent) when unconfigured — the readonly input then
     * shows empty.
     */
    private void applyRequestIdFieldValue(Map<String, Object> fieldValues,
            ProcessInstance processInstance,
            Map<String, Object> variables) {
        String requestId = requestIdEnricher()
                .buildRequestId(processInstance.getFunctionUnitCode(), variables);
        if (requestId != null) {
            fieldValues.put(RequestIdEnricher.REQUEST_ID_FIELD, requestId);
        }
    }

    /**
     * Submits Task Form updates (editable fields only).
     * Filters read-only fields and updates only EDITABLE process variables.
     *
     * @param taskId   task instance ID
     * @param userId   acting user ID
     * @param formData form payload (may include read-only fields; they are filtered
     *                 out)
     */
    public void submitTaskForm(String taskId, String userId, Map<String, Object> formData) {
        submitTaskForm(taskId, userId, formData, null);
    }

    /**
     * Submits Task Form updates (editable fields only) with optional
     * concurrent-modification detection.
     *
     * @param taskId         task instance ID
     * @param userId         acting user ID
     * @param formData       form payload (read-only fields filtered)
     * @param baselineValues field snapshot from client load for concurrency check;
     *                       null skips detection
     */
    public void submitTaskForm(String taskId, String userId, Map<String, Object> formData,
            Map<String, Object> baselineValues) {
        log.info("Submitting task form for task: {}, user: {}", taskId, userId);

        TaskInfo taskInfo = getTaskInfo(taskId);

        // Get field permissions to filter out READONLY fields
        Map<String, Object> formDefinition = fetchTaskFormByStageId(taskInfo.taskDefinitionKey, taskInfo.processInstanceId);
        Map<String, String> fieldPermissions = formDefinition != null
                ? fieldMapper().extractFieldPermissions(formDefinition)
                : Collections.emptyMap();

        Map<String, Object> submittedSnapshot = changeHistorySubmissionFilter().copyPayload(formData);

        // Filter: only accept EDITABLE fields
        Map<String, Object> editableData = filterEditableFields(formData, fieldPermissions);

        if (editableData.isEmpty()) {
            log.debug("No editable fields to update for task: {}", taskId);
            return;
        }

        if (editableData.containsKey("__subTables__")) {
            processInstanceRepository.findById(taskInfo.processInstanceId).ifPresent(pi -> {
                String fuCode = pi.getFunctionUnitCode();
                if (fuCode != null && !fuCode.isBlank()) {
                    ProcessSubTablePrimaryKeyEnricherComponent enricher = subTablePrimaryKeyEnricher();
                    if (enricher != null) {
                        enricher.allocateMissingPrimaryKeysInVariables(fuCode, editableData);
                    }
                }
            });
        }

        Map<String, Object> userChanges = changeHistorySubmissionFilter().filterTaskSubmission(
                taskInfo.processInstanceId, taskInfo.taskDefinitionKey, submittedSnapshot, editableData);
        AtomicReference<Map<String, Object>> snapshotOldVarsRef = new AtomicReference<>();
        AtomicReference<Set<String>> concurrentFieldsRef = new AtomicReference<>(Set.of());

        taskFormWriteTx().executeWithoutResult(status -> {
            ProcessInstance processInstance = requireProcessInstance(taskInfo.processInstanceId);

            Map<String, Object> currentVariables = processInstance.getVariables() != null
                    ? new HashMap<>(processInstance.getVariables())
                    : new HashMap<>();

            Set<String> concurrentFields = detectConcurrentModifications(
                    baselineValues, currentVariables, editableData.keySet());

            if (!concurrentFields.isEmpty()) {
                log.warn("Concurrent modification detected on process {}, task {}, fields: {}, user: {}",
                        taskInfo.processInstanceId, taskId, concurrentFields, userId);
            }

            snapshotOldVarsRef.set(new HashMap<>(currentVariables));
            concurrentFieldsRef.set(Set.copyOf(concurrentFields));

            Map<String, Object> updatedVariables = new HashMap<>(currentVariables);
            Map<String, Object> inbound = new HashMap<>(editableData);
            // Defense in depth: strip even if filterEditableFields missed a case variant.
            SystemAuditFieldFiller.stripClientAuditKeys(inbound);
            updatedVariables.putAll(inbound);

            // Owner fields: Creator pins startUserId; Current Assignee follows snapshot.
            if (ownerFieldComponent != null) {
                ownerFieldComponent.applyOnSubmit(
                        processInstance.getFunctionUnitCode(),
                        new OwnerFieldComponent.OwnerWriteContext(
                                userId,
                                processInstance.getStartUserId(),
                                processInstance.getCurrentAssignee(),
                                processInstance.getCandidateUsers(),
                                currentVariables),
                        updatedVariables);
            }

            // System audit fields: refresh updated_at/updated_by at real update
            // (platform-managed; not gated on Form Design). created_* preserved from insert.
            SystemAuditFieldFiller.fillOnUpdate(updatedVariables, resolveAuditUserDisplay(userId));
            recalculateComputedFields(processInstance.getFunctionUnitCode(), updatedVariables);
            // Prevent geometric __subTables__ bloat: drop deep nested copies before
            // persisting so each
            // task save stores the canonical one-level structure instead of compounding
            // prior rounds.
            SubTableNestingSanitizer.stripDeepNestedSubTables(updatedVariables);
            processInstance.setVariables(updatedVariables);
            processInstanceRepository.save(processInstance);

            log.info("Process variables updated for task: {}, fields: {}", taskId, editableData.keySet());
        });

        /*
         * Change history runs after the write TransactionTemplate commits so failures
         * cannot mark it rollback-only.
         * Sub-table change history is deferred to task completion for consolidated
         * recording.
         */
        ChangeHistoryContext context = ChangeHistoryContext.builder()
                .processInstanceId(taskInfo.processInstanceId)
                .taskInstanceId(taskId)
                .stageId(taskInfo.taskDefinitionKey)
                .userId(userId)
                .build();

        Map<String, Object> snapshotOldVars = snapshotOldVarsRef.get();
        if (snapshotOldVars == null) {
            snapshotOldVars = Collections.emptyMap();
        }
        Set<String> concurrentSnapshot = concurrentFieldsRef.get();
        if (concurrentSnapshot == null) {
            concurrentSnapshot = Set.of();
        }

        try {
            for (String field : concurrentSnapshot) {
                changeHistoryComponent.recordConcurrentModificationWarning(
                        taskInfo.processInstanceId, field, "unknown", userId);
            }
            // Record top-level field changes
            changeHistoryComponent.recordFieldChanges(context, snapshotOldVars, userChanges);
            // Record sub-table row changes immediately so users see per-save
            // history; completion-time consolidation against the first-save
            // baseline is still recorded (cross-save dedup prevents identical
            // old+new pairs from duplicating when the final state equals the
            // last save).
            Object oldSubTables = snapshotOldVars.get("__subTables__");
            Object newSubTables = userChanges.get("__subTables__");
            if (newSubTables != null || oldSubTables != null) {
                Object filteredOldSubTables = changeHistorySubmissionFilter()
                        .filterTaskSubTableBaseline(taskInfo.processInstanceId,
                                taskInfo.taskDefinitionKey, oldSubTables);
                subTableChangeRecorder().recordSubTableChangeHistory(
                        context, filteredOldSubTables, newSubTables);
            }
        } catch (RuntimeException ex) {
            log.warn("task form change-history skipped for task {}: {}", taskId, ex.getMessage());
        }
    }

    /**
     * Detects concurrent edits by comparing baseline values to current process
     * variables.
     * When current value != baseline, another user changed the field during
     * editing.
     *
     * @param baselineValues      field snapshot from client load (may be null)
     * @param currentVariables    current process variables
     * @param submittedFieldNames field names in this submit
     * @return field names modified concurrently
     */
    public Set<String> detectConcurrentModifications(Map<String, Object> baselineValues,
            Map<String, Object> currentVariables,
            Set<String> submittedFieldNames) {
        return fieldMapper().detectConcurrentModifications(baselineValues, currentVariables, submittedFieldNames);
    }

    /**
     * Returns snapshot and live values for a completed task.
     *
     * @param taskId task instance ID
     * @return CompletedTaskFormData DTO
     */
    public CompletedTaskFormData getCompletedTaskFormData(String taskId) {
        log.debug("Getting completed task form data for task: {}", taskId);

        TaskInfo taskInfo = getTaskInfo(taskId);

        ProcessInstance processInstance = requireProcessInstance(taskInfo.processInstanceId);

        Map<String, Object> allVariables = processInstance.getVariables() != null
                ? processInstance.getVariables()
                : Collections.emptyMap();

        // Get snapshot from process variable _snapshot_{taskId}
        String snapshotKey = "_snapshot_" + taskId;
        TaskFormSnapshot snapshot = fieldMapper().extractSnapshot(allVariables, snapshotKey);

        // Get current live values from process variables
        Map<String, Object> liveValues;
        if (snapshot != null && snapshot.getFieldValues() != null) {
            // Get live values for the same field subset as the snapshot
            liveValues = fieldMapper().extractFieldSubset(allVariables, snapshot.getFieldValues().keySet());
        } else {
            liveValues = Collections.emptyMap();
        }

        // Readonly Request ID synthetic field: same value as DW preview / portal lists
        // (frozen variables).
        String requestId = requestIdEnricher().buildRequestId(processInstance.getFunctionUnitCode(), allVariables);
        if (requestId != null) {
            if (!(liveValues instanceof HashMap)) {
                liveValues = new HashMap<>(liveValues);
            }
            liveValues.put(RequestIdEnricher.REQUEST_ID_FIELD, requestId);
            if (snapshot != null && snapshot.getFieldValues() != null
                    && !snapshot.getFieldValues().containsKey(RequestIdEnricher.REQUEST_ID_FIELD)) {
                snapshot.getFieldValues().put(RequestIdEnricher.REQUEST_ID_FIELD, requestId);
            }
        }

        // Get showLiveValues config from form definition
        boolean showLiveValues = true;
        Map<String, Object> formDefinition = fetchTaskFormByStageId(taskInfo.taskDefinitionKey, taskInfo.processInstanceId);
        if (formDefinition != null && formDefinition.containsKey("showLiveValues")) {
            Object slv = formDefinition.get("showLiveValues");
            if (slv instanceof Boolean) {
                showLiveValues = (Boolean) slv;
            }
        }

        // Get Process Form reference data
        ProcessFormData processFormRef = processFormComponent.getProcessFormData(taskInfo.processInstanceId);

        return CompletedTaskFormData.builder()
                .snapshot(snapshot)
                .liveValues(liveValues)
                .showLiveValues(showLiveValues)
                .processFormRef(processFormRef)
                .build();
    }

    /**
     * Merges a Task Form field-subset snapshot as {@code _snapshot_{taskId}} into
     * process variables before approval completion.
     * <p>
     * Callers should {@code save} {@link ProcessInstance} <strong>once</strong> to
     * avoid {@link ProcessInstance#lockVersion}
     * optimistic-lock conflicts (back-to-back UPDATEs on the same row can cause
     * UnexpectedRollback).
     * </p>
     * <p>
     * <b>Anti-bloat guardrails:</b>
     * </p>
     * <ul>
     * <li>When the stage has <em>no</em> Task Form binding
     * ({@code fetchTaskFormByStageId} null or empty fieldPermissions),
     * the snapshot stores <strong>empty fieldValues</strong>—no fallback to copying
     * all process variables and no
     * {@code __subTables__}. Live {@code __subTables__} already lives on root
     * variables; duplicating it in snapshots
     * inflates the {@code variables} JSON column (frontend alias keys per binding,
     * multiplied by MI child completions →
     * PostgreSQL parameter encoding OOM).</li>
     * <li>When the stage <em>has</em> a Task Form binding, the snapshot keeps the
     * fieldPermissions subset plus
     * {@code __subTables__} so Portal can fully render the completed form.</li>
     * </ul>
     *
     * @param processInstanceId process instance owning the task; identifies the function unit that scopes
     *                          the Task Form binding lookup
     * @param mergedVariables merged process variables map (snapshot key written in
     *                        place)
     * @return form field names included in the snapshot (for logging)
     */
    public Set<String> mergeCompletedTaskSnapshotIntoVariables(String taskId, String userId, String taskDefinitionKey,
            String processInstanceId, Map<String, Object> mergedVariables) {
        if (mergedVariables == null || taskId == null || taskDefinitionKey == null) {
            return Set.of();
        }
        Map<String, Object> formDefinition = fetchTaskFormByStageId(taskDefinitionKey, processInstanceId);
        Map<String, String> fieldPermissions = formDefinition != null
                ? fieldMapper().extractFieldPermissions(formDefinition)
                : Collections.emptyMap();

        // Empty snapshot when no Task Form binding—avoid writing __subTables__ (with
        // alias copies) with no UI consumer.
        Map<String, Object> fieldValues;
        if (fieldPermissions.isEmpty()) {
            fieldValues = new HashMap<>();
        } else {
            fieldValues = fieldMapper().extractFieldSubset(mergedVariables, fieldPermissions.keySet());
            if (mergedVariables.containsKey("__subTables__")) {
                fieldValues.put("__subTables__", mergedVariables.get("__subTables__"));
            }
        }

        TaskFormSnapshot snapshot = TaskFormSnapshot.builder()
                .taskId(taskId)
                .taskDefinitionKey(taskDefinitionKey)
                .assignee(userId)
                .completedAt(Instant.now())
                .fieldValues(fieldValues)
                .build();

        mergedVariables.put("_snapshot_" + taskId, snapshotToMap(snapshot));
        log.debug("Merged snapshot keys into variables for task {}, fields {}", taskId, fieldValues.keySet());
        return Set.copyOf(fieldValues.keySet());
    }

    /**
     * Captures snapshot on task completion.
     * Persists current process variables (Task Form field subset) as
     * _snapshot_{taskId}.
     *
     * @param taskId task instance ID
     * @param userId acting user ID (assignee)
     */
    @Transactional
    public void captureTaskFormSnapshot(String taskId, String userId) {
        log.info("Capturing task form snapshot for task: {}, user: {}", taskId, userId);

        TaskInfo taskInfo = getTaskInfo(taskId);
        persistTaskFormSnapshot(taskId, userId, taskInfo.taskDefinitionKey, taskInfo.processInstanceId,
                Collections.emptyMap());
    }

    /**
     * Captures snapshot after task completion.
     * <p>
     * Flowable runtime task is gone after completion; caller must pass
     * stage/process info captured beforehand.
     * </p>
     */
    @Transactional
    public void captureTaskFormSnapshot(String taskId, String userId, String taskDefinitionKey,
            String processInstanceId, Map<String, Object> completedVariables) {
        log.info("Capturing completed task form snapshot for task: {}, stage: {}", taskId, taskDefinitionKey);

        persistTaskFormSnapshot(taskId, userId, taskDefinitionKey, processInstanceId,
                completedVariables != null ? completedVariables : Collections.emptyMap());
    }

    private void persistTaskFormSnapshot(String taskId, String userId, String taskDefinitionKey,
            String processInstanceId, Map<String, Object> completedVariables) {
        ProcessInstance processInstance = requireProcessInstance(processInstanceId);

        Map<String, Object> merged = new HashMap<>();
        if (processInstance.getVariables() != null) {
            merged.putAll(processInstance.getVariables());
        }
        merged.putAll(completedVariables != null ? completedVariables : Collections.emptyMap());

        Set<String> snapshotFieldKeys = mergeCompletedTaskSnapshotIntoVariables(taskId, userId, taskDefinitionKey,
                processInstanceId, merged);
        SubTableNestingSanitizer.stripDeepNestedSubTables(merged);
        processInstance.setVariables(merged);
        processInstanceRepository.save(processInstance);

        log.info("Task form snapshot captured for task: {}, fields: {}", taskId, snapshotFieldKeys);
    }

    // ==================== Public utility methods for testing ====================

    /**
     * Filters read-only fields, keeping EDITABLE fields only.
     * When fieldPermissions is empty, accepts all fields (backward compatible).
     */
    public Map<String, Object> filterEditableFields(Map<String, Object> formData,
            Map<String, String> fieldPermissions) {
        return fieldMapper().filterEditableFields(formData, fieldPermissions);
    }

    /**
     * Extracts a field subset from full process variables.
     */
    public Map<String, Object> extractFieldSubset(Map<String, Object> allVariables,
            Set<String> fieldNames) {
        return fieldMapper().extractFieldSubset(allVariables, fieldNames);
    }

    /**
     * Counts fields that differ between snapshot and live values.
     */
    public int countSnapshotDiffs(Map<String, Object> snapshotValues, Map<String, Object> liveValues) {
        return fieldMapper().countSnapshotDiffs(snapshotValues, liveValues);
    }

    /**
     * Converts snapshot DTO to Map for storage in process variables.
     */
    public Map<String, Object> snapshotToMap(TaskFormSnapshot snapshot) {
        return fieldMapper().snapshotToMap(snapshot);
    }

    /**
     * Restores snapshot DTO from Map (read from process variables).
     */
    public TaskFormSnapshot mapToSnapshot(Map<String, Object> map) {
        return fieldMapper().mapToSnapshot(map);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Loads task info (taskDefinitionKey, processInstanceId) via
     * WorkflowEngineClient from Flowable.
     */
    @SuppressWarnings("unchecked")
    protected TaskInfo getTaskInfo(String taskId) {
        if (workflowEngineClient.isAvailable()) {
            Optional<Map<String, Object>> result = workflowEngineClient.getTaskById(taskId);
            if (result.isPresent()) {
                Map<String, Object> body = result.get();
                Map<String, Object> data = body.containsKey("data")
                        ? (Map<String, Object>) body.get("data")
                        : body;

                String taskDefinitionKey = (String) data.get("taskDefinitionKey");
                String processInstanceId = (String) data.get("processInstanceId");

                if (taskDefinitionKey != null && processInstanceId != null) {
                    return new TaskInfo(taskDefinitionKey, processInstanceId);
                }
            }
        }

        throw new PortalException("404", "Task not found: " + taskId);
    }

    /**
     * Loads Task Form definition by stageId (taskDefinitionKey), scoped to the process instance's function unit.
     * Prefers developer-workstation; falls back to local
     * {@code dw_form_stage_bindings} when unreachable (shared PostgreSQL with DW).
     *
     * <p>{@code processInstanceId} is what identifies the function unit: the same BPMN node id may be bound
     * in several units, so an unscoped lookup can return a different unit's form.</p>
     */
    private Map<String, Object> fetchTaskFormByStageId(String stageId, String processInstanceId) {
        return formDefinitionLoader().fetchTaskFormByStageId(stageId, processInstanceId, developerWorkstationUrl);
    }

    // ========== Inner data class ==========

    /**
     * Task info (from Flowable).
     */
    public static class TaskInfo {
        public final String taskDefinitionKey;
        public final String processInstanceId;

        public TaskInfo(String taskDefinitionKey, String processInstanceId) {
            this.taskDefinitionKey = taskDefinitionKey;
            this.processInstanceId = processInstanceId;
        }
    }
}
