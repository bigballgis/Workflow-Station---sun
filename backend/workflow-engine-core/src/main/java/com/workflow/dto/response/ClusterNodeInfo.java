package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 集群节点信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterNodeInfo {
    
    /**
     * 节点ID
     */
    private String nodeId;
    
    /**
     * 主机地址
     */
    private String host;
    
    /**
     * 端口
     */
    private int port;
    
    /**
     * 节点状态：LEADER, FOLLOWER, DRAINING
     */
    private String status;
    
    /**
     * 负载分数（0-1）
     */
    private double loadScore;
    
    /**
     * 已处理任务数
     */
    private long processedTasks;
    
    /**
     * 活跃连接数
     */
    private long activeConnections;
    
    /**
     * 启动时间
     */
    private LocalDateTime startTime;
    
    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeat;
}
