package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程实例查询结果
 */
@Data
@Builder
public class ProcessInstanceQueryResult {
    
    /**
     * 流程实例列表
     */
    private List<ProcessInstanceInfo> processInstances;
    
    /**
     * 总记录数
     */
    private long totalCount;
    
    /**
     * 当前页码
     */
    private int currentPage;
    
    /**
     * 每页大小
     */
    private int pageSize;
    
    /**
     * 总页数
     */
    private int totalPages;
    
    /**
     * 流程实例信息
     */
    @Data
    @Builder
    public static class ProcessInstanceInfo {
        
        /**
         * 流程实例ID
         */
        private String processInstanceId;
        
        /**
         * 流程定义ID
         */
        private String processDefinitionId;
        
        /**
         * 流程定义键
         */
        private String processDefinitionKey;
        
        /**
         * 流程定义名称
         */
        private String processDefinitionName;
        
        /**
         * 业务键
         */
        private String businessKey;
        
        /**
         * 流程实例名称
         */
        private String name;
        
        /**
         * 启动时间
         */
        private LocalDateTime startTime;
        
        /**
         * 结束时间
         */
        private LocalDateTime endTime;
        
        /**
         * 启动用户ID
         */
        private String startUserId;
        
        /**
         * 流程实例状态
         */
        private String state;
        
        /**
         * 是否暂停
         */
        private boolean suspended;
        
        /**
         * 是否已结束
         */
        private boolean ended;
        
        /**
         * 流程变量
         */
        private Map<String, Object> variables;
        
        /**
         * 当前活动任务数
         */
        private long activeTaskCount;
    }
}