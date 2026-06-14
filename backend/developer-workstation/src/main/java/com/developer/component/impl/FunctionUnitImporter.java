package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.util.BpmnIdRewriter;
import com.developer.util.BpmnLastTaskAssigneeTopologyValidator;
import com.developer.util.BpmnProcessIdRewriter;
import com.developer.util.DeveloperWorkstationSequenceSynchronizer;
import com.developer.util.XmlEncodingUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.security.SecureRandom;

/**
 * 功能单元导入编排协作类。
 * 负责导入主流程编排（创建功能单元、落库各组件、重写 BPMN、序列同步），
 * 以及命名/编码冲突解析、唯一编码生成、最后任务负责人拓扑校验。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FunctionUnitImporter {

    private final FunctionUnitRepository functionUnitRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository;
    private final EntityManager entityManager;
    private final DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;
    private final ExportImportPackageParser packageParser;
    private final FunctionUnitImportWriter importWriter;

    private record ResolvedImportIdentity(String name, String code, boolean skipped, String skipMessage) {}

    @Transactional
    public Map<String, Object> importFunctionUnit(MultipartFile file, String conflictStrategy) {
        sequenceSynchronizer.synchronizeAll();
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> packageData = packageParser.parseImportPackage(file);

        // Prefer manifest.json; fall back to legacy metadata.json
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = packageData.containsKey("manifest") ?
                (Map<String, Object>) packageData.get("manifest") :
                (Map<String, Object>) packageData.get("metadata");

        String name = (String) manifest.get("name");
        String code = (String) manifest.get("code");
        String version = (String) manifest.get("version");
        String description = (String) manifest.get("description");

        ResolvedImportIdentity resolved = resolveImportIdentity(name, code, conflictStrategy);
        if (resolved.skipped()) {
            result.put("status", "SKIPPED");
            result.put("message", resolved.skipMessage());
            return result;
        }
        name = resolved.name();
        code = resolved.code();

        // Create function unit
        FunctionUnit functionUnit = FunctionUnit.builder()
                .name(name)
                .code(code)
                .displayName(description)
                .currentVersion(version)
                .deployedAt(Instant.now()) // Set deployed_at to avoid null constraint violation
                .build();
        functionUnit = functionUnitRepository.save(functionUnit);

        Map<Long, Long> tableIdMapping = new HashMap<>();
        Map<String, Long> importedTableNameToId = new HashMap<>();
        Map<String, Map<String, FieldDefinition>> importedFieldLookup = new HashMap<>();
        if (packageData.containsKey("tables")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables = (List<Map<String, Object>>) packageData.get("tables");
            for (Map<String, Object> tableData : tables) {
                TableDefinition table = importWriter.importTable(functionUnit, tableData);
                importWriter.recordSourceIdMapping(tableData.get("tableId"), table.getId(), tableIdMapping);
                importedTableNameToId.put(table.getTableName(), table.getId());
                Map<String, FieldDefinition> fieldByName = new HashMap<>();
                for (FieldDefinition field : table.getFieldDefinitions()) {
                    fieldByName.put(field.getFieldName(), field);
                }
                importedFieldLookup.put(table.getTableName(), fieldByName);
            }
            importWriter.importForeignKeys(tables, importedTableNameToId, importedFieldLookup);
        }

        if (packageData.containsKey("tableRelations")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tableRelations = (List<Map<String, Object>>) packageData.get("tableRelations");
            importWriter.importTableRelations(functionUnit, tableRelations, importedTableNameToId);
        }

        Map<Long, Long> formIdMapping = new HashMap<>();
        Map<String, Long> importedFormNameToId = new HashMap<>();
        List<Map<String, Object>> formDataList = new ArrayList<>();
        if (packageData.containsKey("forms")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> forms = (List<Map<String, Object>>) packageData.get("forms");
            formDataList.addAll(forms);
            for (Map<String, Object> formData : forms) {
                FormDefinition form = importWriter.importFormShell(functionUnit, formData, importedTableNameToId);
                importWriter.recordSourceIdMapping(formData.get("formId"), form.getId(), formIdMapping);
                importedFormNameToId.put(form.getFormName(), form.getId());
            }
            for (Map<String, Object> formData : formDataList) {
                Object sourceFormIdObj = formData.get("formId");
                if (!(sourceFormIdObj instanceof Number sourceFormId)) {
                    continue;
                }
                Long newFormId = formIdMapping.get(sourceFormId.longValue());
                if (newFormId == null) {
                    continue;
                }
                FormDefinition form = formDefinitionRepository.findById(newFormId)
                        .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", newFormId));
                importWriter.finalizeFormImport(form, formData, importedTableNameToId);
            }
        }

        Map<Long, Long> actionIdMapping = new HashMap<>();
        if (packageData.containsKey("actions")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actions = (List<Map<String, Object>>) packageData.get("actions");
            for (Map<String, Object> actionData : actions) {
                var action = importWriter.importAction(functionUnit, actionData);
                importWriter.recordSourceIdMapping(actionData.get("actionId"), action.getId(), actionIdMapping);
            }
        }

        if (packageData.containsKey("decisions")) {
            @SuppressWarnings("unchecked")
            List<String> decisions = (List<String>) packageData.get("decisions");
            for (String dmnXml : decisions) {
                importWriter.importDecision(functionUnit, dmnXml);
            }
        }

        // Write process after tables/forms/actions import; rewrite old BPMN IDs (same as clone)
        if (packageData.containsKey("process")) {
            String bpmnXml = (String) packageData.get("process");
            String rewrittenBpmn = BpmnIdRewriter.rewrite(
                    bpmnXml,
                    tableIdMapping,
                    formIdMapping,
                    actionIdMapping,
                    importedTableNameToId,
                    importedFormNameToId);
            rewrittenBpmn = BpmnProcessIdRewriter.rewriteToFunctionUnitCode(rewrittenBpmn, functionUnit.getCode());
            assertLastTaskAssigneeTopologyOrThrow(XmlEncodingUtil.smartDecode(rewrittenBpmn));
            ProcessDefinition process = ProcessDefinition.builder()
                    .functionUnit(functionUnit)
                    .functionUnitVersionId(functionUnit.getId())
                    .bpmnXml(XmlEncodingUtil.encode(rewrittenBpmn))
                    .build();
            processDefinitionRepository.save(process);
        }

        sequenceSynchronizer.synchronizeAll();
        entityManager.flush();

        result.put("status", "SUCCESS");
        result.put("functionUnitId", functionUnit.getId());
        result.put("name", functionUnit.getName());
        result.put("version", functionUnit.getCurrentVersion());
        return result;
    }

    /**
     * Import/preview matches save/deploy: LAST_TASK_ASSIGNEE anchor requires single incoming flow.
     */
    private void assertLastTaskAssigneeTopologyOrThrow(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return;
        }
        ValidationResult topo = BpmnLastTaskAssigneeTopologyValidator.validate(bpmnXml);
        if (!topo.isValid()) {
            String detail = topo.getErrors().stream()
                    .map(ValidationResult.ValidationError::getMessage)
                    .collect(Collectors.joining("; "));
            throw new DeveloperBusinessException("LAST_TASK_ANCHOR_TOPOLOGY", detail);
        }
    }

    private ResolvedImportIdentity resolveImportIdentity(String manifestName, String manifestCode, String conflictStrategy) {
        String name = manifestName;
        String manifestCodeNormalized = manifestCode != null && !manifestCode.isBlank() ? manifestCode : null;
        boolean nameExists = functionUnitRepository.existsByName(name);
        boolean codeExists = manifestCodeNormalized != null && functionUnitRepository.existsByCode(manifestCodeNormalized);

        switch (conflictStrategy) {
            case "SKIP" -> {
                if (nameExists) {
                    return new ResolvedImportIdentity(name, manifestCodeNormalized, true,
                            "Function unit already exists, skipped");
                }
                if (codeExists) {
                    return new ResolvedImportIdentity(name, manifestCodeNormalized, true,
                            "Function unit code already exists, skipped");
                }
            }
            case "OVERWRITE" -> {
                if (nameExists) {
                    deleteExistingFunctionUnitForImport(functionUnitRepository.findByName(name).orElse(null));
                } else if (codeExists) {
                    deleteExistingFunctionUnitForImport(functionUnitRepository.findByCode(manifestCodeNormalized).orElse(null));
                }
            }
            case "RENAME" -> {
                if (nameExists) {
                    name = manifestName + "_imported_" + System.currentTimeMillis();
                }
            }
            default -> throw new DeveloperBusinessException("BIZ_INVALID_STRATEGY", "Invalid conflict strategy");
        }

        String code = resolveUniqueImportCode(name, manifestCodeNormalized, conflictStrategy, nameExists, codeExists);
        return new ResolvedImportIdentity(name, code, false, null);
    }

    private String resolveUniqueImportCode(
            String resolvedName,
            String manifestCode,
            String conflictStrategy,
            boolean nameConflict,
            boolean codeConflict) {
        if ("RENAME".equals(conflictStrategy) && (nameConflict || codeConflict)) {
            return generateImportCode(resolvedName);
        }
        if ("OVERWRITE".equals(conflictStrategy) && manifestCode != null && !functionUnitRepository.existsByCode(manifestCode)) {
            return manifestCode;
        }
        if (manifestCode != null && !functionUnitRepository.existsByCode(manifestCode)) {
            return manifestCode;
        }
        return generateImportCode(resolvedName);
    }

    private void deleteExistingFunctionUnitForImport(FunctionUnit existing) {
        if (existing == null || existing.getId() == null) {
            return;
        }
        Long functionUnitId = existing.getId();
        functionUnitDevGroupAssignmentRepository.deleteByFunctionUnitId(functionUnitId);
        functionUnitRepository.deleteById(functionUnitId);
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Generate unique code on import
     */
    private String generateImportCode(String functionUnitName) {
        String prefix = normalizeCodePrefix(functionUnitName);
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        SecureRandom random = new SecureRandom();
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";

        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder randomPart = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                randomPart.append(chars.charAt(random.nextInt(chars.length())));
            }
            String code = prefix + "-" + datePart + "-" + randomPart;
            if (!functionUnitRepository.existsByCode(code)) {
                return code;
            }
        }
        return prefix + "-" + datePart + "-" + (System.currentTimeMillis() % 1000000);
    }

    /**
     * Same prefix sanitization as FunctionUnit create (Flowable/BPMN XML Name + total length limits).
     */
    private String normalizeCodePrefix(String name) {
        final int maxPrefixLen = 34;

        if (name == null) {
            return "fu";
        }
        String raw = name.trim();
        if (raw.isEmpty()) {
            return "fu";
        }

        StringBuilder out = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            char mapped;
            if (Character.isLetterOrDigit(c)) {
                mapped = Character.toLowerCase(c);
            } else if (c == '_' || c == '-' || c == '.') {
                mapped = c;
            } else if (Character.isWhitespace(c)) {
                mapped = '-';
            } else {
                continue;
            }
            if ((mapped == '-' || mapped == '_' || mapped == '.') && mapped == prev) {
                continue;
            }
            out.append(mapped);
            prev = mapped;
            if (out.length() >= maxPrefixLen + 8) {
                break;
            }
        }

        String s = out.toString();
        s = s.replaceAll("^[-_.]+", "").replaceAll("[-_.]+$", "");
        if (s.isEmpty()) {
            return "fu";
        }

        char first = s.charAt(0);
        boolean firstOk = (first >= 'a' && first <= 'z') || first == '_';
        if (!firstOk) {
            s = "fu-" + s;
        }
        s = s.replaceAll("^[-_.]+", "");
        if (s.isEmpty()) {
            return "fu";
        }

        if (s.length() > maxPrefixLen) {
            s = s.substring(0, maxPrefixLen);
            s = s.replaceAll("[-_.]+$", "");
            if (s.isEmpty()) {
                return "fu";
            }
        }

        if (s.length() >= 3 && s.regionMatches(true, 0, "xml", 0, 3)) {
            s = "fu-" + s;
            if (s.length() > maxPrefixLen) {
                s = s.substring(0, maxPrefixLen).replaceAll("[-_.]+$", "");
                if (s.isEmpty()) {
                    return "fu";
                }
            }
        }

        return s;
    }
}
