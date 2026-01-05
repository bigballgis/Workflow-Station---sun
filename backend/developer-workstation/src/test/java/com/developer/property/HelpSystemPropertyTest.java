package com.developer.property;

import com.developer.component.HelpSystemComponent;
import com.developer.component.impl.HelpSystemComponentImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * 帮助系统属性测试
 * Property 24-25: 表达式智能补全、搜索结果相关性
 */
public class HelpSystemPropertyTest {
    
    private final HelpSystemComponent helpSystemComponent = new HelpSystemComponentImpl();
    
    /**
     * Property 24: 表达式智能补全
     * 补全建议应返回有效结果
     */
    @Property(tries = 20)
    void expressionAutoCompleteProperty(@ForAll("expressionPrefixes") String prefix) {
        List<Map<String, Object>> suggestions = 
                helpSystemComponent.getExpressionSuggestions(prefix, "default");
        
        assertThat(suggestions).isNotNull();
        // 每个建议应包含text字段
        for (Map<String, Object> suggestion : suggestions) {
            assertThat(suggestion).containsKey("text");
        }
    }
    
    /**
     * Property 25: 搜索结果相关性
     * 搜索应返回有限数量的结果
     */
    @Property(tries = 20)
    void searchResultRelevanceProperty(
            @ForAll("searchKeywords") String keyword,
            @ForAll @IntRange(min = 1, max = 100) int limit) {
        
        List<Map<String, Object>> results = 
                helpSystemComponent.search(keyword, null, limit);
        
        assertThat(results).isNotNull();
        assertThat(results.size()).isLessThanOrEqualTo(limit);
    }
    
    @Provide
    Arbitrary<String> expressionPrefixes() {
        return Arbitraries.of("$", "${", "now", "current", "has", "is");
    }
    
    @Provide
    Arbitrary<String> searchKeywords() {
        return Arbitraries.of("流程", "表单", "表", "动作", "process", "form", "table");
    }
}
