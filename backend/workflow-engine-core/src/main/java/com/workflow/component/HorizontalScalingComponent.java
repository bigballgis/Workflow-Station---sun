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
 * Horizontal Scaling Component
 * 
 * Handles multi-instance deployment support, load balancing, node discovery and health checks
 * Supports hot deployment and zero-downtime upgrades
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HorizontalScalingComponent {

    private final StringRedisTemplate stringRedisTemplate;
    
    // Cluster configuration
    private static final String CLUSTER_PREFIX = "workflow:cluster:";
    private static final String NODE_REGISTRY = CLUSTER_PREFIX + "nodes";
    private static final String NODE_HEARTBEAT = CLUSTER_PREFIX + "heartbeat:";
    private static final String LEADER_KEY = CLUSTER_PREFIX + "leader";
    private static final String TASK_LOCK_PREFIX = CLUSTER_PREFIX + "lock:task:";
    
    // Node configuration
    private static final long HEARTBEAT_INTERVAL_MS = 10000; // 10 seconds
    private static final long NODE_TIMEOUT_MS = 30000; // 30 seconds
    private static final long LEADER_LEASE_SECONDS = 30; // Leader lease 30 seconds
    
    // Current node info
    private String nodeId;
    private String nodeHost;
    private int nodePort;
    private LocalDateTime startTime;
    private volatile boolean isLeader = false;
    
    // Load statistics
    private final AtomicLong processedTasks = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> taskProcessingTimes = new ConcurrentHashMap<>();
    
    // Node cache
    private final ConcurrentHashMap<String, ClusterNodeInfo> nodeCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            // Generate node ID
            this.nodeId = generateNodeId();
            this.nodeHost = InetAddress.getLocalHost().getHostAddress();
            this.nodePort = 8080; // Default port, configurable
            this.startTime = LocalDateTime.now();
            
            // Register node
            registerNode();
            
            log.info("Horizontal scaling component initialized: nodeId={}, host={}", nodeId, nodeHost);
            
        } catch (UnknownHostException e) {
            log.error("Failed to get host address", e);
            this.nodeHost = "unknown";
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Node going offline: nodeId={}", nodeId);
        unregisterNode();
    }

    // ==================== Node registration and discovery ====================

    /**
     * Register current node to cluster
     */
    public void registerNode() {
        try {
            ClusterNodeInfo nodeInfo = buildCurrentNodeInfo();
            String nodeJson = serializeNodeInfo(nodeInfo);
            
            // Register to node list
            stringRedisTemplate.opsForHash().put(NODE_REGISTRY, nodeId, nodeJson);
            
            // Set heartbeat
            updateHeartbeat();
            
            log.info("Node registered successfully: nodeId={}", nodeId);
            
        } catch (Exception e) {
            log.warn("Node registration failed, will retry on heartbeat: {}", e.getMessage());
            // Don't throw exception - allow startup to continue
            // The scheduled heartbeat will retry registration
        }
    }

    /**
     * Unregister current node
     */
    public void unregisterNode() {
        try {
            stringRedisTemplate.opsForHash().delete(NODE_REGISTRY, nodeId);
            stringRedisTemplate.delete(NODE_HEARTBEAT + nodeId);
            
            // If leader, release leadership
            if (isLeader) {
                releaseLeadership();
            }
            
            log.info("Node unregistered successfully: nodeId={}", nodeId);
            
        } catch (Exception e) {
            log.error("Failed to unregister node: {}", e.getMessage(), e);
        }
    }

    /**
     * Update heartbeat
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
            
            // Update node info
            ClusterNodeInfo nodeInfo = buildCurrentNodeInfo();
            String nodeJson = serializeNodeInfo(nodeInfo);
            stringRedisTemplate.opsForHash().put(NODE_REGISTRY, nodeId, nodeJson);
            
            // Try to acquire leadership
            tryAcquireLeadership();
            
            // Clean up expired nodes
            cleanupExpiredNodes();
            
        } catch (Exception e) {
            log.error("Heartbeat update failed: {}", e.getMessage());
        }
    }

    /**
     * Get all active nodes
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
            log.error("Failed to get active nodes: {}", e.getMessage(), e);
            return new ArrayList<>(nodeCache.values());
        }
    }

    /**
     * Check if node is alive
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
            log.error("Failed to check node alive status: nodeId={}, error={}", targetNodeId, e.getMessage());
            return false;
        }
    }

    // ==================== Leader election ====================

    /**
     * Try to acquire leadership
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
                log.info("Leadership acquired successfully: nodeId={}", nodeId);
                return true;
            }
            
            // Check if already leader
            String currentLeader = stringRedisTemplate.opsForValue().get(LEADER_KEY);
            if (nodeId.equals(currentLeader)) {
                // Renew lease
                stringRedisTemplate.expire(LEADER_KEY, Duration.ofSeconds(LEADER_LEASE_SECONDS));
                isLeader = true;
                return true;
            }
            
            isLeader = false;
            return false;
            
        } catch (Exception e) {
            log.error("Failed to acquire leadership: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Release leadership
     */
    public void releaseLeadership() {
        try {
            String currentLeader = stringRedisTemplate.opsForValue().get(LEADER_KEY);
            if (nodeId.equals(currentLeader)) {
                stringRedisTemplate.delete(LEADER_KEY);
                isLeader = false;
                log.info("Release leadership: nodeId={}", nodeId);
            }
        } catch (Exception e) {
            log.error("Failed to release leadership: {}", e.getMessage());
        }
    }

    /**
     * Get current leader
     */
    public String getCurrentLeader() {
        try {
            return stringRedisTemplate.opsForValue().get(LEADER_KEY);
        } catch (Exception e) {
            log.error("Failed to get leader: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if current node is leader
     */
    public boolean isCurrentNodeLeader() {
        return isLeader;
    }

    // ==================== Load balancing ====================

    /**
     * Select best node for task processing
     */
    public LoadBalancingResult selectBestNode(String taskType) {
        log.debug("Selecting best node: taskType={}", taskType);
        
        try {
            List<ClusterNodeInfo> activeNodes = getActiveNodes();
            
            if (activeNodes.isEmpty()) {
                return LoadBalancingResult.builder()
                        .success(false)
                        .message("No available nodes")
                        .build();
            }
            
            // Select best node by load (lowest load first)
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
                    .message("Node selected successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to select node: {}", e.getMessage(), e);
            return LoadBalancingResult.builder()
                    .success(false)
                    .message("Failed to select node: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get load balancing statistics
     */
    public Map<String, Object> getLoadBalancingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<ClusterNodeInfo> activeNodes = getActiveNodes();
        
        stats.put("totalNodes", activeNodes.size());
        stats.put("currentNodeId", nodeId);
        stats.put("isLeader", isLeader);
        stats.put("currentLeader", getCurrentLeader());
        
        // Calculate average load
        double avgLoad = activeNodes.stream()
                .mapToDouble(ClusterNodeInfo::getLoadScore)
                .average()
                .orElse(0.0);
        stats.put("averageLoad", avgLoad);
        
        // Node load distribution
        Map<String, Double> nodeLoads = new HashMap<>();
        for (ClusterNodeInfo node : activeNodes) {
            nodeLoads.put(node.getNodeId(), node.getLoadScore());
        }
        stats.put("nodeLoads", nodeLoads);
        
        return stats;
    }

    // ==================== Distributed locks ====================

    /**
     * Acquire task distributed lock
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
                log.debug("Task lock acquired: taskId={}, nodeId={}", taskId, nodeId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Failed to acquire task lock: taskId={}, error={}", taskId, e.getMessage());
            return false;
        }
    }

    /**
     * Release task distributed lock
     */
    public boolean releaseTaskLock(String taskId) {
        try {
            String lockKey = TASK_LOCK_PREFIX + taskId;
            String lockHolder = stringRedisTemplate.opsForValue().get(lockKey);
            
            if (nodeId.equals(lockHolder)) {
                stringRedisTemplate.delete(lockKey);
                log.debug("Task lock released: taskId={}", taskId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Failed to release task lock: taskId={}, error={}", taskId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if task is locked
     */
    public boolean isTaskLocked(String taskId) {
        try {
            String lockKey = TASK_LOCK_PREFIX + taskId;
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
        } catch (Exception e) {
            log.error("Failed to check task lock status: taskId={}, error={}", taskId, e.getMessage());
            return false;
        }
    }

    // ==================== Scaling metrics ====================

    /**
     * Get scaling metrics
     */
    public ScalingMetrics getScalingMetrics() {
        List<ClusterNodeInfo> activeNodes = getActiveNodes();
        
        // Calculate total cluster load
        double totalLoad = activeNodes.stream()
                .mapToDouble(ClusterNodeInfo::getLoadScore)
                .sum();
        
        double avgLoad = activeNodes.isEmpty() ? 0 : totalLoad / activeNodes.size();
        
        // Calculate load variance (to determine if load is balanced)
        double loadVariance = 0;
        if (!activeNodes.isEmpty()) {
            for (ClusterNodeInfo node : activeNodes) {
                loadVariance += Math.pow(node.getLoadScore() - avgLoad, 2);
            }
            loadVariance /= activeNodes.size();
        }
        
        // Determine if scaling is needed
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
     * Record task processing
     */
    public void recordTaskProcessed(String taskId, long processingTimeMs) {
        processedTasks.incrementAndGet();
        taskProcessingTimes.put(taskId, processingTimeMs);
        
        // Keep processing times for the last 1000 tasks
        if (taskProcessingTimes.size() > 1000) {
            String oldestKey = taskProcessingTimes.keySet().iterator().next();
            taskProcessingTimes.remove(oldestKey);
        }
    }

    /**
     * Increment active connections
     */
    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    /**
     * Decrement active connections
     */
    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    // ==================== Hot deployment support ====================

    /**
     * Prepare for hot deployment (graceful shutdown)
     */
    public void prepareForHotDeploy() {
        log.info("Preparing for hot deployment: nodeId={}", nodeId);
        
        // Stop accepting new tasks
        // Wait for current tasks to complete
        // Release leadership
        if (isLeader) {
            releaseLeadership();
        }
        
        // Mark as draining in node list
        try {
            ClusterNodeInfo nodeInfo = buildCurrentNodeInfo();
            nodeInfo.setStatus("DRAINING");
            String nodeJson = serializeNodeInfo(nodeInfo);
            stringRedisTemplate.opsForHash().put(NODE_REGISTRY, nodeId, nodeJson);
        } catch (Exception e) {
            log.error("Failed to mark node draining status: {}", e.getMessage());
        }
    }

    /**
     * Complete hot deployment (back online)
     */
    public void completeHotDeploy() {
        log.info("Hot deployment completed: nodeId={}", nodeId);
        
        // Re-register node
        registerNode();
    }

    // ==================== Private helper methods ====================

    /**
     * Generate node ID
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
     * Build current node info
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
     * Calculate current load score
     */
    private double calculateCurrentLoadScore() {
        // Calculate load score based on active connections and processed tasks
        long connections = activeConnections.get();
        
        // Simplified load calculation: connections / 100 (assuming max 100 connections)
        double connectionLoad = Math.min(1.0, connections / 100.0);
        
        // Can add more factors: CPU usage, memory usage, etc.
        return connectionLoad;
    }

    /**
     * Calculate recommended node count
     */
    private int calculateRecommendedNodeCount(double avgLoad, int currentNodes) {
        if (avgLoad > 0.8) {
            // Load too high, recommend adding nodes
            return (int) Math.ceil(currentNodes * avgLoad / 0.6);
        } else if (avgLoad < 0.2 && currentNodes > 1) {
            // Load too low, recommend removing nodes
            return Math.max(1, (int) Math.ceil(currentNodes * avgLoad / 0.4));
        }
        return currentNodes;
    }

    /**
     * Cleaned up expired node
     */
    private void cleanupExpiredNodes() {
        try {
            Map<Object, Object> nodeEntries = stringRedisTemplate.opsForHash().entries(NODE_REGISTRY);
            
            for (Object entryNodeId : nodeEntries.keySet()) {
                String targetNodeId = (String) entryNodeId;
                if (!isNodeAlive(targetNodeId)) {
                    stringRedisTemplate.opsForHash().delete(NODE_REGISTRY, targetNodeId);
                    nodeCache.remove(targetNodeId);
                    log.info("Cleaned up expired node: nodeId={}", targetNodeId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to clean up expired nodes: {}", e.getMessage());
        }
    }

    /**
     * Serialize node info
     */
    private String serializeNodeInfo(ClusterNodeInfo nodeInfo) {
        // Simplified serialization; should use JSON in production
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
     * Deserialize node info
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
            log.error("Failed to deserialize node info: {}", e.getMessage());
            return null;
        }
    }

    // ==================== Getter methods ====================

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
