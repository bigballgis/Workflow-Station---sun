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
                "title", "Process Designer Guide",
                "content", "The process designer is used to create and edit BPMN processes...",
                "category", "designer"
        ));
        HELP_DOCUMENTS.put("table-designer", Map.of(
                "title", "Table Designer Guide",
                "content", "The table designer is used to define data table structures...",
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
                help.put("title", "User Task");
                help.put("description", "User tasks require manual processing, you can configure assignment and forms");
                help.put("tips", List.of("Configure task assignee", "Bind form", "Set action buttons"));
                break;
            case "exclusiveGateway":
                help.put("title", "Exclusive Gateway");
                help.put("description", "Exclusive gateway is used for conditional branching, only one branch will be executed");
                help.put("tips", List.of("Configure condition expressions", "Set default branch"));
                break;
            default:
                help.put("title", elementType);
                help.put("description", "No help information available");
        }
        
        return help;
    }
    
    @Override
    public Map<String, Object> getHelpDocument(String documentId) {
        return HELP_DOCUMENTS.getOrDefault(documentId, Map.of(
                "title", "Document not found",
                "content", "The requested help document does not exist"
        ));
    }
    
    @Override
    public List<Map<String, Object>> getGuidedTourSteps(String tourId) {
        List<Map<String, Object>> steps = new ArrayList<>();
        
        if ("process-designer-tour".equals(tourId)) {
            steps.add(Map.of("target", ".toolbox", "title", "Toolbox", 
                    "content", "Drag elements from here to the canvas"));
            steps.add(Map.of("target", ".canvas", "title", "Canvas", 
                    "content", "Design your process here"));
            steps.add(Map.of("target", ".properties-panel", "title", "Properties Panel", 
                    "content", "Configure properties of selected elements here"));
        }
        
        return steps;
    }
    
    private String getKeywordDescription(String keyword) {
        return switch (keyword) {
            case "${" -> "Variable expression start marker";
            case "processInstance" -> "Process instance object";
            case "task" -> "Current task object";
            case "now()" -> "Get current time";
            case "currentUser()" -> "Get current user";
            default -> keyword;
        };
    }
}
