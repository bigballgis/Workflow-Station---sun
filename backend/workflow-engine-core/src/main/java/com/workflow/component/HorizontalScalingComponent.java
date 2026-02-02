package com.workflow.component;

import com.workflow.dto.response.ClusterNodeInfo;
import com.workflow.dto.response.LoadBalancingResult;
import com.workflow.dto.response.ScalingMetrics;
import com.workflow.exception.WorkflowBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 水平扩展支持组件
 * 
 * 负责多实例部署支持、负载均衡、节点发现和健康检查
 * 支持热部署和零停机升级
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HorizontalScalingComponent {

    private final StringRedisTemplate stringRedisTemplate;
    
    // 集群配置
    private static final String CLUSTER_PREFIX = "workflow:cluster:";
    private static final String NODE_REGISTRY = CLUSTER_PREFIX + "nodes";
    private static final String NODE_HEARTBEAT = CLUSTER_PREFIX + "heartbeat:";
    private static final String LEADER_KEY = CLUSTER_PREFIX + "leader";
    private static final String TASK_LOCK_PREFIX = CLUSTER_PREFIX + "lock:task:";
    
    // 节点配置
    private static final long HEARTBEAT_INTERVAL_MS = 10000; // 10秒
    private static final long NODE_TIMEOUT_MS = 30000; // 30秒
    private static final long LEADER_LEASE_SECONDS = 30; // 领导者租约30秒
    
    // 当前节点信息
    private String nodeId;
    private String nodeHost;
    private int nodePort;
    private LocalDateTime startTime;
    private volatile boolean isLeader = false;
    
    // 负载统计
    private final AtomicLong processedTasks = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> taskProcessingTimes = new ConcurrentHashMap<>();
    
    // 节点缓存
    private final ConcurrentHashMap<String, ClusterNodeInfo> nodeCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            // 生成节点ID
            this.nodeId = generateNodeId();
            this.nodeHost = InetAddress.getLocalHost().getHostAddress();
            this.nodePort = 8080; // 默认端口，可从配置读取
            this.startTime = LocalDateTime.now();
            
            // 注册节点
            registerNode();
            
            log.info("水平扩展组件初始化完成: nodeId={}, host={}", nodeId, nodeHost);
            
        } catch (UnknownHostException e) {
            log.error("获取主机地址失败", e);
            this.nodeHost = "unknown";
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("节点下线: nodeId={}", nodeId);
        unregisterNode();
    }

    // ==================== 节点注册和发现 ====================

    /**
     * 注册当前节点到集群
     */
    public void registerNode() {
        try {
            ClusterNodeInfo nodeInfo = buildCurrentNodeInfo();
            String nodeJson = serializeNodeInfo(nodeInfo);
            
            // 注册到节点列表
            stringRedisTemplate.opsForHash().put(NODE_REGISTRY, nodeId, nodeJson);
            
            // 设置心跳
            updateHeartbeat();
            
            log.info("节点注册成功: nodeId={}", nodeId);
            
        } catch (Exception e) {
            log.warn("节点注册失败，将在心跳时重试: {}", e.getMessage());
            // Don't throw exception - allow startup to continue
            // The scheduled heartbeat will retry registration
        }
    }

    /**
     * 注销当前节点
     */
    public void unregisterNode() {
        try {
            stringRedisTemplate.opsForHash().delete(NODE_REGISTRY, nodeId);
            stringRedisTemplate.delete(NODE_HEARTBEAT + nodeId);
            
            // 如果是领导者，释放领导权
            if (isLeader) {
                releaseLeadership();
            }
            
            log.info("节点注销成功: nodeId={}", nodeId);
            
        } catch (Exception e) {
            log.error("节点注销失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 更新心跳
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void updateHeartbeat() {
        try {
            String heartbeatKey = NODE_HEARTBEAT + nodeId;
            stringRedisTemplate.opsForValue().set(
                    heartbeatKey, 
                    String.valueOf(System.currentTimeMillis()),
                    Duration.ofMillis(NODE_TIMEOUT_MS * 2)
            );
            
            // 更新节点信息
            ClusterNodeInfo nodeInfo = buildCurrentNodeInfo();
            String nodeJson = serializeNodeInfo(nodeInfo);
            stringRedisTemplate.opsForHash().put(NODE_REGISTRY, nodeId, nodeJson);
            
            // 尝试获取领导权
            tryAcquireLeadership();
            
            // 清理过期节点
            cleanupExpiredNodes();
            
        } catch (Exception e) {
            log.error("心跳更新失败: {}", e.getMessage());
        }
    }

    /**
     * 获取所有活跃节点
     */
    public List<ClusterNodeInfo> getActiveNodes() {
        try {
            Map<Object, Object> nodeEntries = stringRedisTemplate.opsForHash().entries(NODE_REGISTRY);
            List<ClusterNodeInfo> activeNodes = new ArrayList<>();
            
            for (Map.Entry<Object, Object> entry : nodeEntries.entrySet()) {
                String entryNodeId = (String) entry.getKey();
                String nodeJson = (String) entry.getValue();
                
                if (isNodeAlive(entryNodeId)) {
                    ClusterNodeInfo nodeInfo = deserializeNodeInfo(nodeJson);
                    if (nodeInfo != null) {
                        activeNodes.add(nodeInfo);
                        nodeCache.put(entryNodeId, nodeInfo);
                    }
                }
            }
            
            return activeNodes;
            
        } catch (Exception e) {
            log.error("获取活跃节点失败: {}", e.getMessage(), e);
            return new ArrayList<>(nodeCache.values());
        }
    }

    /**
     * 检查节点是否存活
     */
    public boolean isNodeAlive(String targetNodeId) {
        try {
            String heartbeat = stringRedisTemplate.opsForValue().get(NODE_HEARTBEAT + targetNodeId);
            if (heartbeat == null) {
                return false;
            }
            
            long lastHeartbeat = Long.parseLong(heartbeat);
            return System.currentTimeMillis() - lastHeartbeat < NODE_TIMEOUT_MS;
            
        } catch (Exception e) {
            log.error("检查节点存活状态失败: nodeId={}, error={}", targetNodeId, e.getMessage());
            return false;
        }
    }

    // ==================== 领导者选举 ====================

    /**
     * 尝试获取领导权
     */
    public boolean tryAcquireLeadership() {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    LEADER_KEY, 
                    nodeId, 
                    Duration.ofSeconds(LEADER_LEASE_SECONDS)
            );
            
            if (Boolean.TRUE.equals(acquired)) {
                isLeader = true;
                log.info("获取领导权成功: nodeId={}", nodeId);
                return true;
            }
            
            // 检查是否已经是领导者
            String currentLeader = stringRedisTemplate.opsForValue().get(LEADER_KEY);
            if (nodeId.equals(currentLeader)) {
                // 续约
                stringRedisTemplate.expire(LEADER_KEY, Duration.ofSeconds(LEADER_LEASE_SECONDS));
                isLeader = true;
                return true;
            }
            
            isLeader = false;
            return false;
            
        } catch (Exception e) {
            log.error("获取领导权失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 释放领导权
     */
    public void releaseLeadership() {
        try {
            String currentLeader = stringRedisTemplate.opsForValue().get(LEADER_KEY);
            if (nodeId.equals(currentLeader)) {
                stringRedisTemplate.delete(LEADER_KEY);
                isLeader = false;
                log.info("释放领导权: nodeId={}", nodeId);
            }
        } catch (Exception e) {
            log.error("释放领导权失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前领导者
     */
    public String getCurrentLeader() {
        try {
            return stringRedisTemplate.opsForValue().get(LEADER_KEY);
        } catch (Exception e) {
            log.error("获取领导者失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查当前节点是否是领导者
     */
    public boolean isCurrentNodeLeader() {
        return isLeader;
    }

    // ==================== 负载均衡 ====================

    /**
     * 选择最佳节点处理任务
     */
    public LoadBalancingResult selectBestNode(String taskType) {
        log.debug("选择最佳节点: taskType={}", taskType);
        
        try {
            List<ClusterNodeInfo> activeNodes = getActiveNodes();
            
            if (activeNodes.isEmpty()) {
                return LoadBalancingResult.builder()
                        .success(false)
                        .message("没有可用的节点")
                        .build();
            }
            
            // 根据负载选择最佳节点（最小负载优先）
            ClusterNodeInfo bestNode = activeNodes.stream()
                    .min(Comparator.comparingDouble(ClusterNodeInfo::getLoadScore))
                    .orElse(activeNodes.get(0));
            
            return LoadBalancingResult.builder()
                    .success(true)
                    .selectedNodeId(bestNode.getNodeId())
                    .selectedNodeHost(bestNode.getHost())
                    .selectedNodePort(bestNode.getPort())
                    .loadScore(bestNode.getLoadScore())
                    .totalNodes(activeNodes.size())
                    .message("节点选择成功")
                    .build();
                    
        } catch (Exception e) {
            log.error("选择节点失败: {}", e.getMessage(), e);
            return LoadBalancingResult.builder()
                    .success(false)
                    .message("节点选择失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 获取负载均衡统计
     */
    public Map<String, Object> getLoadBalancingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<ClusterNodeInfo> activeNodes = getActiveNodes();
        
        stats.put("totalNodes", activeNodes.size());
        stats.put("currentNodeId", nodeId);
        stats.put("isLeader", isLeader);
        stats.put("currentLeader", getCurrentLeader());
        
        // 计算平均负载
        double avgLoad = activeNodes.stream()
                .mapToDouble(ClusterNodeInfo::getLoadScore)
                .average()
                .orElse(0.0);
        stats.put("averageLoad", avgLoad);
        
        // 节点负载分布
        Map<String, Double> nodeLoads = new HashMap<>();
        for (ClusterNodeInfo node : activeNodes) {
            nodeLoads.put(node.getNodeId(), node.getLoadScore());
        }
        stats.put("nodeLoads", nodeLoads);
        
        return stats;
    }

    // ==================== 分布式锁 ====================

    /**
     * 获取任务分布式锁
     */
    public boolean acquireTaskLock(String taskId, long timeoutMs) {
        try {
            String lockKey = TASK_LOCK_PREFIX + taskId;
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    lockKey, 
                    nodeId, 
                    Duration.ofMillis(timeoutMs)
            );
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("获取任务锁成功: taskId={}, nodeId={}", taskId, nodeId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("获取任务锁失败: taskId={}, error={}", taskId, e.getMessage());
            return false;
        }
    }

    /**
     * 释放任务分布式锁
     */
    public boolean releaseTaskLock(String taskId) {
        try {
            String lockKey = TASK_LOCK_PREFIX + taskId;
            String lockHolder = stringRedisTemplate.opsForValue().get(lockKey);
            
            if (nodeId.equals(lockHolder)) {
                stringRedisTemplate.delete(lockKey);
                log.debug("释放任务锁成功: taskId={}", taskId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("释放任务锁失败: taskId={}, error={}", taskId, e.getMessage());
            return false;
        }
    }

    /**
     * 检查任务是否被锁定
     */
    public boolean isTaskLocked(String taskId) {
        try {
            String lockKey = TASK_LOCK_PREFIX + taskId;
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
        } catch (Exception e) {
            log.error("检查任务锁状态失败: taskId={}, error={}", taskId, e.getMessage());
            return false;
        }
    }

    // ==================== 扩展指标 ====================

    /**
     * 获取扩展指标
     */
    public ScalingMetrics getScalingMetrics() {
        List<ClusterNodeInfo> activeNodes = getActiveNodes();
        
        // 计算集群总负载
        double totalLoad = activeNodes.stream()
                .mapToDouble(ClusterNodeInfo::getLoadScore)
                .sum();
        
        double avgLoad = activeNodes.isEmpty() ? 0 : totalLoad / activeNodes.size();
        
        // 计算负载方差（用于判断负载是否均衡）
        double loadVariance = 0;
        if (!activeNodes.isEmpty()) {
            for (ClusterNodeInfo node : activeNodes) {
                loadVariance += Math.pow(node.getLoadScore() - avgLoad, 2);
            }
            loadVariance /= activeNodes.size();
        }
        
        // 判断是否需要扩展
        boolean needsScaleOut = avgLoad > 0.8;
        boolean needsScaleIn = avgLoad < 0.2 && activeNodes.size() > 1;
        
        return ScalingMetrics.builder()
                .totalNodes(activeNodes.size())
                .activeNodes(activeNodes.size())
                .averageLoad(avgLoad)
                .loadVariance(loadVariance)
                .totalProcessedTasks(processedTasks.get())
                .currentNodeProcessedTasks(processedTasks.get())
                .needsScaleOut(needsScaleOut)
                .needsScaleIn(needsScaleIn)
                .recommendedNodeCount(calculateRecommendedNodeCount(avgLoad, activeNodes.size()))
                .metricsTime(LocalDateTime.now())
                .build();
    }

    /**
     * 记录任务处理
     */
    public void recordTaskProcessed(String taskId, long processingTimeMs) {
        processedTasks.incrementAndGet();
        taskProcessingTimes.put(taskId, processingTimeMs);
        
        // 保持最近1000个任务的处理时间
        if (taskProcessingTimes.size() > 1000) {
            String oldestKey = taskProcessingTimes.keySet().iterator().next();
            taskProcessingTimes.remove(oldestKey);
        }
    }

    /**
     * 增加活跃连接数
     */
    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    /**
     * 减少活跃连接数
     */
    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    // ==================== 热部署支持 ====================

    /**
     * 准备热部署（优雅下线）
     */
    public void prepareForHotDeploy() {
        log.info("准备热部署: nodeId={}", nodeId);
        
        // 停止接收新任务
        // 等待当前任务完成
        // 释放领导权
        if (isLeader) {
            releaseLeadership();
        }
        
        // 从节点列表中标记为下线中
        try {
            ClusterNodeInfo nodeInfo = buildCurrentNodeInfo();
            nodeInfo.setStatus("DRAINING");
            String nodeJson = serializeNodeInfo(nodeInfo);
            stringRedisTemplate.opsForHash().put(NODE_REGISTRY, nodeId, nodeJson);
        } catch (Exception e) {
            log.error("标记节点下线状态失败: {}", e.getMessage());
        }
    }

    /**
     * 完成热部署（重新上线）
     */
    public void completeHotDeploy() {
        log.info("完成热部署: nodeId={}", nodeId);
        
        // 重新注册节点
        registerNode();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成节点ID
     */
    private String generateNodeId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (UnknownHostException e) {
            return "node-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 构建当前节点信息
     */
    private ClusterNodeInfo buildCurrentNodeInfo() {
        double loadScore = calculateCurrentLoadScore();
        
        return ClusterNodeInfo.builder()
                .nodeId(nodeId)
                .host(nodeHost)
                .port(nodePort)
                .status(isLeader ? "LEADER" : "FOLLOWER")
                .loadScore(loadScore)
                .processedTasks(processedTasks.get())
                .activeConnections(activeConnections.get())
                .startTime(startTime)
                .lastHeartbeat(LocalDateTime.now())
                .build();
    }

    /**
     * 计算当前负载分数
     */
    private double calculateCurrentLoadScore() {
        // 基于活跃连接数和处理任务数计算负载分数
        long connections = activeConnections.get();
        
        // 简化的负载计算：连接数 / 100（假设最大100个连接）
        double connectionLoad = Math.min(1.0, connections / 100.0);
        
        // 可以添加更多因素：CPU使用率、内存使用率等
        return connectionLoad;
    }

    /**
     * 计算推荐节点数
     */
    private int calculateRecommendedNodeCount(double avgLoad, int currentNodes) {
        if (avgLoad > 0.8) {
            // 负载过高，建议增加节点
            return (int) Math.ceil(currentNodes * avgLoad / 0.6);
        } else if (avgLoad < 0.2 && currentNodes > 1) {
            // 负载过低，建议减少节点
            return Math.max(1, (int) Math.ceil(currentNodes * avgLoad / 0.4));
        }
        return currentNodes;
    }

    /**
     * 清理过期节点
     */
    private void cleanupExpiredNodes() {
        try {
            Map<Object, Object> nodeEntries = stringRedisTemplate.opsForHash().entries(NODE_REGISTRY);
            
            for (Object entryNodeId : nodeEntries.keySet()) {
                String targetNodeId = (String) entryNodeId;
                if (!isNodeAlive(targetNodeId)) {
                    stringRedisTemplate.opsForHash().delete(NODE_REGISTRY, targetNodeId);
                    nodeCache.remove(targetNodeId);
                    log.info("清理过期节点: nodeId={}", targetNodeId);
                }
            }
        } catch (Exception e) {
            log.error("清理过期节点失败: {}", e.getMessage());
        }
    }

    /**
     * 序列化节点信息
     */
    private String serializeNodeInfo(ClusterNodeInfo nodeInfo) {
        // 简化的序列化，实际应使用JSON
        return String.format("%s|%s|%d|%s|%.2f|%d|%d|%s|%s",
                nodeInfo.getNodeId(),
                nodeInfo.getHost(),
                nodeInfo.getPort(),
                nodeInfo.getStatus(),
                nodeInfo.getLoadScore(),
                nodeInfo.getProcessedTasks(),
                nodeInfo.getActiveConnections(),
                nodeInfo.getStartTime(),
                nodeInfo.getLastHeartbeat()
        );
    }

    /**
     * 反序列化节点信息
     */
    private ClusterNodeInfo deserializeNodeInfo(String nodeJson) {
        try {
            String[] parts = nodeJson.split("\\|");
            if (parts.length < 9) {
                return null;
            }
            
            return ClusterNodeInfo.builder()
                    .nodeId(parts[0])
                    .host(parts[1])
                    .port(Integer.parseInt(parts[2]))
                    .status(parts[3])
                    .loadScore(Double.parseDouble(parts[4]))
                    .processedTasks(Long.parseLong(parts[5]))
                    .activeConnections(Long.parseLong(parts[6]))
                    .startTime(LocalDateTime.parse(parts[7]))
                    .lastHeartbeat(LocalDateTime.parse(parts[8]))
                    .build();
                    
        } catch (Exception e) {
            log.error("反序列化节点信息失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== Getter方法 ====================

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeHost() {
        return nodeHost;
    }

    public int getNodePort() {
        return nodePort;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }
}
