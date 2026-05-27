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
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Pre-publish validation (structure, dependencies, and Flowable dry-run deploy).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitValidationComponent {

    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitContentRepository contentRepository;
    private final FunctionUnitDependencyRepository dependencyRepository;
    private final ProcessDeploymentComponent processDeploymentComponent;
    private final I18nService i18nService;

    public ValidationResult validate(String functionUnitId) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new AdminBusinessException("FUNCTION_UNIT_NOT_FOUND",
                        i18nService.getMessage("admin.fu.not_found_by_id", functionUnitId)));

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
            result.addWarning(i18nService.getMessage("admin.fu.validate_warning_no_process"));
        }
    }

    private boolean validateBpmnContent(String bpmnXml, String contentName, ValidationResult result) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            result.addError("BPMN_SYNTAX", contentName, i18nService.getMessage("admin.fu.bpmn_empty"));
            return false;
        }
        if (!bpmnXml.contains("definitions") || !bpmnXml.contains("process")) {
            result.addError("BPMN_SYNTAX", contentName, i18nService.getMessage("admin.fu.bpmn_invalid"));
            return false;
        }
        if (!bpmnXml.contains("startEvent")) {
            result.addError("BPMN_SYNTAX", contentName, i18nService.getMessage("admin.fu.bpmn_missing_start_event"));
            return false;
        }
        if (!bpmnXml.contains("endEvent")) {
            result.addError("BPMN_SYNTAX", contentName, i18nService.getMessage("admin.fu.bpmn_missing_end_event"));
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
            result.addError("FORM_CONFIG", contentName, i18nService.getMessage("admin.fu.form_config_invalid"));
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
            result.addError("DATA_TABLE", contentName, i18nService.getMessage("admin.fu.data_table_invalid"));
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
                        i18nService.getMessage("admin.fu.dependency_required_missing",
                                dep.getDependencyCode(), dep.getDependencyVersion()));
                result.setDependenciesValid(false);
                continue;
            }
            FunctionUnit resolved = existing.get();
            if (resolved.getStatus() != FunctionUnitStatus.DEPLOYED) {
                result.addError("DEPENDENCY_NOT_DEPLOYED", dep.getDependencyCode(),
                        i18nService.getMessage("admin.fu.dependency_not_deployed_status",
                                dep.getDependencyCode(), resolved.getStatus()));
                result.setDependenciesValid(false);
            } else if (!resolved.isEnabled()) {
                result.addError("DEPENDENCY_DISABLED", dep.getDependencyCode(),
                        i18nService.getMessage("admin.fu.dependency_deployed_but_disabled", dep.getDependencyCode()));
                result.setDependenciesValid(false);
            }
        }
    }

    private void validateEngineDeploy(String functionUnitId, ValidationResult result) {
        ProcessDeploymentComponent.ProcessDeploymentResult dryRun =
                processDeploymentComponent.dryRunDeployFunctionUnitProcess(functionUnitId);

        if (dryRun.isSuccess()) {
            return;
        }

        result.setEngineDeployValid(false);
        if (dryRun.isEngineUnavailable()) {
            result.addError("ENGINE_UNAVAILABLE", "workflow-engine",
                    dryRun.getMessage());
        } else if (dryRun.getErrors() != null) {
            for (String error : dryRun.getErrors()) {
                result.addError("ENGINE_DEPLOY", "process", error);
            }
        } else {
            String msg = dryRun.getMessage() != null ? dryRun.getMessage()
                    : i18nService.getMessage("admin.deploy.process.deploy_failed_summary");
            result.addError("ENGINE_DEPLOY", "process", msg);
        }
    }
}
