package com.developer.component.impl;

import com.developer.component.ExportImportComponent;
import com.developer.dto.ExportManifest;
import com.developer.entity.*;
import com.developer.enums.ActionType;
import com.developer.enums.DataType;
import com.developer.enums.FormType;
import com.developer.enums.TableType;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.*;
import com.developer.util.XmlEncodingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
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
    
    @Value("${platform.version:1.0.0}")
    private String platformVersion;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportFunctionUnit(Long functionUnitId) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            // 用于计算校验和的内容
            Map<String, byte[]> fileContents = new LinkedHashMap<>();
            
            // 构建组件清单
            List<String> tableFiles = new ArrayList<>();
            List<String> formFiles = new ArrayList<>();
            List<String> actionFiles = new ArrayList<>();
            
            // 导出流程定义 - 解码Base64后导出原始XML
            String processFile = null;
            if (functionUnit.getProcessDefinition() != null) {
                String bpmnXml = XmlEncodingUtil.smartDecode(
                        functionUnit.getProcessDefinition().getBpmnXml());
                processFile = "process/process.bpmn";
                byte[] processData = bpmnXml.getBytes(StandardCharsets.UTF_8);
                fileContents.put(processFile, processData);
                addZipEntry(zos, processFile, processData);
            }
            
            // 导出表定义
            int tableIndex = 0;
            for (TableDefinition table : functionUnit.getTableDefinitions()) {
                String fileName = "tables/table_" + tableIndex + ".json";
                byte[] data = objectMapper.writeValueAsBytes(serializeTable(table));
                fileContents.put(fileName, data);
                addZipEntry(zos, fileName, data);
                tableFiles.add(fileName);
                tableIndex++;
            }
            
            // 导出表单定义
            int formIndex = 0;
            for (FormDefinition form : functionUnit.getFormDefinitions()) {
                String fileName = "forms/form_" + formIndex + ".json";
                byte[] data = objectMapper.writeValueAsBytes(serializeForm(form));
                fileContents.put(fileName, data);
                addZipEntry(zos, fileName, data);
                formFiles.add(fileName);
                formIndex++;
            }
            
            // 导出动作定义
            int actionIndex = 0;
            for (ActionDefinition action : functionUnit.getActionDefinitions()) {
                String fileName = "actions/action_" + actionIndex + ".json";
                byte[] data = objectMapper.writeValueAsBytes(serializeAction(action));
                fileContents.put(fileName, data);
                addZipEntry(zos, fileName, data);
                actionFiles.add(fileName);
                actionIndex++;
            }
            
            // 构建 manifest
            ExportManifest.IconInfo iconInfo = null;
            if (functionUnit.getIcon() != null) {
                iconInfo = ExportManifest.IconInfo.builder()
                        .name(functionUnit.getIcon().getName())
                        .category(functionUnit.getIcon().getCategory() != null ? 
                                functionUnit.getIcon().getCategory().name() : null)
                        .color(null) // Icon entity doesn't have color field
                        .build();
            }
            
            ExportManifest manifest = ExportManifest.builder()
                    .name(functionUnit.getName())
                    .code(functionUnit.getName().replaceAll("\\s+", "_").toLowerCase()) // Generate code from name
                    .version(functionUnit.getCurrentVersion())
                    .description(functionUnit.getDescription())
                    .exportedAt(LocalDateTime.now())
                    .exportedBy("system") // TODO: 从安全上下文获取当前用户
                    .platformVersion(platformVersion)
                    .minPlatformVersion("1.0.0")
                    .components(ExportManifest.Components.builder()
                            .process(processFile)
                            .tables(tableFiles)
                            .forms(formFiles)
                            .actions(actionFiles)
                            .build())
                    .dependencies(new ArrayList<>())
                    .icon(iconInfo)
                    .build();
            
            ObjectMapper manifestMapper = new ObjectMapper();
            manifestMapper.registerModule(new JavaTimeModule());
            manifestMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            manifestMapper.enable(SerializationFeature.INDENT_OUTPUT);
            
            byte[] manifestData = manifestMapper.writeValueAsBytes(manifest);
            fileContents.put("manifest.json", manifestData);
            addZipEntry(zos, "manifest.json", manifestData);
            
            // 生成校验和
            String checksum = generateChecksum(fileContents);
            byte[] checksumData = checksum.getBytes(StandardCharsets.UTF_8);
            addZipEntry(zos, "checksum.sha256", checksumData);
            
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("SYS_EXPORT_ERROR", "导出功能单元失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成文件内容的SHA-256校验和
     */
    private String generateChecksum(Map<String, byte[]> fileContents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder checksumBuilder = new StringBuilder();
            
            for (Map.Entry<String, byte[]> entry : fileContents.entrySet()) {
                byte[] hash = digest.digest(entry.getValue());
                String hashHex = bytesToHex(hash);
                checksumBuilder.append(hashHex).append("  ").append(entry.getKey()).append("\n");
            }
            
            return checksumBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("SYS_CHECKSUM_ERROR", "生成校验和失败: " + e.getMessage());
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Override
    @Transactional
    public Map<String, Object> importFunctionUnit(MultipartFile file, String conflictStrategy) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> packageData = parseImportPackage(file);
        
        // 优先使用 manifest.json，兼容旧的 metadata.json
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = packageData.containsKey("manifest") ? 
                (Map<String, Object>) packageData.get("manifest") :
                (Map<String, Object>) packageData.get("metadata");
        
        String name = (String) manifest.get("name");
        String code = (String) manifest.get("code");
        String version = (String) manifest.get("version");
        String description = (String) manifest.get("description");
        
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
                .description(description)
                .currentVersion(version)
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
        
        // 导入表定义
        if (packageData.containsKey("tables")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables = (List<Map<String, Object>>) packageData.get("tables");
            for (Map<String, Object> tableData : tables) {
                importTable(functionUnit, tableData);
            }
        }
        
        // 导入表单定义
        if (packageData.containsKey("forms")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> forms = (List<Map<String, Object>>) packageData.get("forms");
            for (Map<String, Object> formData : forms) {
                importForm(functionUnit, formData);
            }
        }
        
        // 导入动作定义
        if (packageData.containsKey("actions")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actions = (List<Map<String, Object>>) packageData.get("actions");
            for (Map<String, Object> actionData : actions) {
                importAction(functionUnit, actionData);
            }
        }
        
        result.put("status", "SUCCESS");
        result.put("functionUnitId", functionUnit.getId());
        result.put("name", functionUnit.getName());
        result.put("version", functionUnit.getCurrentVersion());
        return result;
    }
    
    private void importTable(FunctionUnit functionUnit, Map<String, Object> tableData) {
        TableDefinition table = TableDefinition.builder()
                .functionUnit(functionUnit)
                .tableName((String) tableData.get("tableName"))
                .tableType(TableType.valueOf((String) tableData.get("tableType")))
                .description((String) tableData.get("description"))
                .build();
        table = tableDefinitionRepository.save(table);
        
        // 导入字段定义
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) tableData.get("fields");
        if (fields != null) {
            for (Map<String, Object> fieldData : fields) {
                FieldDefinition field = FieldDefinition.builder()
                        .tableDefinition(table)
                        .fieldName((String) fieldData.get("fieldName"))
                        .dataType(DataType.valueOf((String) fieldData.get("dataType")))
                        .length(fieldData.get("length") != null ? ((Number) fieldData.get("length")).intValue() : null)
                        .nullable((Boolean) fieldData.get("nullable"))
                        .isPrimaryKey((Boolean) fieldData.get("isPrimaryKey"))
                        .build();
                table.getFieldDefinitions().add(field);
            }
            tableDefinitionRepository.save(table);
        }
    }
    
    private void importForm(FunctionUnit functionUnit, Map<String, Object> formData) {
        FormDefinition form = FormDefinition.builder()
                .functionUnit(functionUnit)
                .formName((String) formData.get("formName"))
                .formType(FormType.valueOf((String) formData.get("formType")))
                .configJson(null) // Will be set from configJson field
                .build();
        formDefinitionRepository.save(form);
    }
    
    private void importAction(FunctionUnit functionUnit, Map<String, Object> actionData) {
        ActionDefinition action = ActionDefinition.builder()
                .functionUnit(functionUnit)
                .actionName((String) actionData.get("actionName"))
                .actionType(ActionType.valueOf((String) actionData.get("actionType")))
                .configJson(null) // Will be set from configJson field
                .build();
        actionDefinitionRepository.save(action);
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
        Map<String, byte[]> rawFiles = new HashMap<>();
        
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                rawFiles.put(entry.getName(), baos.toByteArray());
            }
        } catch (IOException e) {
            throw new BusinessException("SYS_IMPORT_ERROR", "解析导入包失败: " + e.getMessage());
        }
        
        try {
            // 解析 manifest.json（新格式）或 metadata.json（旧格式）
            if (rawFiles.containsKey("manifest.json")) {
                result.put("manifest", objectMapper.readValue(rawFiles.get("manifest.json"), Map.class));
            } else if (rawFiles.containsKey("metadata.json")) {
                result.put("metadata", objectMapper.readValue(rawFiles.get("metadata.json"), Map.class));
            }
            
            // 解析流程文件
            for (String fileName : rawFiles.keySet()) {
                if (fileName.endsWith(".bpmn")) {
                    result.put("process", new String(rawFiles.get(fileName), StandardCharsets.UTF_8));
                    break;
                }
            }
            
            // 解析表定义（支持新旧两种格式）
            List<Map<String, Object>> tables = new ArrayList<>();
            if (rawFiles.containsKey("tables.json")) {
                tables = objectMapper.readValue(rawFiles.get("tables.json"), List.class);
            } else {
                for (String fileName : rawFiles.keySet()) {
                    if (fileName.startsWith("tables/") && fileName.endsWith(".json")) {
                        tables.add(objectMapper.readValue(rawFiles.get(fileName), Map.class));
                    }
                }
            }
            result.put("tables", tables);
            
            // 解析表单定义
            List<Map<String, Object>> forms = new ArrayList<>();
            if (rawFiles.containsKey("forms.json")) {
                forms = objectMapper.readValue(rawFiles.get("forms.json"), List.class);
            } else {
                for (String fileName : rawFiles.keySet()) {
                    if (fileName.startsWith("forms/") && fileName.endsWith(".json")) {
                        forms.add(objectMapper.readValue(rawFiles.get(fileName), Map.class));
                    }
                }
            }
            result.put("forms", forms);
            
            // 解析动作定义
            List<Map<String, Object>> actions = new ArrayList<>();
            if (rawFiles.containsKey("actions.json")) {
                actions = objectMapper.readValue(rawFiles.get("actions.json"), List.class);
            } else {
                for (String fileName : rawFiles.keySet()) {
                    if (fileName.startsWith("actions/") && fileName.endsWith(".json")) {
                        actions.add(objectMapper.readValue(rawFiles.get(fileName), Map.class));
                    }
                }
            }
            result.put("actions", actions);
            
            // 保存校验和用于验证
            if (rawFiles.containsKey("checksum.sha256")) {
                result.put("checksum", new String(rawFiles.get("checksum.sha256"), StandardCharsets.UTF_8));
            }
            
        } catch (IOException e) {
            throw new BusinessException("SYS_IMPORT_ERROR", "解析导入包内容失败: " + e.getMessage());
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
