package com.admin.component;

import com.admin.entity.Dictionary;
import com.admin.entity.DictionaryItem;
import com.admin.entity.DictionaryVersion;
import com.admin.enums.DictionaryStatus;
import com.admin.enums.DictionaryType;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.DictionaryItemRepository;
import com.admin.repository.DictionaryRepository;
import com.admin.repository.DictionaryVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据字典管理组件
 * 负责字典分类管理、字典项管理、多语言支持和版本控制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataDictionaryManagerComponent {
    
    private final DictionaryRepository dictionaryRepository;
    private final DictionaryItemRepository itemRepository;
    private final DictionaryVersionRepository versionRepository;
    private final ObjectMapper objectMapper;
    
    // ==================== 字典管理 ====================
    
    /**
     * 创建字典
     */
    @Transactional
    public Dictionary createDictionary(DictionaryCreateRequest request, String creatorId) {
        log.info("Creating dictionary: {}", request.getCode());
        
        // 验证代码唯一性
        if (dictionaryRepository.existsByCode(request.getCode())) {
            throw new AdminBusinessException("DUPLICATE_CODE", "字典代码已存在: " + request.getCode());
        }
        
        Dictionary dictionary = Dictionary.builder()
                .id(UUID.randomUUID().toString())
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .status(DictionaryStatus.DRAFT)
                .dataSourceType(request.getDataSourceType())
                .dataSourceConfig(request.getDataSourceConfig())
                .cacheTtl(request.getCacheTtl() != null ? request.getCacheTtl() : 0)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .version(1)
                .createdBy(creatorId)
                .build();
        
        dictionary = dictionaryRepository.save(dictionary);
        log.info("Dictionary created: {}", dictionary.getId());
        return dictionary;
    }

    
    /**
     * 更新字典
     */
    @Transactional
    @CacheEvict(value = "dictionaries", key = "#id")
    public Dictionary updateDictionary(String id, DictionaryUpdateRequest request, String updaterId) {
        log.info("Updating dictionary: {}", id);
        
        Dictionary dictionary = getDictionaryById(id);
        
        // 系统字典不可编辑
        if (!dictionary.isEditable()) {
            throw new AdminBusinessException("NOT_EDITABLE", "系统字典不可编辑");
        }
        
        // 保存版本快照
        saveVersionSnapshot(dictionary, "更新前快照");
        
        if (request.getName() != null) {
            dictionary.setName(request.getName());
        }
        if (request.getDescription() != null) {
            dictionary.setDescription(request.getDescription());
        }
        if (request.getDataSourceType() != null) {
            dictionary.setDataSourceType(request.getDataSourceType());
        }
        if (request.getDataSourceConfig() != null) {
            dictionary.setDataSourceConfig(request.getDataSourceConfig());
        }
        if (request.getCacheTtl() != null) {
            dictionary.setCacheTtl(request.getCacheTtl());
        }
        if (request.getSortOrder() != null) {
            dictionary.setSortOrder(request.getSortOrder());
        }
        
        dictionary.setUpdatedBy(updaterId);
        dictionary.incrementVersion();
        
        dictionary = dictionaryRepository.save(dictionary);
        log.info("Dictionary updated: {}", dictionary.getId());
        return dictionary;
    }
    
    /**
     * 删除字典
     */
    @Transactional
    @CacheEvict(value = "dictionaries", key = "#id")
    public void deleteDictionary(String id) {
        log.info("Deleting dictionary: {}", id);
        
        Dictionary dictionary = getDictionaryById(id);
        
        // 系统字典不可删除
        if (!dictionary.isDeletable()) {
            throw new AdminBusinessException("NOT_DELETABLE", "系统字典不可删除");
        }
        
        // 删除所有字典项
        itemRepository.deleteByDictionaryId(id);
        
        // 删除所有版本历史
        versionRepository.deleteByDictionaryId(id);
        
        // 删除字典
        dictionaryRepository.delete(dictionary);
        log.info("Dictionary deleted: {}", id);
    }
    
    /**
     * 获取字典
     */
    @Cacheable(value = "dictionaries", key = "#id")
    public Dictionary getDictionaryById(String id) {
        return dictionaryRepository.findById(id)
                .orElseThrow(() -> new AdminBusinessException("NOT_FOUND", "字典不存在: " + id));
    }
    
    /**
     * 根据代码获取字典
     */
    @Cacheable(value = "dictionaries", key = "'code:' + #code")
    public Dictionary getDictionaryByCode(String code) {
        return dictionaryRepository.findByCode(code)
                .orElseThrow(() -> new AdminBusinessException("NOT_FOUND", "字典不存在: " + code));
    }
    
    /**
     * 获取字典列表
     */
    public List<Dictionary> listDictionaries(DictionaryType type, DictionaryStatus status) {
        if (type != null && status != null) {
            return dictionaryRepository.findByTypeAndStatus(type, status);
        } else if (type != null) {
            return dictionaryRepository.findByType(type);
        } else if (status != null) {
            return dictionaryRepository.findByStatus(status);
        }
        return dictionaryRepository.findAll();
    }
    
    /**
     * 分页获取字典列表
     */
    public Page<Dictionary> listDictionaries(Pageable pageable) {
        return dictionaryRepository.findAll(pageable);
    }
    
    /**
     * 搜索字典
     */
    public List<Dictionary> searchDictionaries(String keyword) {
        return dictionaryRepository.searchByKeyword(keyword);
    }
    
    /**
     * 启用字典
     */
    @Transactional
    @CacheEvict(value = "dictionaries", key = "#id")
    public Dictionary activateDictionary(String id, String operatorId) {
        Dictionary dictionary = getDictionaryById(id);
        dictionary.setStatus(DictionaryStatus.ACTIVE);
        dictionary.setUpdatedBy(operatorId);
        return dictionaryRepository.save(dictionary);
    }
    
    /**
     * 禁用字典
     */
    @Transactional
    @CacheEvict(value = "dictionaries", key = "#id")
    public Dictionary deactivateDictionary(String id, String operatorId) {
        Dictionary dictionary = getDictionaryById(id);
        
        if (dictionary.isSystemDictionary()) {
            throw new AdminBusinessException("NOT_ALLOWED", "系统字典不可禁用");
        }
        
        dictionary.setStatus(DictionaryStatus.INACTIVE);
        dictionary.setUpdatedBy(operatorId);
        return dictionaryRepository.save(dictionary);
    }

    
    // ==================== 字典项管理 ====================
    
    /**
     * 创建字典项
     */
    @Transactional
    @CacheEvict(value = "dictionaryItems", key = "#dictionaryId")
    public DictionaryItem createDictionaryItem(String dictionaryId, DictionaryItemRequest request, String creatorId) {
        log.info("Creating dictionary item: {} for dictionary: {}", request.getItemCode(), dictionaryId);
        
        Dictionary dictionary = getDictionaryById(dictionaryId);
        
        // 验证代码唯一性
        if (itemRepository.existsByDictionaryIdAndItemCode(dictionaryId, request.getItemCode())) {
            throw new AdminBusinessException("DUPLICATE_CODE", "字典项代码已存在: " + request.getItemCode());
        }
        
        DictionaryItem item = DictionaryItem.builder()
                .id(UUID.randomUUID().toString())
                .dictionary(dictionary)
                .itemCode(request.getItemCode())
                .name(request.getName())
                .nameEn(request.getNameEn())
                .nameZhCn(request.getNameZhCn())
                .nameZhTw(request.getNameZhTw())
                .value(request.getValue())
                .description(request.getDescription())
                .status(DictionaryStatus.ACTIVE)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .extAttributes(request.getExtAttributes())
                .createdBy(creatorId)
                .build();
        
        // 设置父级
        if (request.getParentId() != null) {
            DictionaryItem parent = itemRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AdminBusinessException("NOT_FOUND", "父级字典项不存在"));
            item.setParent(parent);
        }
        
        item = itemRepository.save(item);
        
        // 更新字典版本
        dictionary.incrementVersion();
        dictionary.setUpdatedBy(creatorId);
        dictionaryRepository.save(dictionary);
        
        log.info("Dictionary item created: {}", item.getId());
        return item;
    }
    
    /**
     * 更新字典项
     */
    @Transactional
    @CacheEvict(value = "dictionaryItems", allEntries = true)
    public DictionaryItem updateDictionaryItem(String itemId, DictionaryItemRequest request, String updaterId) {
        log.info("Updating dictionary item: {}", itemId);
        
        DictionaryItem item = getDictionaryItemById(itemId);
        Dictionary dictionary = item.getDictionary();
        
        if (request.getName() != null) {
            item.setName(request.getName());
        }
        if (request.getNameEn() != null) {
            item.setNameEn(request.getNameEn());
        }
        if (request.getNameZhCn() != null) {
            item.setNameZhCn(request.getNameZhCn());
        }
        if (request.getNameZhTw() != null) {
            item.setNameZhTw(request.getNameZhTw());
        }
        if (request.getValue() != null) {
            item.setValue(request.getValue());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        if (request.getSortOrder() != null) {
            item.setSortOrder(request.getSortOrder());
        }
        if (request.getValidFrom() != null) {
            item.setValidFrom(request.getValidFrom());
        }
        if (request.getValidTo() != null) {
            item.setValidTo(request.getValidTo());
        }
        if (request.getExtAttributes() != null) {
            item.setExtAttributes(request.getExtAttributes());
        }
        
        item.setUpdatedBy(updaterId);
        item = itemRepository.save(item);
        
        // 更新字典版本
        dictionary.incrementVersion();
        dictionary.setUpdatedBy(updaterId);
        dictionaryRepository.save(dictionary);
        
        log.info("Dictionary item updated: {}", item.getId());
        return item;
    }
    
    /**
     * 删除字典项
     */
    @Transactional
    @CacheEvict(value = "dictionaryItems", allEntries = true)
    public void deleteDictionaryItem(String itemId, String operatorId) {
        log.info("Deleting dictionary item: {}", itemId);
        
        DictionaryItem item = getDictionaryItemById(itemId);
        Dictionary dictionary = item.getDictionary();
        
        // 检查是否有子项
        if (item.hasChildren()) {
            throw new AdminBusinessException("HAS_CHILDREN", "字典项存在子项，无法删除");
        }
        
        itemRepository.delete(item);
        
        // 更新字典版本
        dictionary.incrementVersion();
        dictionary.setUpdatedBy(operatorId);
        dictionaryRepository.save(dictionary);
        
        log.info("Dictionary item deleted: {}", itemId);
    }
    
    /**
     * 获取字典项
     */
    public DictionaryItem getDictionaryItemById(String itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new AdminBusinessException("NOT_FOUND", "字典项不存在: " + itemId));
    }
    
    /**
     * 获取字典的所有字典项
     */
    @Cacheable(value = "dictionaryItems", key = "#dictionaryId")
    public List<DictionaryItem> getDictionaryItems(String dictionaryId) {
        return itemRepository.findByDictionaryIdOrderBySortOrder(dictionaryId);
    }
    
    /**
     * 获取字典的有效字典项
     */
    public List<DictionaryItem> getValidDictionaryItems(String dictionaryId) {
        return itemRepository.findValidItems(dictionaryId);
    }
    
    /**
     * 获取字典的顶级字典项（树形结构）
     */
    public List<DictionaryItem> getTopLevelItems(String dictionaryId) {
        return itemRepository.findTopLevelItems(dictionaryId);
    }
    
    /**
     * 根据字典代码获取字典项
     */
    public List<DictionaryItem> getDictionaryItemsByCode(String dictCode) {
        return itemRepository.findByDictionaryCode(dictCode);
    }
    
    /**
     * 启用字典项
     */
    @Transactional
    @CacheEvict(value = "dictionaryItems", allEntries = true)
    public DictionaryItem activateDictionaryItem(String itemId, String operatorId) {
        DictionaryItem item = getDictionaryItemById(itemId);
        item.setStatus(DictionaryStatus.ACTIVE);
        item.setUpdatedBy(operatorId);
        return itemRepository.save(item);
    }
    
    /**
     * 禁用字典项
     */
    @Transactional
    @CacheEvict(value = "dictionaryItems", allEntries = true)
    public DictionaryItem deactivateDictionaryItem(String itemId, String operatorId) {
        DictionaryItem item = getDictionaryItemById(itemId);
        item.setStatus(DictionaryStatus.INACTIVE);
        item.setUpdatedBy(operatorId);
        return itemRepository.save(item);
    }

    
    // ==================== 多语言支持 ====================
    
    /**
     * 获取指定语言的字典项列表
     */
    public List<DictionaryItemLocalized> getDictionaryItemsLocalized(String dictionaryId, String language) {
        List<DictionaryItem> items = getValidDictionaryItems(dictionaryId);
        return items.stream()
                .map(item -> DictionaryItemLocalized.builder()
                        .id(item.getId())
                        .itemCode(item.getItemCode())
                        .name(item.getNameByLanguage(language))
                        .value(item.getValue())
                        .sortOrder(item.getSortOrder())
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * 批量更新字典项的多语言名称
     */
    @Transactional
    @CacheEvict(value = "dictionaryItems", allEntries = true)
    public void updateItemTranslations(String itemId, Map<String, String> translations, String updaterId) {
        DictionaryItem item = getDictionaryItemById(itemId);
        
        if (translations.containsKey("en")) {
            item.setNameEn(translations.get("en"));
        }
        if (translations.containsKey("zh-CN")) {
            item.setNameZhCn(translations.get("zh-CN"));
        }
        if (translations.containsKey("zh-TW")) {
            item.setNameZhTw(translations.get("zh-TW"));
        }
        
        item.setUpdatedBy(updaterId);
        itemRepository.save(item);
    }
    
    // ==================== 版本控制 ====================
    
    /**
     * 保存版本快照
     */
    @Transactional
    public DictionaryVersion saveVersionSnapshot(Dictionary dictionary, String changeDescription) {
        try {
            // 构建快照数据
            DictionarySnapshot snapshot = DictionarySnapshot.builder()
                    .dictionary(dictionary)
                    .items(getDictionaryItems(dictionary.getId()))
                    .build();
            
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            
            DictionaryVersion version = DictionaryVersion.builder()
                    .id(UUID.randomUUID().toString())
                    .dictionaryId(dictionary.getId())
                    .version(dictionary.getVersion())
                    .snapshotData(snapshotJson)
                    .changeDescription(changeDescription)
                    .createdBy(dictionary.getUpdatedBy())
                    .build();
            
            return versionRepository.save(version);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize dictionary snapshot", e);
            throw new AdminBusinessException("SERIALIZATION_ERROR", "序列化字典快照失败");
        }
    }
    
    /**
     * 获取版本历史
     */
    public List<DictionaryVersion> getVersionHistory(String dictionaryId) {
        return versionRepository.findByDictionaryIdOrderByVersionDesc(dictionaryId);
    }
    
    /**
     * 回滚到指定版本
     */
    @Transactional
    @CacheEvict(value = {"dictionaries", "dictionaryItems"}, allEntries = true)
    public Dictionary rollbackToVersion(String dictionaryId, Integer targetVersion, String operatorId) {
        log.info("Rolling back dictionary {} to version {}", dictionaryId, targetVersion);
        
        Dictionary dictionary = getDictionaryById(dictionaryId);
        
        // 系统字典不可回滚
        if (dictionary.isSystemDictionary()) {
            throw new AdminBusinessException("NOT_ALLOWED", "系统字典不可回滚");
        }
        
        // 获取目标版本快照
        DictionaryVersion version = versionRepository.findByDictionaryIdAndVersion(dictionaryId, targetVersion)
                .orElseThrow(() -> new AdminBusinessException("NOT_FOUND", "版本不存在: " + targetVersion));
        
        try {
            // 保存当前版本快照
            saveVersionSnapshot(dictionary, "回滚前快照");
            
            // 解析快照数据
            DictionarySnapshot snapshot = objectMapper.readValue(version.getSnapshotData(), DictionarySnapshot.class);
            
            // 删除当前所有字典项
            itemRepository.deleteByDictionaryId(dictionaryId);
            
            // 恢复字典项
            for (DictionaryItem item : snapshot.getItems()) {
                item.setId(UUID.randomUUID().toString());
                item.setDictionary(dictionary);
                item.setCreatedAt(Instant.now());
                item.setUpdatedAt(Instant.now());
                itemRepository.save(item);
            }
            
            // 更新字典版本
            dictionary.incrementVersion();
            dictionary.setUpdatedBy(operatorId);
            dictionary = dictionaryRepository.save(dictionary);
            
            log.info("Dictionary rolled back to version {}", targetVersion);
            return dictionary;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize dictionary snapshot", e);
            throw new AdminBusinessException("DESERIALIZATION_ERROR", "反序列化字典快照失败");
        }
    }

    
    // ==================== 内部类 ====================
    
    /**
     * 字典创建请求
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DictionaryCreateRequest {
        private String code;
        private String name;
        private String description;
        private DictionaryType type;
        private com.admin.enums.DataSourceType dataSourceType;
        private String dataSourceConfig;
        private Integer cacheTtl;
        private Integer sortOrder;
    }
    
    /**
     * 字典更新请求
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DictionaryUpdateRequest {
        private String name;
        private String description;
        private com.admin.enums.DataSourceType dataSourceType;
        private String dataSourceConfig;
        private Integer cacheTtl;
        private Integer sortOrder;
    }
    
    /**
     * 字典项请求
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DictionaryItemRequest {
        private String parentId;
        private String itemCode;
        private String name;
        private String nameEn;
        private String nameZhCn;
        private String nameZhTw;
        private String value;
        private String description;
        private Integer sortOrder;
        private Instant validFrom;
        private Instant validTo;
        private String extAttributes;
    }
    
    /**
     * 本地化字典项
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DictionaryItemLocalized {
        private String id;
        private String itemCode;
        private String name;
        private String value;
        private Integer sortOrder;
    }
    
    /**
     * 字典快照
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DictionarySnapshot {
        private Dictionary dictionary;
        private List<DictionaryItem> items;
    }
}
