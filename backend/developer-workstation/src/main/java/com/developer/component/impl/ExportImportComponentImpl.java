package com.developer.component.impl;

import com.developer.component.ExportImportComponent;
import com.developer.dto.ValidationResult;
import com.developer.repository.FunctionUnitRepository;
import com.developer.util.BpmnLastTaskAssigneeTopologyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Export/import component implementation.
 *
 * <p>门面：保留接口全部 public 方法签名，导出/导入/解析职责分别委托给
 * {@link FunctionUnitExporter}、{@link FunctionUnitImporter}、{@link ExportImportPackageParser}。
 * 校验/冲突检查仍在门面内编排（依赖解析协作类）。
 */
@Component
@RequiredArgsConstructor
public class ExportImportComponentImpl implements ExportImportComponent {

    private final FunctionUnitRepository functionUnitRepository;
    private final ExportImportPackageParser packageParser;
    private final FunctionUnitExporter functionUnitExporter;
    private final FunctionUnitImporter functionUnitImporter;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportFunctionUnit(Long functionUnitId) {
        return functionUnitExporter.exportFunctionUnit(functionUnitId);
    }

    @Override
    @Transactional
    public Map<String, Object> importFunctionUnit(MultipartFile file, String conflictStrategy) {
        return functionUnitImporter.importFunctionUnit(file, conflictStrategy);
    }

    @Override
    public Map<String, Object> validateImportPackage(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            Map<String, Object> packageData = packageParser.parseImportPackage(file);

            // Same as import: manifest.json (new) or metadata.json (legacy)
            Map<String, Object> descriptor = packageParser.resolvePackageDescriptor(packageData);
            if (descriptor == null) {
                errors.add("Missing manifest.json or metadata.json");
            } else {
                Object nameVal = descriptor.get("name");
                if (nameVal == null || (nameVal instanceof String s && s.isBlank())) {
                    errors.add("Manifest or metadata missing name field");
                }
            }

            // Validate process
            if (!packageData.containsKey("process")) {
                warnings.add("Package does not contain a process definition");
            } else {
                String bpmnXml = (String) packageData.get("process");
                if (bpmnXml != null && !bpmnXml.isBlank()) {
                    ValidationResult topo = BpmnLastTaskAssigneeTopologyValidator.validate(bpmnXml);
                    for (ValidationResult.ValidationError e : topo.getErrors()) {
                        errors.add(e.getMessage());
                    }
                }
            }

            result.put("valid", errors.isEmpty());
            result.put("errors", errors);
            result.put("warnings", warnings);
        } catch (Exception e) {
            result.put("valid", false);
            result.put("errors", List.of("Failed to parse import package: " + e.getMessage()));
        }

        return result;
    }

    @Override
    public Map<String, Object> checkConflicts(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();

        try {
            Map<String, Object> packageData = packageParser.parseImportPackage(file);
            Map<String, Object> descriptor = packageParser.resolvePackageDescriptor(packageData);
            if (descriptor == null) {
                result.put("hasConflicts", false);
                result.put("conflicts", conflicts);
                result.put("error", "Missing manifest.json or metadata.json");
                return result;
            }
            Object nameVal = descriptor.get("name");
            if (!(nameVal instanceof String name) || name.isBlank()) {
                result.put("hasConflicts", false);
                result.put("conflicts", conflicts);
                result.put("error", "Manifest or metadata missing name field");
                return result;
            }

            if (functionUnitRepository.existsByName(name)) {
                Map<String, Object> conflict = new HashMap<>();
                conflict.put("type", "FUNCTION_UNIT");
                conflict.put("field", "name");
                conflict.put("name", name);
                conflict.put("message", "Function unit name already exists");
                conflicts.add(conflict);
            }

            Object codeVal = descriptor.get("code");
            if (codeVal instanceof String importCode && !importCode.isBlank()
                    && functionUnitRepository.existsByCode(importCode)) {
                Map<String, Object> conflict = new HashMap<>();
                conflict.put("type", "FUNCTION_UNIT");
                conflict.put("field", "code");
                conflict.put("code", importCode);
                conflict.put("message", "Function unit code already exists");
                conflicts.add(conflict);
            }

            result.put("hasConflicts", !conflicts.isEmpty());
            result.put("conflicts", conflicts);
        } catch (Exception e) {
            result.put("error", "Failed to check conflicts: " + e.getMessage());
        }

        return result;
    }
}
