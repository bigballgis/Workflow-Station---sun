package com.developer.component.impl;

import com.developer.component.ExportImportComponent;
import com.developer.dto.ExportManifest;
import com.developer.dto.ValidationResult;
import com.developer.entity.*;
import com.developer.enums.ActionType;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.enums.DataType;
import com.developer.enums.FormType;
import com.developer.enums.SubMode;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.*;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.security.WorkspaceAccessAction;
import com.developer.util.BpmnLastTaskAssigneeTopologyValidator;
import com.developer.util.BpmnIdRewriter;
import com.developer.util.DeveloperWorkstationSequenceSynchronizer;
import com.developer.util.FormConfigJsonBindingIdRewriter;
import com.developer.util.XmlEncodingUtil;
import com.developer.validation.DmnXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.security.util.SecurityContextUtils;
import jakarta.persistence.EntityManager;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
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
    private final DecisionDefinitionRepository decisionDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final FormStageBindingRepository formStageBindingRepository;
    private final TableRelationRepository tableRelationRepository;
    private final DmnXmlParser dmnXmlParser;
    private final FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;
    private final FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;
    
    @Value("${platform.version:1.0.0}")
    private String platformVersion;

    private record ResolvedImportIdentity(String name, String code, boolean skipped, String skipMessage) {}

    /**
     * 获取当前操作者
     * 优先从 Spring Security Context 获取，如果无法获取则返回 "system"
     * 
     * 返回 "system" 的情况：
     * - 没有认证信息（未登录）
     * - 匿名用户
     * - 系统后台任务
     * - 获取过程中发生异常
     * 
     * @return 当前操作者用户名，如果无法获取则返回 "system"
     */
    private String getCurrentOperator() {
        try {
            return SecurityContextUtils.getCurrentUsername().orElse("system");
        } catch (Exception e) {
            log.debug("Failed to get current operator from security context: {}", e.getMessage());
        }
        return "system";
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportFunctionUnit(Long functionUnitId) {
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.VIEW);
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        List<TableDefinition> tables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
        List<FormDefinition> forms = formDefinitionRepository.findByFunctionUnitIdWithBindings(functionUnitId);
        List<ActionDefinition> actions = actionDefinitionRepository.findByFunctionUnitId(functionUnitId);
        List<DecisionDefinition> decisions = decisionDefinitionRepository.findByFunctionUnitId(functionUnitId);
        List<TableRelation> tableRelations = tableRelationRepository.findByFunctionUnitId(functionUnitId);
        Map<Long, String> tableIdToName = tables.stream()
                .collect(Collectors.toMap(TableDefinition::getId, TableDefinition::getTableName));
        Map<Long, List<FormStageBinding>> stageBindingsByFormId = new HashMap<>();
        for (FormDefinition form : forms) {
            stageBindingsByFormId.put(form.getId(), formStageBindingRepository.findByFormId(form.getId()));
        }
        ProcessDefinition processDefinition = functionUnit.getProcessDefinition();
        
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
            if (processDefinition != null) {
                String bpmnXml = XmlEncodingUtil.smartDecode(processDefinition.getBpmnXml());
                processFile = "process/process.bpmn";
                byte[] processData = bpmnXml.getBytes(StandardCharsets.UTF_8);
                fileContents.put(processFile, processData);
                addZipEntry(zos, processFile, processData);
            }
            
            // 导出表定义
            int tableIndex = 0;
            for (TableDefinition table : tables) {
                String fileName = "tables/table_" + tableIndex + ".json";
                byte[] data = objectMapper.writeValueAsBytes(serializeTable(table, tableIdToName));
                fileContents.put(fileName, data);
                addZipEntry(zos, fileName, data);
                tableFiles.add(fileName);
                tableIndex++;
            }

            // 导出表关系
            if (!tableRelations.isEmpty()) {
                String relationsFile = "relations/table_relations.json";
                List<Map<String, Object>> relationPayload = tableRelations.stream()
                        .map(rel -> serializeTableRelation(rel, tableIdToName))
                        .toList();
                byte[] relationsData = objectMapper.writeValueAsBytes(relationPayload);
                fileContents.put(relationsFile, relationsData);
                addZipEntry(zos, relationsFile, relationsData);
            }
            
            // 导出表单定义
            int formIndex = 0;
            for (FormDefinition form : forms) {
                String fileName = "forms/form_" + formIndex + ".json";
                byte[] data = objectMapper.writeValueAsBytes(
                        serializeForm(form, stageBindingsByFormId.getOrDefault(form.getId(), List.of())));
                fileContents.put(fileName, data);
                addZipEntry(zos, fileName, data);
                formFiles.add(fileName);
                formIndex++;
            }
            
            // 导出动作定义
            int actionIndex = 0;
            for (ActionDefinition action : actions) {
                String fileName = "actions/action_" + actionIndex + ".json";
                byte[] data = objectMapper.writeValueAsBytes(serializeAction(action));
                fileContents.put(fileName, data);
                addZipEntry(zos, fileName, data);
                actionFiles.add(fileName);
                actionIndex++;
            }
            
            // 导出决策定义（DMN XML 格式）
            List<String> decisionFiles = new ArrayList<>();
            int decisionIndex = 0;
            for (DecisionDefinition decision : decisions) {
                String fileName = "decisions/decision_" + decisionIndex + ".dmn";
                byte[] data = decision.getDmnXml() != null ? 
                        decision.getDmnXml().getBytes(StandardCharsets.UTF_8) : new byte[0];
                fileContents.put(fileName, data);
                addZipEntry(zos, fileName, data);
                decisionFiles.add(fileName);
                decisionIndex++;
            }
            
            // 构建 manifest
            ExportManifest.IconInfo iconInfo = null;
            if (functionUnit.getIcon() != null) {
                iconInfo = ExportManifest.IconInfo.builder()
                        .name(functionUnit.getIcon().getName())
                        .category(functionUnit.getIcon().getCategory() != null ? 
                                functionUnit.getIcon().getCategory().name() : null)
                        .color(null)
                        .svgContent(functionUnit.getIcon().getSvgContent())
                        .build();
            }
            
            ExportManifest manifest = ExportManifest.builder()
                    .name(functionUnit.getName())
                    .code(functionUnit.getCode()) // Use actual code field
                    .version(functionUnit.getCurrentVersion())
                    .description(functionUnit.getDescription())
                    .exportedAt(LocalDateTime.now())
                    .exportedBy(getCurrentOperator())
                    .platformVersion(platformVersion)
                    .minPlatformVersion("1.0.0")
                    .components(ExportManifest.Components.builder()
                            .process(processFile)
                            .tables(tableFiles)
                            .forms(formFiles)
                            .actions(actionFiles)
                            .decisions(decisionFiles)
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
            throw new DeveloperBusinessException("SYS_EXPORT_ERROR", "Failed to export function unit: " + e.getMessage());
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
            throw new DeveloperBusinessException("SYS_CHECKSUM_ERROR", "Failed to generate checksum: " + e.getMessage());
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

        ResolvedImportIdentity resolved = resolveImportIdentity(name, code, conflictStrategy);
        if (resolved.skipped()) {
            result.put("status", "SKIPPED");
            result.put("message", resolved.skipMessage());
            return result;
        }
        name = resolved.name();
        code = resolved.code();

        // 创建功能单元
        FunctionUnit functionUnit = FunctionUnit.builder()
                .name(name)
                .code(code)
                .description(description)
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
                TableDefinition table = importTable(functionUnit, tableData);
                recordSourceIdMapping(tableData.get("tableId"), table.getId(), tableIdMapping);
                importedTableNameToId.put(table.getTableName(), table.getId());
                Map<String, FieldDefinition> fieldByName = new HashMap<>();
                for (FieldDefinition field : table.getFieldDefinitions()) {
                    fieldByName.put(field.getFieldName(), field);
                }
                importedFieldLookup.put(table.getTableName(), fieldByName);
            }
            importForeignKeys(tables, importedTableNameToId, importedFieldLookup);
        }

        if (packageData.containsKey("tableRelations")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tableRelations = (List<Map<String, Object>>) packageData.get("tableRelations");
            importTableRelations(functionUnit, tableRelations, importedTableNameToId);
        }

        Map<Long, Long> formIdMapping = new HashMap<>();
        Map<String, Long> importedFormNameToId = new HashMap<>();
        List<Map<String, Object>> formDataList = new ArrayList<>();
        if (packageData.containsKey("forms")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> forms = (List<Map<String, Object>>) packageData.get("forms");
            formDataList.addAll(forms);
            for (Map<String, Object> formData : forms) {
                FormDefinition form = importFormShell(functionUnit, formData, importedTableNameToId);
                recordSourceIdMapping(formData.get("formId"), form.getId(), formIdMapping);
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
                finalizeFormImport(form, formData, importedTableNameToId);
            }
        }

        Map<Long, Long> actionIdMapping = new HashMap<>();
        if (packageData.containsKey("actions")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actions = (List<Map<String, Object>>) packageData.get("actions");
            for (Map<String, Object> actionData : actions) {
                ActionDefinition action = importAction(functionUnit, actionData);
                recordSourceIdMapping(actionData.get("actionId"), action.getId(), actionIdMapping);
            }
        }

        if (packageData.containsKey("decisions")) {
            @SuppressWarnings("unchecked")
            List<String> decisions = (List<String>) packageData.get("decisions");
            for (String dmnXml : decisions) {
                importDecision(functionUnit, dmnXml);
            }
        }

        // 流程在表/表单/动作导入后再写入，并重写 BPMN 中的旧 ID 引用（与 clone 一致）
        if (packageData.containsKey("process")) {
            String bpmnXml = (String) packageData.get("process");
            String rewrittenBpmn = BpmnIdRewriter.rewrite(
                    bpmnXml,
                    tableIdMapping,
                    formIdMapping,
                    actionIdMapping,
                    importedTableNameToId,
                    importedFormNameToId);
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
    
    private void recordSourceIdMapping(Object sourceIdObj, Long newId, Map<Long, Long> mapping) {
        if (sourceIdObj instanceof Number sourceId && newId != null) {
            mapping.put(sourceId.longValue(), newId);
        }
    }

    private TableDefinition importTable(FunctionUnit functionUnit, Map<String, Object> tableData) {
        TableDefinition table = TableDefinition.builder()
                .functionUnit(functionUnit)
                .tableName((String) tableData.get("tableName"))
                .tableType(TableType.valueOf((String) tableData.get("tableType")))
                .tableDisplayName((String) tableData.get("tableDisplayName"))
                .description((String) tableData.get("description"))
                .build();
        table = tableDefinitionRepository.save(table);
        
        // 导入字段定义
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) tableData.get("fields");
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                Map<String, Object> fieldData = fields.get(i);
                Integer sortOrder = fieldData.get("sortOrder") instanceof Number number
                        ? number.intValue()
                        : i;
                Boolean nullable = fieldData.get("nullable") instanceof Boolean boolVal ? boolVal : true;
                Boolean isPrimaryKey = fieldData.get("isPrimaryKey") instanceof Boolean pkVal ? pkVal : false;
                Boolean isUnique = fieldData.get("isUnique") instanceof Boolean uniqueVal ? uniqueVal : false;
                FieldDefinition field = FieldDefinition.builder()
                        .tableDefinition(table)
                        .fieldName((String) fieldData.get("fieldName"))
                        .dataType(DataType.valueOf((String) fieldData.get("dataType")))
                        .length(fieldData.get("length") != null ? ((Number) fieldData.get("length")).intValue() : null)
                        .precision(fieldData.get("precision") != null ? ((Number) fieldData.get("precision")).intValue() : null)
                        .scale(fieldData.get("scale") != null ? ((Number) fieldData.get("scale")).intValue() : null)
                        .nullable(nullable)
                        .defaultValue((String) fieldData.get("defaultValue"))
                        .isPrimaryKey(isPrimaryKey)
                        .isUnique(isUnique)
                        .description((String) fieldData.get("description"))
                        .sortOrder(sortOrder)
                        .build();
                table.getFieldDefinitions().add(field);
            }
            tableDefinitionRepository.save(table);
        }
        return table;
    }

    private void importForeignKeys(List<Map<String, Object>> tables,
                                   Map<String, Long> importedTableNameToId,
                                   Map<String, Map<String, FieldDefinition>> importedFieldLookup) {
        for (Map<String, Object> tableData : tables) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foreignKeys = (List<Map<String, Object>>) tableData.get("foreignKeys");
            if (foreignKeys == null || foreignKeys.isEmpty()) {
                continue;
            }
            String tableName = (String) tableData.get("tableName");
            Long tableId = importedTableNameToId.get(tableName);
            if (tableId == null) {
                continue;
            }
            TableDefinition table = tableDefinitionRepository.findByIdWithFields(tableId)
                    .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", tableId));
            Map<String, FieldDefinition> fieldByName = importedFieldLookup.getOrDefault(tableName, Map.of());
            for (Map<String, Object> fkData : foreignKeys) {
                String fieldName = (String) fkData.get("fieldName");
                String refTableName = (String) fkData.get("refTableName");
                String refFieldName = (String) fkData.get("refFieldName");
                FieldDefinition field = fieldByName.get(fieldName);
                Map<String, FieldDefinition> refFieldByName = importedFieldLookup.getOrDefault(refTableName, Map.of());
                FieldDefinition refField = refFieldByName.get(refFieldName);
                Long refTableId = importedTableNameToId.get(refTableName);
                if (field == null || refField == null || refTableId == null) {
                    log.warn("Skipping foreign key import for {}.{} -> {}.{} (missing field/table)",
                            tableName, fieldName, refTableName, refFieldName);
                    continue;
                }
                TableDefinition refTable = tableDefinitionRepository.getReferenceById(refTableId);
                ForeignKey foreignKey = ForeignKey.builder()
                        .tableDefinition(table)
                        .fieldDefinition(field)
                        .refTableDefinition(refTable)
                        .refFieldDefinition(refField)
                        .onDelete(fkData.get("onDelete") instanceof String onDelete ? onDelete : "NO ACTION")
                        .onUpdate(fkData.get("onUpdate") instanceof String onUpdate ? onUpdate : "NO ACTION")
                        .build();
                table.getForeignKeys().add(foreignKey);
            }
            if (!table.getForeignKeys().isEmpty()) {
                tableDefinitionRepository.save(table);
            }
        }
    }

    private void importTableRelations(FunctionUnit functionUnit,
                                      List<Map<String, Object>> tableRelations,
                                      Map<String, Long> importedTableNameToId) {
        for (Map<String, Object> relationData : tableRelations) {
            String sourceTableName = (String) relationData.get("sourceTableName");
            String targetTableName = (String) relationData.get("targetTableName");
            Long sourceTableId = importedTableNameToId.get(sourceTableName);
            Long targetTableId = importedTableNameToId.get(targetTableName);
            if (sourceTableId == null || targetTableId == null) {
                log.warn("Skipping table relation import {} -> {} (table not found)",
                        sourceTableName, targetTableName);
                continue;
            }
            TableRelation relation = TableRelation.builder()
                    .functionUnit(functionUnit)
                    .sourceTableId(sourceTableId)
                    .sourceFieldName((String) relationData.get("sourceFieldName"))
                    .relationType((String) relationData.get("relationType"))
                    .targetTableId(targetTableId)
                    .targetFieldName((String) relationData.get("targetFieldName"))
                    .build();
            tableRelationRepository.save(relation);
        }
    }
    
    @SuppressWarnings("unchecked")
    private FormDefinition importFormShell(FunctionUnit functionUnit,
                                             Map<String, Object> formData,
                                             Map<String, Long> importedTableNameToId) {
        Map<String, Object> configJsonMap = parseConfigJsonObject(formData.get("configJson"));
        Map<String, String> fieldPermissions = parseFieldPermissions(formData.get("fieldPermissions"));
        Boolean showLiveValues = formData.get("showLiveValues") instanceof Boolean boolVal ? boolVal : true;

        FormDefinition form = FormDefinition.builder()
                .functionUnit(functionUnit)
                .formName((String) formData.get("formName"))
                .formType(FormType.valueOf((String) formData.get("formType")))
                .description((String) formData.get("description"))
                .configJson(configJsonMap != null ? configJsonMap : new HashMap<>())
                .fieldPermissions(fieldPermissions)
                .showLiveValues(showLiveValues)
                .build();

        String boundTableName = (String) formData.get("boundTableName");
        if (boundTableName != null && importedTableNameToId.containsKey(boundTableName)) {
            form.setBoundTable(tableDefinitionRepository.getReferenceById(importedTableNameToId.get(boundTableName)));
        }

        return formDefinitionRepository.save(form);
    }

    @SuppressWarnings("unchecked")
    private void finalizeFormImport(FormDefinition form,
                                    Map<String, Object> formData,
                                    Map<String, Long> importedTableNameToId) {
        Map<Long, Long> bindingIdMapping = importFormTableBindings(form, formData, importedTableNameToId);

        Map<String, Object> configJson = parseConfigJsonObject(formData.get("configJson"));
        if (configJson == null) {
            configJson = form.getConfigJson() != null ? new HashMap<>(form.getConfigJson()) : new HashMap<>();
        } else {
            configJson = new HashMap<>(configJson);
        }
        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, bindingIdMapping);
        form.setConfigJson(configJson);

        importFormStageBindings(form, formData);
        formDefinitionRepository.save(form);
    }

    private Map<Long, Long> importFormTableBindings(FormDefinition form,
                                                    Map<String, Object> formData,
                                                    Map<String, Long> importedTableNameToId) {
        Map<Long, Long> bindingIdMapping = new HashMap<>();
        Object bindingsObj = formData.get("tableBindings");
        if (!(bindingsObj instanceof List<?> bindingsList)) {
            return bindingIdMapping;
        }
        for (Object bindingObj : bindingsList) {
            if (!(bindingObj instanceof Map<?, ?> bindingMapRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> bindingData = (Map<String, Object>) bindingMapRaw;
            BindingType bindingType = BindingType.valueOf((String) bindingData.get("bindingType"));
            BindingMode bindingMode = BindingMode.valueOf((String) bindingData.get("bindingMode"));

            TableDefinition table = null;
            String tableName = (String) bindingData.get("tableName");
            if (tableName != null && importedTableNameToId.containsKey(tableName)) {
                table = tableDefinitionRepository.getReferenceById(importedTableNameToId.get(tableName));
            }

            Long relationTableId = bindingData.get("relationTableId") instanceof Number number
                    ? number.longValue() : null;
            Integer sortOrder = bindingData.get("sortOrder") instanceof Number number
                    ? number.intValue() : null;
            SubMode subMode = bindingData.get("subMode") instanceof String subModeStr
                    ? SubMode.valueOf(subModeStr) : null;

            FormTableBinding binding = FormTableBinding.builder()
                    .form(form)
                    .table(table)
                    .relationTableId(relationTableId)
                    .bindingType(bindingType)
                    .bindingMode(bindingMode)
                    .foreignKeyField((String) bindingData.get("foreignKeyField"))
                    .sortOrder(sortOrder)
                    .subMode(subMode)
                    .build();
            FormTableBinding savedBinding = formTableBindingRepository.save(binding);

            if (bindingData.get("bindingId") instanceof Number sourceBindingId) {
                bindingIdMapping.put(sourceBindingId.longValue(), savedBinding.getId());
            }
        }
        return bindingIdMapping;
    }

    private void importFormStageBindings(FormDefinition form, Map<String, Object> formData) {
        Object stageBindingsObj = formData.get("stageBindings");
        if (!(stageBindingsObj instanceof List<?> stageBindingsList)) {
            return;
        }
        for (Object stageObj : stageBindingsList) {
            if (!(stageObj instanceof Map<?, ?> stageMapRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> stageData = (Map<String, Object>) stageMapRaw;
            Boolean readOnly = stageData.get("readOnly") instanceof Boolean boolVal ? boolVal : false;
            FormStageBinding stageBinding = FormStageBinding.builder()
                    .form(form)
                    .stageId((String) stageData.get("stageId"))
                    .stageName((String) stageData.get("stageName"))
                    .readOnly(readOnly)
                    .build();
            form.getStageBindings().add(stageBinding);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigJsonObject(Object configJsonObj) {
        if (configJsonObj instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        if (configJsonObj instanceof String str && !str.isBlank()) {
            try {
                return objectMapper.readValue(str, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse configJson string: {}", e.getMessage());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseFieldPermissions(Object fieldPermissionsObj) {
        if (fieldPermissionsObj instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return result;
        }
        return new HashMap<>();
    }
    
    @SuppressWarnings("unchecked")
    private ActionDefinition importAction(FunctionUnit functionUnit, Map<String, Object> actionData) {
        Map<String, Object> configJsonMap = parseConfigJsonObject(actionData.get("configJson"));
        ActionDefinition action = ActionDefinition.builder()
                .functionUnit(functionUnit)
                .actionName((String) actionData.get("actionName"))
                .actionType(ActionType.valueOf((String) actionData.get("actionType")))
                .configJson(configJsonMap != null ? configJsonMap : new HashMap<>())
                .build();
        return actionDefinitionRepository.save(action);
    }

    /**
     * 导入决策定义（从 DMN XML）
     * 使用 DmnXmlParser 提取 decisionKey、hitPolicy，从 XML decision 元素提取 name
     * 冲突策略: 同一 functionUnit 下相同 decisionKey 则覆盖
     */
    private void importDecision(FunctionUnit functionUnit, String dmnXml) {
        if (dmnXml == null || dmnXml.isBlank()) {
            log.warn("Skipping empty DMN XML during import");
            return;
        }

        String decisionKey = dmnXmlParser.extractDecisionKey(dmnXml);
        if (decisionKey == null || decisionKey.isBlank()) {
            log.warn("Skipping DMN XML without decision key during import");
            return;
        }

        String hitPolicy = dmnXmlParser.extractHitPolicy(dmnXml);

        // Extract decision name from the model
        String decisionName = null;
        try {
            var model = dmnXmlParser.parseToModel(dmnXml);
            if (model != null && model.getDecisionName() != null) {
                decisionName = model.getDecisionName();
            }
        } catch (Exception e) {
            log.warn("Failed to parse decision name from DMN XML for key {}: {}", decisionKey, e.getMessage());
        }

        // Handle conflict: overwrite if same decisionKey exists in this functionUnit
        List<DecisionDefinition> existing = decisionDefinitionRepository.findByFunctionUnitId(functionUnit.getId());
        existing.stream()
                .filter(d -> decisionKey.equals(d.getDecisionKey()))
                .findFirst()
                .ifPresent(d -> decisionDefinitionRepository.deleteById(d.getId()));

        DecisionDefinition decision = DecisionDefinition.builder()
                .functionUnit(functionUnit)
                .decisionKey(decisionKey)
                .decisionName(decisionName)
                .dmnXml(dmnXml)
                .hitPolicy(hitPolicy)
                .build();
        decisionDefinitionRepository.save(decision);
    }

    @Override
    public Map<String, Object> validateImportPackage(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            Map<String, Object> packageData = parseImportPackage(file);
            
            // 与导入一致：manifest.json（新）或 metadata.json（旧）
            Map<String, Object> descriptor = resolvePackageDescriptor(packageData);
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
            Map<String, Object> packageData = parseImportPackage(file);
            Map<String, Object> descriptor = resolvePackageDescriptor(packageData);
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

    /**
     * 导入/预览与保存、部署一致：LAST_TASK_ASSIGNEE 锚点要求单入线。
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

    private void addZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    /**
     * 与 {@link #importFunctionUnit} 一致：优先 manifest.json，兼容 metadata.json。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolvePackageDescriptor(Map<String, Object> packageData) {
        if (packageData.containsKey("manifest")) {
            return (Map<String, Object>) packageData.get("manifest");
        }
        if (packageData.containsKey("metadata")) {
            return (Map<String, Object>) packageData.get("metadata");
        }
        return null;
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
            throw new DeveloperBusinessException("SYS_IMPORT_ERROR", "Failed to parse import package: " + e.getMessage());
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

            // 解析表关系
            if (rawFiles.containsKey("relations/table_relations.json")) {
                result.put("tableRelations", objectMapper.readValue(
                        rawFiles.get("relations/table_relations.json"), List.class));
            }
            
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
            
            // 解析决策定义（DMN XML 文件）
            List<String> decisions = new ArrayList<>();
            for (String fileName : rawFiles.keySet()) {
                if (fileName.startsWith("decisions/") && fileName.endsWith(".dmn")) {
                    decisions.add(new String(rawFiles.get(fileName), StandardCharsets.UTF_8));
                }
            }
            result.put("decisions", decisions);
            
            // 保存校验和用于验证
            if (rawFiles.containsKey("checksum.sha256")) {
                result.put("checksum", new String(rawFiles.get("checksum.sha256"), StandardCharsets.UTF_8));
            }
            
        } catch (IOException e) {
            throw new DeveloperBusinessException("SYS_IMPORT_ERROR", "Failed to parse import package content: " + e.getMessage());
        }
        
        return result;
    }
    
    private Map<String, Object> serializeTable(TableDefinition table, Map<Long, String> tableIdToName) {
        Map<String, Object> map = new HashMap<>();
        map.put("tableId", table.getId());
        map.put("tableName", table.getTableName());
        map.put("tableDisplayName", table.getTableDisplayName());
        map.put("tableType", table.getTableType().name());
        map.put("description", table.getDescription());
        map.put("fields", table.getFieldDefinitions().stream().map(this::serializeField).toList());
        if (table.getForeignKeys() != null && !table.getForeignKeys().isEmpty()) {
            map.put("foreignKeys", table.getForeignKeys().stream()
                    .map(fk -> serializeForeignKey(fk, tableIdToName))
                    .toList());
        }
        return map;
    }

    private Map<String, Object> serializeForeignKey(ForeignKey foreignKey, Map<Long, String> tableIdToName) {
        Map<String, Object> map = new HashMap<>();
        map.put("fieldName", foreignKey.getFieldDefinition().getFieldName());
        Long refTableId = foreignKey.getRefTableDefinition() != null
                ? foreignKey.getRefTableDefinition().getId() : null;
        map.put("refTableName", refTableId != null ? tableIdToName.get(refTableId) : null);
        map.put("refFieldName", foreignKey.getRefFieldDefinition().getFieldName());
        map.put("onDelete", foreignKey.getOnDelete());
        map.put("onUpdate", foreignKey.getOnUpdate());
        return map;
    }

    private Map<String, Object> serializeTableRelation(TableRelation relation, Map<Long, String> tableIdToName) {
        Map<String, Object> map = new HashMap<>();
        map.put("sourceTableName", tableIdToName.get(relation.getSourceTableId()));
        map.put("sourceFieldName", relation.getSourceFieldName());
        map.put("relationType", relation.getRelationType());
        map.put("targetTableName", tableIdToName.get(relation.getTargetTableId()));
        map.put("targetFieldName", relation.getTargetFieldName());
        return map;
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
     * 生成导入时的唯一编码
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
     * 与 FunctionUnit 创建逻辑保持一致的前缀清洗规则（Flowable/BPMN XML Name 约束 + 总长度约束）。
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
    
    private Map<String, Object> serializeField(FieldDefinition field) {
        Map<String, Object> map = new HashMap<>();
        map.put("fieldName", field.getFieldName());
        map.put("dataType", field.getDataType().name());
        map.put("length", field.getLength());
        map.put("precision", field.getPrecision());
        map.put("scale", field.getScale());
        map.put("nullable", field.getNullable());
        map.put("defaultValue", field.getDefaultValue());
        map.put("isPrimaryKey", field.getIsPrimaryKey());
        map.put("isUnique", field.getIsUnique());
        map.put("description", field.getDescription());
        map.put("sortOrder", field.getSortOrder());
        return map;
    }
    
    private Map<String, Object> serializeForm(FormDefinition form, List<FormStageBinding> stageBindings) {
        Map<String, Object> map = new HashMap<>();
        map.put("formId", form.getId());
        map.put("formName", form.getFormName());
        map.put("formType", form.getFormType().name());
        map.put("description", form.getDescription());
        map.put("boundTableName", form.getBoundTableName());
        map.put("showLiveValues", form.getShowLiveValues());
        map.put("fieldPermissions", form.getFieldPermissions());
        map.put("configJson", form.getConfigJson());
        if (form.getTableBindings() != null && !form.getTableBindings().isEmpty()) {
            map.put("tableBindings", form.getTableBindings().stream()
                    .map(this::serializeFormTableBinding)
                    .toList());
        }
        if (stageBindings != null && !stageBindings.isEmpty()) {
            map.put("stageBindings", stageBindings.stream().map(this::serializeFormStageBinding).toList());
        }
        return map;
    }

    private Map<String, Object> serializeFormTableBinding(FormTableBinding binding) {
        Map<String, Object> map = new HashMap<>();
        map.put("bindingId", binding.getId());
        map.put("bindingType", binding.getBindingType().name());
        map.put("bindingMode", binding.getBindingMode().name());
        map.put("tableName", binding.getTableName());
        map.put("relationTableId", binding.getRelationTableId());
        map.put("foreignKeyField", binding.getForeignKeyField());
        map.put("sortOrder", binding.getSortOrder());
        if (binding.getSubMode() != null) {
            map.put("subMode", binding.getSubMode().name());
        }
        return map;
    }

    private Map<String, Object> serializeFormStageBinding(FormStageBinding stageBinding) {
        Map<String, Object> map = new HashMap<>();
        map.put("stageId", stageBinding.getStageId());
        map.put("stageName", stageBinding.getStageName());
        map.put("readOnly", stageBinding.getReadOnly());
        return map;
    }
    
    private Map<String, Object> serializeAction(ActionDefinition action) {
        Map<String, Object> map = new HashMap<>();
        map.put("actionId", action.getId());
        map.put("actionName", action.getActionName());
        map.put("actionType", action.getActionType().name());
        map.put("configJson", action.getConfigJson());
        return map;
    }
}
