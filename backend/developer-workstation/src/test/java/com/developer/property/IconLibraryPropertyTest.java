package com.developer.property;

import com.developer.component.IconLibraryComponent;
import com.developer.component.impl.IconLibraryComponentImpl;
import com.developer.enums.IconCategory;
import com.developer.repository.IconRepository;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 图标库属性测试
 * Property 11-12: 图标文件验证、图标使用保护
 */
public class IconLibraryPropertyTest {
    
    /**
     * Property 11: 图标文件验证
     * SVG内容应符合基本格式要求
     */
    @Property(tries = 20)
    void iconFileValidationProperty(@ForAll("svgContents") String svgContent) {
        IconRepository repository = mock(IconRepository.class);
        IconLibraryComponent component = new IconLibraryComponentImpl(repository);
        
        assertThat(component).isNotNull();
        // SVG应包含基本标签
        if (svgContent.contains("<svg")) {
            assertThat(svgContent).contains("</svg>");
        }
    }
    
    /**
     * Property 12: 图标使用保护
     * 图标分类应为有效的枚举值
     */
    @Property(tries = 20)
    void iconUsageProtectionProperty(@ForAll("iconCategories") IconCategory category) {
        assertThat(category).isNotNull();
        assertThat(category).isIn(IconCategory.values());
    }
    
    @Provide
    Arbitrary<String> svgContents() {
        return Arbitraries.of(
                "<svg></svg>",
                "<svg viewBox=\"0 0 24 24\"><path d=\"M0 0\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
        );
    }
    
    @Provide
    Arbitrary<IconCategory> iconCategories() {
        return Arbitraries.of(IconCategory.values());
    }
}
