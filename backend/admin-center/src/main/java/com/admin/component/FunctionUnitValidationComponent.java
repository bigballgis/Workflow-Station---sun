package com.admin.component;

import com.admin.dto.response.ValidationResult;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.entity.FunctionUnitDependency;
import com.admin.enums.ContentType;
import com.admin.enums.DependencyType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 功能单元发布前校验（静态结构 + 依赖 + Flowable 试部署）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitValidationComponent {

    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitContentRepository contentRepository;
    private final FunctionUnitDependencyRepository dependencyRepository;
    private final ProcessDeploymentComponent processDeploymentComponent;

    public ValidationResult validate(String functionUnitId) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new AdminBusinessException("FUNCTION_UNIT_NOT_FOUND",
                        "功能单元不存在: " + functionUnitId));

        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .fileFormatValid(true)
                .integrityValid(true)
                .signatureValid(true)
                .bpmnSyntaxValid(true)
                .dataTableValid(true)
                .formConfigValid(true)
                .dependenciesValid(true)
                .engineDeployValid(true)
                .functionUnitId(functionUnitId)
                .status(functionUnit.getStatus().name())
                .build();

        validateContents(functionUnitId, result);
        validateDependencies(functionUnit, result);
        validateEngineDeploy(functionUnitId, result);

        result.setValid(result.getErrors() == null || result.getErrors().isEmpty());
        return result;
    }

    private void validateContents(String functionUnitId, ValidationResult result) {
        List<FunctionUnitContent> contents = contentRepository.findByFunctionUnitId(functionUnitId);
        boolean hasProcess = false;

        for (FunctionUnitContent content : contents) {
            if (content.getContentType() == ContentType.PROCESS) {
                hasProcess = true;
                if (!validateBpmnContent(content.getContentData(), content.getContentName(), result)) {
                    result.setBpmnSyntaxValid(false);
                }
            } else if (content.getContentType() == ContentType.FORM) {
                if (!validateFormContent(content.getContentData(), content.getContentName(), result)) {
                    result.setFormConfigValid(false);
                }
            } else if (content.getContentType() == ContentType.DATA_TABLE) {
                if (!validateDataTableContent(content.getContentData(), content.getContentName(), result)) {
                    result.setDataTableValid(false);
                }
            }
        }

        if (!hasProcess) {
            result.addWarning("功能单元未包含流程定义，部署后无法发起流程");
        }
    }

    private boolean validateBpmnContent(String bpmnXml, String contentName, ValidationResult result) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            result.addError("BPMN_SYNTAX", contentName, "BPMN 内容为空");
            return false;
        }
        if (!bpmnXml.contains("definitions") || !bpmnXml.contains("process")) {
            result.addError("BPMN_SYNTAX", contentName, "无效的 BPMN 格式");
            return false;
        }
        if (!bpmnXml.contains("startEvent")) {
            result.addError("BPMN_SYNTAX", contentName, "流程缺少开始事件 (startEvent)");
            return false;
        }
        if (!bpmnXml.contains("endEvent")) {
            result.addError("BPMN_SYNTAX", contentName, "流程缺少结束事件 (endEvent)");
            return false;
        }
        return true;
    }

    private boolean validateFormContent(String formConfig, String contentName, ValidationResult result) {
        if (formConfig == null || formConfig.isBlank()) {
            return true;
        }
        String trimmed = formConfig.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            result.addError("FORM_CONFIG", contentName, "无效的表单配置 JSON");
            return false;
        }
        return true;
    }

    private boolean validateDataTableContent(String tableDefinition, String contentName, ValidationResult result) {
        if (tableDefinition == null || tableDefinition.isBlank()) {
            return true;
        }
        String upperDef = tableDefinition.toUpperCase();
        if (!upperDef.contains("CREATE TABLE") && !upperDef.contains("ALTER TABLE")
                && !tableDefinition.trim().startsWith("{")) {
            result.addError("DATA_TABLE", contentName, "无效的数据表定义");
            return false;
        }
        return true;
    }

    private void validateDependencies(FunctionUnit functionUnit, ValidationResult result) {
        List<FunctionUnitDependency> dependencies = dependencyRepository.findByFunctionUnitId(functionUnit.getId());
        for (FunctionUnitDependency dep : dependencies) {
            if (dep.getDependencyType() != DependencyType.REQUIRED) {
                continue;
            }
            Optional<FunctionUnit> existing = functionUnitRepository.findLatestByCode(dep.getDependencyCode());
            if (existing.isEmpty()) {
                result.addError("MISSING_DEPENDENCY", dep.getDependencyCode(),
                        "缺少必需依赖: " + dep.getDependencyCode() + ":" + dep.getDependencyVersion());
                result.setDependenciesValid(false);
                continue;
            }
            FunctionUnit resolved = existing.get();
            if (resolved.getStatus() != FunctionUnitStatus.DEPLOYED) {
                result.addError("DEPENDENCY_NOT_DEPLOYED", dep.getDependencyCode(),
                        "依赖 " + dep.getDependencyCode() + " 尚未部署 (当前状态: " + resolved.getStatus() + ")");
                result.setDependenciesValid(false);
            } else if (!resolved.isEnabled()) {
                result.addError("DEPENDENCY_DISABLED", dep.getDependencyCode(),
                        "依赖 " + dep.getDependencyCode() + " 已部署但未启用");
                result.setDependenciesValid(false);
            }
        }
    }

    private void validateEngineDeploy(String functionUnitId, ValidationResult result) {
        ProcessDeploymentComponent.ProcessDeploymentResult dryRun =
                processDeploymentComponent.dryRunDeployFunctionUnitProcess(functionUnitId);

        if (dryRun.isSuccess()
                || (dryRun.getMessage() != null && dryRun.getMessage().contains("没有流程定义"))) {
            return;
        }

        result.setEngineDeployValid(false);
        if (dryRun.getMessage() != null && dryRun.getMessage().contains("Flowable")) {
            result.addError("ENGINE_UNAVAILABLE", "workflow-engine",
                    dryRun.getMessage());
        } else if (dryRun.getErrors() != null) {
            for (String error : dryRun.getErrors()) {
                result.addError("ENGINE_DEPLOY", "process", error);
            }
        } else {
            result.addError("ENGINE_DEPLOY", "process", dryRun.getMessage());
        }
    }
}
