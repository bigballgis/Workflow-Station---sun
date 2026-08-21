package com.developer.component.impl;

import com.developer.component.ProcessDesignComponent;
import com.developer.dto.ValidationResult;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.util.XmlEncodingUtil;
import com.platform.common.i18n.I18nService;
import com.platform.common.i18n.impl.I18nServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 流程设计组件实现。
 *
 * <p>作为门面（facade）保留接口全部 public 方法签名，方法体委托同包协作类：
 * <ul>
 *   <li>{@link ProcessBpmnStaleIdFixer} —— 落库前陈旧 ID（formId/subTableId/actionIds）修正</li>
 *   <li>{@link ProcessBpmnValidator} —— BPMN 解析与各类校验（含多实例、LAST_TASK_ASSIGNEE 拓扑）</li>
 *   <li>{@link ProcessSimulationHelper} —— 流程模拟</li>
 *   <li>{@link ProcessDebugProbeRunner} —— 调试 lookup probe / action runner</li>
 * </ul>
 * 拆分为纯结构重组，业务行为零变化。</p>
 */
@Component
@Slf4j
public class ProcessDesignComponentImpl implements ProcessDesignComponent {

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;

    private final ProcessBpmnStaleIdFixer staleIdFixer;
    private final ProcessBpmnValidator bpmnValidator;
    private final ProcessSimulationHelper simulationHelper;
    private final ProcessDebugProbeRunner debugProbeRunner;
    private final ProcessBpmnFormStageBindingSync formStageBindingSync;

    @Autowired
    public ProcessDesignComponentImpl(
            ProcessDefinitionRepository processDefinitionRepository,
            FunctionUnitRepository functionUnitRepository,
            ProcessBpmnStaleIdFixer staleIdFixer,
            ProcessBpmnValidator bpmnValidator,
            ProcessSimulationHelper simulationHelper,
            ProcessDebugProbeRunner debugProbeRunner,
            ProcessBpmnFormStageBindingSync formStageBindingSync) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.functionUnitRepository = functionUnitRepository;
        this.staleIdFixer = staleIdFixer;
        this.bpmnValidator = bpmnValidator;
        this.simulationHelper = simulationHelper;
        this.debugProbeRunner = debugProbeRunner;
        this.formStageBindingSync = formStageBindingSync;
    }

    /**
     * Backward-compatible constructor for existing unit/property tests.
     *
     * <p>仅注入 4 个仓库；缺失的调试依赖（FormTableBinding/Action 仓库、JdbcTemplate、ObjectMapper）
     * 以 null 占位，与拆分前的行为一致（仅调试场景使用，校验/解析/模拟不受影响）。
     * {@code formStageBindingSync} 同样以 null 占位——依赖 {@code FormStageBindingRepository}，
     * 该仓库在这个精简构造器里没有对应入参；{@code save()} 对 null 直接跳过同步，不影响其余行为。</p>
     */
    public ProcessDesignComponentImpl(
            ProcessDefinitionRepository processDefinitionRepository,
            FunctionUnitRepository functionUnitRepository,
            TableDefinitionRepository tableDefinitionRepository,
            FormDefinitionRepository formDefinitionRepository) {
        this(
                processDefinitionRepository,
                functionUnitRepository,
                new ProcessBpmnStaleIdFixer(tableDefinitionRepository, formDefinitionRepository, null),
                new ProcessBpmnValidator(tableDefinitionRepository, formDefinitionRepository, testI18nService()),
                new ProcessSimulationHelper(tableDefinitionRepository),
                new ProcessDebugProbeRunner(formDefinitionRepository, null, null, null, null),
                null);
    }

    /**
     * 落库前修正陈旧 ID。委托 {@link ProcessBpmnStaleIdFixer}；保留包级可见的门面入口，
     * 供同包 {@code VersionComponentImpl}（快照恢复场景）调用，签名不变。
     */
    String fixStaleIds(Long functionUnitId, String bpmnXml) {
        return staleIdFixer.fixStaleIds(functionUnitId, bpmnXml);
    }

    @Override
    @Transactional
    public ProcessDefinition save(Long functionUnitId, String bpmnXml, boolean allowEmpty) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));


        // Auto-correct stale IDs (formId, subTableId, actionIds) using name lookup
        bpmnXml = fixStaleIds(functionUnitId, bpmnXml);

        ProcessDefinition existing = processDefinitionRepository
                .findByFunctionUnitId(functionUnitId)
                .orElse(null);

        // 空图护栏：把已存的非空流程整体覆盖成空图，只有调用方显式声明 allowEmpty 才放行。
        // 2026-07-31 dev FU 50030 即被设计器 2s 自动保存覆盖成空 <bpmn:process/>，
        // 且本表只存当前版本、无历史，覆盖即不可恢复。前端同一判定见
        // frontend/developer-workstation/src/utils/bpmnDiagramContent.ts。
        if (!allowEmpty && bpmnValidator.isEmptyDiagram(bpmnXml)) {
            String existingXml = existing == null ? null : XmlEncodingUtil.smartDecode(existing.getBpmnXml());
            if (!bpmnValidator.isEmptyDiagram(existingXml)) {
                log.warn("Blocked empty-diagram overwrite for functionUnitId={} (existing process is not empty)",
                        functionUnitId);
                throw new DeveloperBusinessException("EMPTY_PROCESS_OVERWRITE_BLOCKED",
                        "Refusing to overwrite the existing non-empty process definition with an empty diagram. "
                                + "Confirm the deletion in the designer (explicit save) if this is intended.");
            }
        }

        ValidationResult lastTaskTopo = validateLastTaskAssigneeTopology(bpmnXml);
        if (!lastTaskTopo.isValid()) {
            String detail = lastTaskTopo.getErrors().stream()
                    .map(ValidationResult.ValidationError::getMessage)
                    .collect(Collectors.joining("; "));
            throw new DeveloperBusinessException("LAST_TASK_ANCHOR_TOPOLOGY", detail);
        }

        ProcessDefinition processDefinition = existing != null
                ? existing
                : ProcessDefinition.builder()
                        .functionUnit(functionUnit)
                        .functionUnitVersionId(functionUnitId)
                        .build();

        // 使用Base64编码存储XML，避免特殊字符转义问题
        String encodedXml = XmlEncodingUtil.encode(bpmnXml);
        processDefinition.setBpmnXml(encodedXml);

        ProcessDefinition saved = processDefinitionRepository.save(processDefinition);

        // Keep dw_form_stage_bindings in sync with whatever the Bind Process Node dialog just
        // wrote into the BPMN extension properties — that dialog only ever touches the XML, and
        // this table is the only thing user-portal's runtime Task Form resolution reads.
        if (formStageBindingSync != null) {
            try {
                formStageBindingSync.sync(functionUnitId, bpmnXml);
            } catch (RuntimeException e) {
                log.warn("Form-stage-binding sync failed for functionUnitId={}: {}",
                        functionUnitId, e.getMessage());
            }
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessDefinition getByFunctionUnitId(Long functionUnitId) {
        Optional<ProcessDefinition> optional = processDefinitionRepository.findByFunctionUnitId(functionUnitId);

        // 如果流程定义不存在，返回 null 而不是抛出异常
        // 这允许前端创建新的流程定义
        if (optional.isEmpty()) {
            log.debug("ProcessDefinition not found for functionUnitId={}, returning null", functionUnitId);
            return null;
        }

        ProcessDefinition processDefinition = optional.get();

        // 智能解码：兼容旧数据（未编码）和新数据（Base64编码）
        String decodedXml = XmlEncodingUtil.smartDecode(processDefinition.getBpmnXml());
        processDefinition.setBpmnXml(decodedXml);

        return processDefinition;
    }

    @Override
    public ValidationResult validate(String bpmnXml) {
        return bpmnValidator.validate(bpmnXml);
    }

    @Override
    public Map<String, Object> simulate(Long functionUnitId, String bpmnXml, Map<String, Object> variables) {
        return simulationHelper.simulate(functionUnitId, bpmnXml, variables);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> debugLookupProbe(Long functionUnitId, Map<String, Object> request) {
        return debugProbeRunner.debugLookupProbe(functionUnitId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> debugRunAction(Long functionUnitId, Map<String, Object> request) {
        ProcessDefinition process = getByFunctionUnitId(functionUnitId);
        return debugProbeRunner.debugRunAction(functionUnitId, request, process);
    }

    @Override
    public Map<String, Object> parseBpmnXml(String bpmnXml) {
        return bpmnValidator.parseBpmnXml(bpmnXml);
    }

    @Override
    public ValidationResult validateMultiInstance(String bpmnXml, Long functionUnitId) {
        return bpmnValidator.validateMultiInstance(bpmnXml, functionUnitId);
    }

    @Override
    public ValidationResult validateLastTaskAssigneeTopology(String bpmnXml) {
        return bpmnValidator.validateLastTaskAssigneeTopology(bpmnXml);
    }

    private static I18nService testI18nService() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        return new I18nServiceImpl(messageSource);
    }
}
