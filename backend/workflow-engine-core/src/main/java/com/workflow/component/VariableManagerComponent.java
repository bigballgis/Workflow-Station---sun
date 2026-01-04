package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.VariableSetRequest;
import com.workflow.dto.request.DataTableQueryRequest;
import com.workflow.dto.request.DataTableInsertRequest;
import com.workflow.dto.request.DataTableUpdateRequest;
import com.workflow.dto.request.DataTableDeleteRequest;
import com.workflow.dto.response.VariableGetResult;
import com.workflow.dto.response.VariableHistoryResult;
import com.workflow.dto.response.DataTableQueryResult;
import com.workflow.dto.response.DataTableOperationResult;
import com.workflow.entity.ProcessVariable;
import com.workflow.enums.VariableType;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ProcessVariableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 流程变量管理组件
 * 
 * 负责流程变量的存储、检索、类型转换和历史记录管理
 * 支持多种数据类型：字符串、数字、布尔值、日期、JSON对象、文件等
 * 集成PostgreSQL JSONB支持复杂对象存储
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VariableManagerComponent {

    private final RuntimeService runtimeService;
    private final ProcessVariableRepository processVariableRepository;
    private final ObjectMapper objectMapper;
    private final DataTableManagerComponent dataTableManagerComponent;

    /**
     * 设置流程变量
     * 支持流程实例级别、执行级别和任务级别的变量作用域
     * 
     * @param request 变量设置请求
     * @return 变量设置结果
     */
    @Transactional
    public String setVariable(VariableSetRequest request) {
        log.info("设置流程变量: processInstanceId={}, name={}, type={}", 
                request.getProcessInstanceId(), request.getName(), request.getType());
        
        // 验证请求参数
        validateVariableSetRequest(request);
        
        try {
            // 根据作用域设置变量
            switch (request.getScope()) {
                case PROCESS_INSTANCE:
                    setProcessInstanceVariable(request);
                    break;
                case EXECUTION:
                    setExecutionVariable(request);
                    break;
                case TASK:
                    setTaskVariable(request);
                    break;
                default:
                    throw new WorkflowValidationException(List.of(
                        new WorkflowValidationException.ValidationError("scope", "不支持的变量作用域: " + request.getScope(), request.getScope())
                    ));
            }
            
            // 保存变量历史记录
            ProcessVariable variable = createVariableEntity(request);
            ProcessVariable savedVariable = processVariableRepository.save(variable);
            
            log.info("流程变量设置成功: variableId={}", savedVariable.getId());
            return savedVariable.getId();
            
        } catch (Exception e) {
            log.error("设置流程变量失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("VARIABLE_SET_FAILED", "设置流程变量失败: " + e.getMessage());
        }
    }

    /**
     * 获取流程变量
     * 支持变量作用域查找，从任务级别向上查找到流程实例级别
     * 
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名称
     * @param scope 变量作用域
     * @return 变量获取结果
     */
    public VariableGetResult getVariable(String processInstanceId, String variableName, String scope) {
        log.info("获取流程变量: processInstanceId={}, name={}, scope={}", 
                processInstanceId, variableName, scope);
        
        try {
            Object value = null;
            VariableType type = null;
            
            // 根据作用域获取变量
            if ("TASK".equals(scope) && processInstanceId != null) {
                // 从任务作用域获取
                value = runtimeService.getVariable(processInstanceId, variableName);
            } else if ("EXECUTION".equals(scope) && processInstanceId != null) {
                // 从执行作用域获取
                value = runtimeService.getVariable(processInstanceId, variableName);
            } else {
                // 从流程实例作用域获取
                value = runtimeService.getVariable(processInstanceId, variableName);
            }
            
            if (value != null) {
                type = determineVariableType(value);
            }
            
            return VariableGetResult.builder()
                    .name(variableName)
                    .value(value)
                    .type(type)
                    .processInstanceId(processInstanceId)
                    .scope(scope)
                    .found(value != null)
                    .build();
                    
        } catch (Exception e) {
            log.error("获取流程变量失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("VARIABLE_GET_FAILED", "获取流程变量失败: " + e.getMessage());
        }
    }

    /**
     * 获取流程实例的所有变量
     * 
     * @param processInstanceId 流程实例ID
     * @return 变量映射
     */
    public Map<String, Object> getAllVariables(String processInstanceId) {
        log.info("获取流程实例所有变量: processInstanceId={}", processInstanceId);
        
        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            log.info("获取到 {} 个流程变量", variables.size());
            return variables;
            
        } catch (Exception e) {
            log.error("获取流程实例所有变量失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("VARIABLES_GET_FAILED", "获取流程实例所有变量失败: " + e.getMessage());
        }
    }

    /**
     * 删除流程变量
     * 
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名称
     */
    @Transactional
    public void removeVariable(String processInstanceId, String variableName) {
        log.info("删除流程变量: processInstanceId={}, name={}", processInstanceId, variableName);
        
        try {
            runtimeService.removeVariable(processInstanceId, variableName);
            
            // 记录删除操作到历史
            ProcessVariable deleteRecord = ProcessVariable.builder()
                    .name(variableName)
                    .processInstanceId(processInstanceId)
                    .type(VariableType.DELETED)
                    .textValue("DELETED")
                    .createdTime(LocalDateTime.now())
                    .build();
            processVariableRepository.save(deleteRecord);
            
            log.info("流程变量删除成功");
            
        } catch (Exception e) {
            log.error("删除流程变量失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("VARIABLE_REMOVE_FAILED", "删除流程变量失败: " + e.getMessage());
        }
    }

    /**
     * 获取变量修改历史
     * 
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名称
     * @return 变量历史记录
     */
    public VariableHistoryResult getVariableHistory(String processInstanceId, String variableName) {
        log.info("获取变量历史: processInstanceId={}, name={}", processInstanceId, variableName);
        
        List<ProcessVariable> history = processVariableRepository
                .findByProcessInstanceIdAndNameOrderByCreatedTimeDesc(processInstanceId, variableName);
        
        return VariableHistoryResult.builder()
                .processInstanceId(processInstanceId)
                .variableName(variableName)
                .history(history)
                .totalCount(history.size())
                .build();
    }

    /**
     * 类型转换和格式化
     * 
     * @param value 原始值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    public Object convertVariableType(Object value, VariableType targetType) {
        if (value == null) {
            return null;
        }
        
        try {
            switch (targetType) {
                case STRING:
                    return value.toString();
                case INTEGER:
                    if (value instanceof Number) {
                        return ((Number) value).intValue();
                    }
                    return Integer.valueOf(value.toString());
                case LONG:
                    if (value instanceof Number) {
                        return ((Number) value).longValue();
                    }
                    return Long.valueOf(value.toString());
                case DOUBLE:
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                    return Double.valueOf(value.toString());
                case BOOLEAN:
                    if (value instanceof Boolean) {
                        return value;
                    }
                    return Boolean.valueOf(value.toString());
                case DATE:
                    if (value instanceof Date) {
                        return value;
                    }
                    // 可以添加更多日期格式解析逻辑
                    throw new WorkflowValidationException(List.of(
                        new WorkflowValidationException.ValidationError("value", "不支持的日期格式转换", value)
                    ));
                case JSON:
                    if (value instanceof String) {
                        return objectMapper.readTree((String) value);
                    }
                    return objectMapper.valueToTree(value);
                default:
                    return value;
            }
        } catch (Exception e) {
            log.error("变量类型转换失败: {} -> {}", value.getClass().getSimpleName(), targetType, e);
            throw new WorkflowValidationException(List.of(
                new WorkflowValidationException.ValidationError("value", "变量类型转换失败: " + e.getMessage(), value)
            ));
        }
    }

    /**
     * 设置流程实例级别变量
     */
    private void setProcessInstanceVariable(VariableSetRequest request) {
        Object processedValue = processVariableValue(request.getValue(), request.getType());
        runtimeService.setVariable(request.getProcessInstanceId(), request.getName(), processedValue);
    }

    /**
     * 设置执行级别变量
     */
    private void setExecutionVariable(VariableSetRequest request) {
        if (request.getExecutionId() == null) {
            throw new WorkflowValidationException(List.of(
                new WorkflowValidationException.ValidationError("executionId", "执行级别变量需要提供executionId", null)
            ));
        }
        Object processedValue = processVariableValue(request.getValue(), request.getType());
        runtimeService.setVariable(request.getExecutionId(), request.getName(), processedValue);
    }

    /**
     * 设置任务级别变量
     */
    private void setTaskVariable(VariableSetRequest request) {
        if (request.getTaskId() == null) {
            throw new WorkflowValidationException(List.of(
                new WorkflowValidationException.ValidationError("taskId", "任务级别变量需要提供taskId", null)
            ));
        }
        Object processedValue = processVariableValue(request.getValue(), request.getType());
        runtimeService.setVariableLocal(request.getTaskId(), request.getName(), processedValue);
    }

    /**
     * 处理变量值，根据类型进行序列化
     */
    private Object processVariableValue(Object value, VariableType type) {
        if (value == null) {
            return null;
        }
        
        try {
            switch (type) {
                case JSON:
                    // JSON类型序列化为字符串存储
                    if (value instanceof String) {
                        // 验证JSON格式
                        objectMapper.readTree((String) value);
                        return value;
                    } else {
                        return objectMapper.writeValueAsString(value);
                    }
                case STRING:
                case INTEGER:
                case LONG:
                case DOUBLE:
                case BOOLEAN:
                case DATE:
                    return value;
                default:
                    return value.toString();
            }
        } catch (JsonProcessingException e) {
            throw new WorkflowValidationException(List.of(
                new WorkflowValidationException.ValidationError("value", "JSON序列化失败: " + e.getMessage(), value)
            ));
        }
    }

    /**
     * 创建变量实体用于历史记录
     */
    private ProcessVariable createVariableEntity(VariableSetRequest request) {
        ProcessVariable.ProcessVariableBuilder builder = ProcessVariable.builder()
                .name(request.getName())
                .type(request.getType())
                .processInstanceId(request.getProcessInstanceId())
                .executionId(request.getExecutionId())
                .taskId(request.getTaskId())
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now());

        // 根据类型设置相应的值字段
        Object value = request.getValue();
        if (value != null) {
            switch (request.getType()) {
                case STRING:
                    builder.textValue(value.toString());
                    break;
                case INTEGER:
                case LONG:
                    builder.longValue(((Number) value).longValue());
                    break;
                case DOUBLE:
                    builder.doubleValue(((Number) value).doubleValue());
                    break;
                case BOOLEAN:
                    builder.textValue(value.toString());
                    break;
                case DATE:
                    if (value instanceof Date) {
                        builder.dateValue((Date) value);
                    }
                    break;
                case JSON:
                    try {
                        String jsonString = value instanceof String ? 
                                (String) value : objectMapper.writeValueAsString(value);
                        builder.jsonValue(jsonString);
                    } catch (JsonProcessingException e) {
                        throw new WorkflowValidationException(List.of(
                            new WorkflowValidationException.ValidationError("value", "JSON序列化失败: " + e.getMessage(), value)
                        ));
                    }
                    break;
            }
        }

        return builder.build();
    }

    /**
     * 确定变量类型
     */
    private VariableType determineVariableType(Object value) {
        if (value == null) {
            return VariableType.STRING;
        }
        
        if (value instanceof String) {
            return VariableType.STRING;
        } else if (value instanceof Integer) {
            return VariableType.INTEGER;
        } else if (value instanceof Long) {
            return VariableType.LONG;
        } else if (value instanceof Double || value instanceof Float) {
            return VariableType.DOUBLE;
        } else if (value instanceof Boolean) {
            return VariableType.BOOLEAN;
        } else if (value instanceof Date) {
            return VariableType.DATE;
        } else {
            // 复杂对象默认为JSON类型
            return VariableType.JSON;
        }
    }

    /**
     * 验证变量设置请求
     */
    private void validateVariableSetRequest(VariableSetRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (request == null) {
            errors.add(new WorkflowValidationException.ValidationError("request", "变量设置请求不能为空", null));
        } else {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                errors.add(new WorkflowValidationException.ValidationError("name", "变量名称不能为空", request.getName()));
            }
            
            if (request.getType() == null) {
                errors.add(new WorkflowValidationException.ValidationError("type", "变量类型不能为空", null));
            }
            
            if (request.getScope() == null) {
                errors.add(new WorkflowValidationException.ValidationError("scope", "变量作用域不能为空", null));
            } else {
                // 根据作用域验证必需的ID
                switch (request.getScope()) {
                    case PROCESS_INSTANCE:
                        if (request.getProcessInstanceId() == null) {
                            errors.add(new WorkflowValidationException.ValidationError("processInstanceId", "流程实例级别变量需要提供processInstanceId", null));
                        }
                        break;
                    case EXECUTION:
                        if (request.getExecutionId() == null) {
                            errors.add(new WorkflowValidationException.ValidationError("executionId", "执行级别变量需要提供executionId", null));
                        }
                        break;
                    case TASK:
                        if (request.getTaskId() == null) {
                            errors.add(new WorkflowValidationException.ValidationError("taskId", "任务级别变量需要提供taskId", null));
                        }
                        break;
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    // ==================== 数据表CRUD操作方法 ====================

    /**
     * 查询数据表记录
     * 支持复杂查询条件、分页、排序和多表连接
     * 
     * @param request 查询请求
     * @return 查询结果
     */
    @Transactional(readOnly = true)
    public DataTableQueryResult queryDataTable(DataTableQueryRequest request) {
        log.info("通过变量管理器查询数据表: tableName={}", request.getTableName());
        return dataTableManagerComponent.queryTable(request);
    }

    /**
     * 插入数据表记录
     * 支持自动生成主键返回
     * 
     * @param request 插入请求
     * @return 操作结果
     */
    @Transactional
    public DataTableOperationResult insertDataTableRecord(DataTableInsertRequest request) {
        log.info("通过变量管理器插入数据表记录: tableName={}", request.getTableName());
        return dataTableManagerComponent.insertRecord(request);
    }

    /**
     * 更新数据表记录
     * 支持条件更新和批量更新
     * 
     * @param request 更新请求
     * @return 操作结果
     */
    @Transactional
    public DataTableOperationResult updateDataTableRecord(DataTableUpdateRequest request) {
        log.info("通过变量管理器更新数据表记录: tableName={}", request.getTableName());
        return dataTableManagerComponent.updateRecord(request);
    }

    /**
     * 删除数据表记录
     * 支持条件删除和批量删除
     * 
     * @param request 删除请求
     * @return 操作结果
     */
    @Transactional
    public DataTableOperationResult deleteDataTableRecord(DataTableDeleteRequest request) {
        log.info("通过变量管理器删除数据表记录: tableName={}", request.getTableName());
        return dataTableManagerComponent.deleteRecord(request);
    }

    /**
     * 执行数据表操作并将结果存储为流程变量
     * 这是一个便捷方法，将数据表查询结果直接存储为流程变量
     * 
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名称
     * @param queryRequest 查询请求
     * @return 变量设置结果
     */
    @Transactional
    public String queryAndStoreAsVariable(String processInstanceId, String variableName, DataTableQueryRequest queryRequest) {
        log.info("查询数据表并存储为变量: processInstanceId={}, variableName={}, tableName={}", 
                processInstanceId, variableName, queryRequest.getTableName());
        
        // 执行查询
        DataTableQueryResult queryResult = dataTableManagerComponent.queryTable(queryRequest);
        
        if (!queryResult.isSuccess()) {
            throw new WorkflowBusinessException("QUERY_FAILED", "数据表查询失败: " + queryResult.getErrorMessage());
        }
        
        // 将查询结果存储为流程变量
        VariableSetRequest variableRequest = VariableSetRequest.builder()
                .name(variableName)
                .value(queryResult.getData())
                .type(VariableType.JSON)
                .scope(com.workflow.enums.VariableScope.PROCESS_INSTANCE)
                .processInstanceId(processInstanceId)
                .build();
        
        return setVariable(variableRequest);
    }

    /**
     * 从流程变量获取数据并插入到数据表
     * 这是一个便捷方法，从流程变量获取数据并插入到指定的数据表
     * 
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名称
     * @param tableName 目标表名
     * @param returnGeneratedKeys 是否返回生成的主键
     * @return 操作结果
     */
    @Transactional
    public DataTableOperationResult insertFromVariable(String processInstanceId, String variableName, 
                                                     String tableName, boolean returnGeneratedKeys) {
        log.info("从流程变量插入数据表: processInstanceId={}, variableName={}, tableName={}", 
                processInstanceId, variableName, tableName);
        
        // 获取流程变量
        VariableGetResult variableResult = getVariable(processInstanceId, variableName, "PROCESS_INSTANCE");
        
        if (!variableResult.getFound()) {
            throw new WorkflowBusinessException("VARIABLE_NOT_FOUND", "流程变量不存在: " + variableName);
        }
        
        // 将变量值转换为Map
        Map<String, Object> data;
        try {
            if (variableResult.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) variableResult.getValue();
                data = mapValue;
            } else if (variableResult.getValue() instanceof String) {
                // 尝试解析JSON字符串
                data = objectMapper.readValue((String) variableResult.getValue(), Map.class);
            } else {
                throw new WorkflowBusinessException("TYPE_CONVERSION_ERROR", "变量值类型不支持插入操作: " + variableResult.getType());
            }
        } catch (Exception e) {
            throw new WorkflowBusinessException("VALUE_CONVERSION_ERROR", "变量值转换失败: " + e.getMessage(), e);
        }
        
        // 构建插入请求
        DataTableInsertRequest insertRequest = DataTableInsertRequest.builder()
                .tableName(tableName)
                .data(data)
                .returnGeneratedKeys(returnGeneratedKeys)
                .build();
        
        return dataTableManagerComponent.insertRecord(insertRequest);
    }
}