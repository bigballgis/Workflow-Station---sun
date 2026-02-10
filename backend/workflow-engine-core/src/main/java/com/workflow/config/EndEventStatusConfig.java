package com.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 结束节点状态配置
 * 用于配置如何从节点名称推断流程状态
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "workflow.end-event-status")
public class EndEventStatusConfig {
    
    /**
     * 表示拒绝状态的关键词列表
     * 当结束节点名称包含这些关键词时，流程状态为 REJECTED
     */
    private List<String> rejectedKeywords = new ArrayList<>();
    
    /**
     * 表示已完成状态的关键词列表
     * 当结束节点名称包含这些关键词时，流程状态为 COMPLETED
     */
    private List<String> completedKeywords = new ArrayList<>();
    
    /**
     * 默认状态（当没有匹配到任何关键词时使用）
     * 默认值：COMPLETED
     */
    private String defaultStatus = "COMPLETED";
    
    /**
     * 是否启用名称推断
     * 如果为 false，则总是返回默认状态
     */
    private boolean enableNameInference = true;
}
