package com.admin.properties;

import com.admin.component.DataDictionaryManagerComponent;
import com.admin.entity.Dictionary;
import com.admin.entity.DictionaryItem;
import com.admin.enums.DictionaryStatus;
import com.admin.enums.DictionaryType;
import com.admin.repository.DictionaryItemRepository;
import com.admin.repository.DictionaryRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 数据字典多语言一致性属性测试
 * 属性 11: 数据字典多语言一致性
 * 验证需求: 需求 6.4
 */
class DictionaryMultiLanguageProperties {
    
    private DataDictionaryManagerComponent component;
    private DictionaryRepository dictionaryRepository;
    private DictionaryItemRepository itemRepository;
    
    @BeforeTry
    void setUp() {
        dictionaryRepository = Mockito.mock(DictionaryRepository.class);
        itemRepository = Mockito.mock(DictionaryItemRepository.class);
        var versionRepository = Mockito.mock(com.admin.repository.DictionaryVersionRepository.class);
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        
        component = new DataDictionaryManagerComponent(
                dictionaryRepository, itemRepository, versionRepository, objectMapper);
    }
    
    // ==================== 属性测试 ====================
    
    /**
     * 属性 11.1: 对于任意字典项，获取英文名称时应返回英文名称或默认名称
     * Feature: admin-center, Property 11.1: English name fallback
     * Validates: Requirements 6.4
     */
    @Property(tries = 20)
    void englishNameFallbackProperty(
            @ForAll("dictionaryItems") DictionaryItem item) {
        
        String result = item.getNameByLanguage("en");
        
        if (item.getNameEn() != null) {
            assertThat(result).isEqualTo(item.getNameEn());
        } else {
            assertThat(result).isEqualTo(item.getName());
        }
    }

    
    /**
     * 属性 11.2: 对于任意字典项，获取简体中文名称时应返回简体中文名称或默认名称
     * Feature: admin-center, Property 11.2: Simplified Chinese name fallback
     * Validates: Requirements 6.4
     */
    @Property(tries = 20)
    void simplifiedChineseNameFallbackProperty(
            @ForAll("dictionaryItems") DictionaryItem item) {
        
        String result = item.getNameByLanguage("zh-CN");
        
        if (item.getNameZhCn() != null) {
            assertThat(result).isEqualTo(item.getNameZhCn());
        } else {
            assertThat(result).isEqualTo(item.getName());
        }
    }
    
    /**
     * 属性 11.3: 对于任意字典项，获取繁体中文名称时应返回繁体中文名称或默认名称
     * Feature: admin-center, Property 11.3: Traditional Chinese name fallback
     * Validates: Requirements 6.4
     */
    @Property(tries = 20)
    void traditionalChineseNameFallbackProperty(
            @ForAll("dictionaryItems") DictionaryItem item) {
        
        String result = item.getNameByLanguage("zh-TW");
        
        if (item.getNameZhTw() != null) {
            assertThat(result).isEqualTo(item.getNameZhTw());
        } else {
            assertThat(result).isEqualTo(item.getName());
        }
    }
    
    /**
     * 属性 11.4: 对于任意未知语言，应返回默认名称
     * Feature: admin-center, Property 11.4: Unknown language fallback to default
     * Validates: Requirements 6.4
     */
    @Property(tries = 20)
    void unknownLanguageFallbackProperty(
            @ForAll("dictionaryItems") DictionaryItem item,
            @ForAll("unknownLanguages") String unknownLanguage) {
        
        String result = item.getNameByLanguage(unknownLanguage);
        
        assertThat(result).isEqualTo(item.getName());
    }
    
    /**
     * 属性 11.5: 对于任意字典项，所有支持的语言都应返回非空名称
     * Feature: admin-center, Property 11.5: All supported languages return non-null
     * Validates: Requirements 6.4
     */
    @Property(tries = 20)
    void allSupportedLanguagesReturnNonNullProperty(
            @ForAll("dictionaryItems") DictionaryItem item,
            @ForAll("supportedLanguages") String language) {
        
        String result = item.getNameByLanguage(language);
        
        assertThat(result).isNotNull();
    }
    
    /**
     * 属性 11.6: 本地化字典项列表应保持原始排序顺序
     * Feature: admin-center, Property 11.6: Localized items preserve sort order
     * Validates: Requirements 6.4
     */
    @Property(tries = 20)
    void localizedItemsPreserveSortOrderProperty(
            @ForAll("dictionaryItemLists") List<DictionaryItem> items,
            @ForAll("supportedLanguages") String language) {
        
        String dictionaryId = "test-dict-" + UUID.randomUUID();
        
        // 设置字典ID
        Dictionary dictionary = createTestDictionary(dictionaryId);
        items.forEach(item -> item.setDictionary(dictionary));
        
        when(itemRepository.findValidItems(dictionaryId)).thenReturn(items);
        
        List<DataDictionaryManagerComponent.DictionaryItemLocalized> localized = 
                component.getDictionaryItemsLocalized(dictionaryId, language);
        
        // 验证排序顺序保持一致
        assertThat(localized).hasSize(items.size());
        for (int i = 0; i < items.size(); i++) {
            assertThat(localized.get(i).getItemCode()).isEqualTo(items.get(i).getItemCode());
            assertThat(localized.get(i).getSortOrder()).isEqualTo(items.get(i).getSortOrder());
        }
    }
    
    /**
     * 属性 11.7: 更新翻译后，获取对应语言应返回更新后的值
     * Feature: admin-center, Property 11.7: Translation update consistency
     * Validates: Requirements 6.4
     */
    @Property(tries = 20)
    void translationUpdateConsistencyProperty(
            @ForAll("dictionaryItems") DictionaryItem item,
            @ForAll("translations") Map<String, String> translations) {
        
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(itemRepository.save(any(DictionaryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        
        component.updateItemTranslations(item.getId(), translations, "test-user");
        
        // 验证翻译已更新
        if (translations.containsKey("en")) {
            assertThat(item.getNameEn()).isEqualTo(translations.get("en"));
        }
        if (translations.containsKey("zh-CN")) {
            assertThat(item.getNameZhCn()).isEqualTo(translations.get("zh-CN"));
        }
        if (translations.containsKey("zh-TW")) {
            assertThat(item.getNameZhTw()).isEqualTo(translations.get("zh-TW"));
        }
    }

    
    // ==================== 数据生成器 ====================
    
    @Provide
    Arbitrary<DictionaryItem> dictionaryItems() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),  // name
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).injectNull(0.3),  // nameEn
                Arbitraries.strings().ofMinLength(1).ofMaxLength(20).injectNull(0.3),  // nameZhCn
                Arbitraries.strings().ofMinLength(1).ofMaxLength(20).injectNull(0.3),  // nameZhTw
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),  // itemCode
                Arbitraries.integers().between(0, 100)  // sortOrder
        ).as((name, nameEn, nameZhCn, nameZhTw, itemCode, sortOrder) -> {
            DictionaryItem item = new DictionaryItem();
            item.setId(UUID.randomUUID().toString());
            item.setItemCode(itemCode);
            item.setName(name);
            item.setNameEn(nameEn);
            item.setNameZhCn(nameZhCn);
            item.setNameZhTw(nameZhTw);
            item.setValue(itemCode);
            item.setStatus(DictionaryStatus.ACTIVE);
            item.setSortOrder(sortOrder);
            item.setCreatedAt(Instant.now());
            item.setUpdatedAt(Instant.now());
            return item;
        });
    }
    
    @Provide
    Arbitrary<List<DictionaryItem>> dictionaryItemLists() {
        return dictionaryItems().list().ofMinSize(1).ofMaxSize(10)
                .map(items -> {
                    // 确保排序顺序唯一
                    for (int i = 0; i < items.size(); i++) {
                        items.get(i).setSortOrder(i);
                        items.get(i).setItemCode("item-" + i);
                    }
                    return items;
                });
    }
    
    @Provide
    Arbitrary<String> supportedLanguages() {
        return Arbitraries.of("en", "zh-CN", "zh-TW");
    }
    
    @Provide
    Arbitrary<String> unknownLanguages() {
        return Arbitraries.of("fr", "de", "ja", "ko", "es", "pt", "ru", "ar");
    }
    
    @Provide
    Arbitrary<Map<String, String>> translations() {
        return Arbitraries.maps(
                Arbitraries.of("en", "zh-CN", "zh-TW"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
        ).ofMinSize(1).ofMaxSize(3);
    }
    
    // ==================== 辅助方法 ====================
    
    private Dictionary createTestDictionary(String id) {
        return Dictionary.builder()
                .id(id)
                .code("TEST_DICT")
                .name("Test Dictionary")
                .type(DictionaryType.CUSTOM)
                .status(DictionaryStatus.ACTIVE)
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
