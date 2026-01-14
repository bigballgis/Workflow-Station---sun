package com.portal.util;

/**
 * 流程定义Key提取工具类
 * 用于从 Flowable 的完整流程定义ID中提取流程Key
 * 
 * Flowable 流程定义ID格式: {processKey}:{version}:{uuid}
 * 例如: Process_PurchaseRequest:2:abc123-def456-ghi789
 */
public final class ProcessKeyExtractor {
    
    private ProcessKeyExtractor() {
        // 工具类，禁止实例化
    }
    
    /**
     * 从 Flowable 流程定义ID中提取流程Key
     * 
     * @param processDefinitionId Flowable 的完整流程定义ID
     *                            格式: {processKey}:{version}:{uuid}
     * @return 提取的流程Key，如果输入为null或空则返回null，
     *         如果没有冒号则返回原始字符串
     * 
     * 示例:
     * - "Process_PurchaseRequest:2:abc123" -> "Process_PurchaseRequest"
     * - "LeaveRequest:1:xyz789" -> "LeaveRequest"
     * - "SimpleProcess" -> "SimpleProcess" (无冒号，返回原值)
     * - null -> null
     * - "" -> null
     */
    public static String extractProcessKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        
        int colonIndex = processDefinitionId.indexOf(':');
        if (colonIndex > 0) {
            return processDefinitionId.substring(0, colonIndex);
        }
        
        // 没有冒号，返回原始字符串
        return processDefinitionId;
    }
    
    /**
     * 检查字符串是否是完整的 Flowable 流程定义ID格式
     * 
     * @param processDefinitionId 要检查的字符串
     * @return true 如果是完整格式 ({processKey}:{version}:{uuid})
     */
    public static boolean isFullProcessDefinitionId(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return false;
        }
        
        // 完整格式应该有两个冒号
        int firstColon = processDefinitionId.indexOf(':');
        if (firstColon <= 0) {
            return false;
        }
        
        int secondColon = processDefinitionId.indexOf(':', firstColon + 1);
        return secondColon > firstColon + 1;
    }
    
    /**
     * 从完整的流程定义ID中提取版本号
     * 
     * @param processDefinitionId Flowable 的完整流程定义ID
     * @return 版本号字符串，如果无法提取则返回null
     */
    public static String extractVersion(String processDefinitionId) {
        if (!isFullProcessDefinitionId(processDefinitionId)) {
            return null;
        }
        
        int firstColon = processDefinitionId.indexOf(':');
        int secondColon = processDefinitionId.indexOf(':', firstColon + 1);
        
        return processDefinitionId.substring(firstColon + 1, secondColon);
    }
}
