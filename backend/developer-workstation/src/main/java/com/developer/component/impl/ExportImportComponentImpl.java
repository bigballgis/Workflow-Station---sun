package com.developer.component.impl;

import com.developer.component.ExportImportComponent;
import com.developer.entity.*;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.*;
import com.developer.util.XmlEncodingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * 导入导出组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExportImportComponentImpl implements ExportImportComponent {
    
    private final FunctionUnitRepository functionUnitRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportFunctionUnit(Long functionUnitId) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            // 导出功能单元元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("name", functionUnit.getName());
            metadata.put("description", functionUnit.getDescription());
            metadata.put("version", functionUnit.getCurrentVersion());
            metadata.put("exportTime", System.currentTimeMillis());
            addZipEntry(zos, "metadata.json", objectMapper.writeValueAsBytes(metadata));
            
            // 导出流程定义 - 解码Base64后导出原始XML
            if (functionUnit.getProcessDefinition() != null) {
                String bpmnXml = XmlEncodingUtil.smartDecode(
                        functionUnit.getProcessDefinition().getBpmnXml());
                addZipEntry(zos, "process.bpmn", bpmnXml.getBytes());
            }
            
            // 导出表定义
            List<Map<String, Object>> tables = new ArrayList<>();
            for (TableDefinition table : functionUnit.getTableDefinitions()) {
                tables.add(serializeTable(table));
            }
            addZipEntry(zos, "tables.json", objectMapper.writeValueAsBytes(tables));
            
            // 导出表单定义
            List<Map<String, Object>> forms = new ArrayList<>();
            for (FormDefinition form : functionUnit.getFormDefinitions()) {
                forms.add(serializeForm(form));
            }
            addZipEntry(zos, "forms.json", objectMapper.writeValueAsBytes(forms));
            
            // 导出动作定义
            List<Map<String, Object>> actions = new ArrayList<>();
            for (ActionDefinition action : functionUnit.getActionDefinitions()) {
                actions.add(serializeAction(action));
            }
            addZipEntry(zos, "actions.json", objectMapper.writeValueAsBytes(actions));
            
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("SYS_EXPORT_ERROR", "导出功能单元失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> importFunctionUnit(MultipartFile file, String conflictStrategy) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> packageData = parseImportPackage(file);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) packageData.get("metadata");
        String name = (String) metadata.get("name");
        
        // 检查冲突
        boolean exists = functionUnitRepository.existsByName(name);
        if (exists) {
            switch (conflictStrategy) {
                case "SKIP":
                    result.put("status", "SKIPPED");
                    result.put("message", "功能单元已存在，已跳过");
                    return result;
                case "OVERWRITE":
                    FunctionUnit existing = functionUnitRepository.findByName(name).orElse(null);
                    if (existing != null) {
                        functionUnitRepository.delete(existing);
                    }
                    break;
                case "RENAME":
                    name = name + "_imported_" + System.currentTimeMillis();
                    break;
                default:
                    throw new BusinessException("BIZ_INVALID_STRATEGY", "无效的冲突策略");
            }
        }
        
        // 创建功能单元
        FunctionUnit functionUnit = FunctionUnit.builder()
                .name(name)
                .description((String) metadata.get("description"))
                .build();
        functionUnit = functionUnitRepository.save(functionUnit);
        
        // 导入流程 - 使用Base64编码存储
        if (packageData.containsKey("process")) {
            String bpmnXml = (String) packageData.get("process");
            ProcessDefinition process = ProcessDefinition.builder()
                    .functionUnit(functionUnit)
                    .bpmnXml(XmlEncodingUtil.encode(bpmnXml))
                    .build();
            processDefinitionRepository.save(process);
        }
        
        result.put("status", "SUCCESS");
        result.put("functionUnitId", functionUnit.getId());
        result.put("name", functionUnit.getName());
        return result;
    }

    @Override
    public Map<String, Object> validateImportPackage(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            Map<String, Object> packageData = parseImportPackage(file);
            
            // 验证元数据
            if (!packageData.containsKey("metadata")) {
                errors.add("缺少元数据文件 metadata.json");
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) packageData.get("metadata");
                if (!metadata.containsKey("name")) {
                    errors.add("元数据缺少 name 字段");
                }
            }
            
            // 验证流程
            if (!packageData.containsKey("process")) {
                warnings.add("包中没有流程定义");
            }
            
            result.put("valid", errors.isEmpty());
            result.put("errors", errors);
            result.put("warnings", warnings);
        } catch (Exception e) {
            result.put("valid", false);
            result.put("errors", List.of("解析导入包失败: " + e.getMessage()));
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> checkConflicts(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        
        try {
            Map<String, Object> packageData = parseImportPackage(file);
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) packageData.get("metadata");
            String name = (String) metadata.get("name");
            
            if (functionUnitRepository.existsByName(name)) {
                Map<String, Object> conflict = new HashMap<>();
                conflict.put("type", "FUNCTION_UNIT");
                conflict.put("name", name);
                conflict.put("message", "功能单元名称已存在");
                conflicts.add(conflict);
            }
            
            result.put("hasConflicts", !conflicts.isEmpty());
            result.put("conflicts", conflicts);
        } catch (Exception e) {
            result.put("error", "检查冲突失败: " + e.getMessage());
        }
        
        return result;
    }

    private void addZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
    
    private Map<String, Object> parseImportPackage(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                
                String entryName = entry.getName();
                byte[] data = baos.toByteArray();
                
                if ("metadata.json".equals(entryName)) {
                    result.put("metadata", objectMapper.readValue(data, Map.class));
                } else if ("process.bpmn".equals(entryName)) {
                    result.put("process", new String(data));
                } else if ("tables.json".equals(entryName)) {
                    result.put("tables", objectMapper.readValue(data, List.class));
                } else if ("forms.json".equals(entryName)) {
                    result.put("forms", objectMapper.readValue(data, List.class));
                } else if ("actions.json".equals(entryName)) {
                    result.put("actions", objectMapper.readValue(data, List.class));
                }
            }
        } catch (IOException e) {
            throw new BusinessException("SYS_IMPORT_ERROR", "解析导入包失败: " + e.getMessage());
        }
        
        return result;
    }
    
    private Map<String, Object> serializeTable(TableDefinition table) {
        Map<String, Object> map = new HashMap<>();
        map.put("tableName", table.getTableName());
        map.put("tableType", table.getTableType().name());
        map.put("description", table.getDescription());
        map.put("fields", table.getFieldDefinitions().stream().map(this::serializeField).toList());
        return map;
    }
    
    private Map<String, Object> serializeField(FieldDefinition field) {
        Map<String, Object> map = new HashMap<>();
        map.put("fieldName", field.getFieldName());
        map.put("dataType", field.getDataType().name());
        map.put("length", field.getLength());
        map.put("nullable", field.getNullable());
        map.put("isPrimaryKey", field.getIsPrimaryKey());
        return map;
    }
    
    private Map<String, Object> serializeForm(FormDefinition form) {
        Map<String, Object> map = new HashMap<>();
        map.put("formName", form.getFormName());
        map.put("formType", form.getFormType().name());
        map.put("configJson", form.getConfigJson());
        return map;
    }
    
    private Map<String, Object> serializeAction(ActionDefinition action) {
        Map<String, Object> map = new HashMap<>();
        map.put("actionName", action.getActionName());
        map.put("actionType", action.getActionType().name());
        map.put("configJson", action.getConfigJson());
        return map;
    }
}
