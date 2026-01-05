package com.developer.component;

import java.util.List;
import java.util.Map;

/**
 * 帮助系统组件接口
 */
public interface HelpSystemComponent {
    
    /**
     * 搜索帮助内容
     */
    List<Map<String, Object>> search(String keyword, String category, int limit);
    
    /**
     * 获取表达式智能补全建议
     */
    List<Map<String, Object>> getExpressionSuggestions(String prefix, String context);
    
    /**
     * 获取上下文帮助
     */
    Map<String, Object> getContextHelp(String context, String elementType);
    
    /**
     * 获取帮助文档
     */
    Map<String, Object> getHelpDocument(String documentId);
    
    /**
     * 获取新手引导步骤
     */
    List<Map<String, Object>> getGuidedTourSteps(String tourId);
}
