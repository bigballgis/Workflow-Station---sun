package com.developer.component.impl;

import com.developer.component.HelpSystemComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 帮助系统组件实现
 */
@Component
@Slf4j
public class HelpSystemComponentImpl implements HelpSystemComponent {
    
    // 表达式关键字
    private static final Map<String, List<String>> EXPRESSION_KEYWORDS = Map.of(
            "variable", List.of("${", "processInstance", "task", "execution", "variables"),
            "function", List.of("now()", "currentUser()", "hasRole()", "isEmpty()", "isNotEmpty()"),
            "operator", List.of("==", "!=", ">", "<", ">=", "<=", "&&", "||", "!")
    );
    
    // 帮助文档
    private static final Map<String, Map<String, Object>> HELP_DOCUMENTS = new HashMap<>();
    
    static {
        HELP_DOCUMENTS.put("process-designer", Map.of(
                "title", "流程设计器使用指南",
                "content", "流程设计器用于创建和编辑BPMN流程...",
                "category", "designer"
        ));
        HELP_DOCUMENTS.put("table-designer", Map.of(
                "title", "表设计器使用指南",
                "content", "表设计器用于定义数据表结构...",
                "category", "designer"
        ));
    }
    
    @Override
    public List<Map<String, Object>> search(String keyword, String category, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : HELP_DOCUMENTS.entrySet()) {
            Map<String, Object> doc = entry.getValue();
            String title = (String) doc.get("title");
            String content = (String) doc.get("content");
            String docCategory = (String) doc.get("category");

            if (category != null && !category.equals(docCategory)) {
                continue;
            }
            
            if (keyword == null || title.contains(keyword) || content.contains(keyword)) {
                Map<String, Object> result = new HashMap<>(doc);
                result.put("id", entry.getKey());
                results.add(result);
                if (results.size() >= limit) {
                    break;
                }
            }
        }
        
        return results;
    }
    
    @Override
    public List<Map<String, Object>> getExpressionSuggestions(String prefix, String context) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : EXPRESSION_KEYWORDS.entrySet()) {
            String category = entry.getKey();
            for (String keyword : entry.getValue()) {
                if (prefix == null || keyword.toLowerCase().startsWith(prefix.toLowerCase())) {
                    Map<String, Object> suggestion = new HashMap<>();
                    suggestion.put("text", keyword);
                    suggestion.put("category", category);
                    suggestion.put("description", getKeywordDescription(keyword));
                    suggestions.add(suggestion);
                }
            }
        }
        
        return suggestions;
    }
    
    @Override
    public Map<String, Object> getContextHelp(String context, String elementType) {
        Map<String, Object> help = new HashMap<>();
        help.put("context", context);
        help.put("elementType", elementType);
        
        switch (elementType) {
            case "userTask":
                help.put("title", "用户任务");
                help.put("description", "用户任务需要人工处理，可以配置分配方式和表单");
                help.put("tips", List.of("配置任务分配人", "绑定表单", "设置动作按钮"));
                break;
            case "exclusiveGateway":
                help.put("title", "排他网关");
                help.put("description", "排他网关用于条件分支，只有一个分支会被执行");
                help.put("tips", List.of("配置条件表达式", "设置默认分支"));
                break;
            default:
                help.put("title", elementType);
                help.put("description", "暂无帮助信息");
        }
        
        return help;
    }
    
    @Override
    public Map<String, Object> getHelpDocument(String documentId) {
        return HELP_DOCUMENTS.getOrDefault(documentId, Map.of(
                "title", "文档未找到",
                "content", "请求的帮助文档不存在"
        ));
    }
    
    @Override
    public List<Map<String, Object>> getGuidedTourSteps(String tourId) {
        List<Map<String, Object>> steps = new ArrayList<>();
        
        if ("process-designer-tour".equals(tourId)) {
            steps.add(Map.of("target", ".toolbox", "title", "工具箱", 
                    "content", "从这里拖拽元素到画布"));
            steps.add(Map.of("target", ".canvas", "title", "画布", 
                    "content", "在这里设计您的流程"));
            steps.add(Map.of("target", ".properties-panel", "title", "属性面板", 
                    "content", "选中元素后在这里配置属性"));
        }
        
        return steps;
    }
    
    private String getKeywordDescription(String keyword) {
        return switch (keyword) {
            case "${" -> "变量表达式开始符";
            case "processInstance" -> "流程实例对象";
            case "task" -> "当前任务对象";
            case "now()" -> "获取当前时间";
            case "currentUser()" -> "获取当前用户";
            default -> keyword;
        };
    }
}
