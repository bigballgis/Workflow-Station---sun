package com.admin.controller;

import com.admin.component.DataDictionaryManagerComponent;
import com.admin.component.DataDictionaryManagerComponent.*;
import com.admin.entity.Dictionary;
import com.admin.entity.DictionaryItem;
import com.admin.entity.DictionaryVersion;
import com.admin.enums.DictionaryStatus;
import com.admin.enums.DictionaryType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dictionaries")
@RequiredArgsConstructor
@Tag(name = "数据字典管理", description = "字典CRUD、字典项查询和关联数据查询接口")
public class DictionaryController {
    
    private final DataDictionaryManagerComponent dictionaryManager;
    
    // ==================== 字典 CRUD ====================
    
    @PostMapping
    @Operation(summary = "创建字典")
    public ResponseEntity<Dictionary> createDictionary(
            @Valid @RequestBody DictionaryCreateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        Dictionary dictionary = dictionaryManager.createDictionary(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dictionary);
    }
    
    @GetMapping
    @Operation(summary = "获取字典列表")
    public ResponseEntity<List<Dictionary>> listDictionaries(
            @RequestParam(required = false) DictionaryType type,
            @RequestParam(required = false) DictionaryStatus status) {
        return ResponseEntity.ok(dictionaryManager.listDictionaries(type, status));
    }
    
    @GetMapping("/page")
    @Operation(summary = "分页获取字典列表")
    public ResponseEntity<Page<Dictionary>> listDictionariesPaged(Pageable pageable) {
        return ResponseEntity.ok(dictionaryManager.listDictionaries(pageable));
    }

    
    @GetMapping("/{id}")
    @Operation(summary = "获取字典详情")
    public ResponseEntity<Dictionary> getDictionary(@PathVariable String id) {
        return ResponseEntity.ok(dictionaryManager.getDictionaryById(id));
    }
    
    @GetMapping("/code/{code}")
    @Operation(summary = "根据代码获取字典")
    public ResponseEntity<Dictionary> getDictionaryByCode(@PathVariable String code) {
        return ResponseEntity.ok(dictionaryManager.getDictionaryByCode(code));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新字典")
    public ResponseEntity<Dictionary> updateDictionary(
            @PathVariable String id,
            @Valid @RequestBody DictionaryUpdateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(dictionaryManager.updateDictionary(id, request, userId));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除字典")
    public ResponseEntity<Void> deleteDictionary(@PathVariable String id) {
        dictionaryManager.deleteDictionary(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/activate")
    @Operation(summary = "启用字典")
    public ResponseEntity<Dictionary> activateDictionary(
            @PathVariable String id, @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(dictionaryManager.activateDictionary(id, userId));
    }
    
    @PostMapping("/{id}/deactivate")
    @Operation(summary = "禁用字典")
    public ResponseEntity<Dictionary> deactivateDictionary(
            @PathVariable String id, @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(dictionaryManager.deactivateDictionary(id, userId));
    }
    
    @GetMapping("/search")
    @Operation(summary = "搜索字典")
    public ResponseEntity<List<Dictionary>> searchDictionaries(@RequestParam String keyword) {
        return ResponseEntity.ok(dictionaryManager.searchDictionaries(keyword));
    }
    
    // ==================== 字典项管理 ====================
    
    @PostMapping("/{dictionaryId}/items")
    @Operation(summary = "创建字典项")
    public ResponseEntity<DictionaryItem> createDictionaryItem(
            @PathVariable String dictionaryId,
            @Valid @RequestBody DictionaryItemRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dictionaryManager.createDictionaryItem(dictionaryId, request, userId));
    }
    
    @GetMapping("/{dictionaryId}/items")
    @Operation(summary = "获取字典项列表")
    public ResponseEntity<List<DictionaryItem>> getDictionaryItems(@PathVariable String dictionaryId) {
        return ResponseEntity.ok(dictionaryManager.getDictionaryItems(dictionaryId));
    }
    
    @GetMapping("/{dictionaryId}/items/valid")
    @Operation(summary = "获取有效字典项")
    public ResponseEntity<List<DictionaryItem>> getValidDictionaryItems(@PathVariable String dictionaryId) {
        return ResponseEntity.ok(dictionaryManager.getValidDictionaryItems(dictionaryId));
    }
    
    @GetMapping("/{dictionaryId}/items/localized")
    @Operation(summary = "获取本地化字典项")
    public ResponseEntity<List<DictionaryItemLocalized>> getLocalizedItems(
            @PathVariable String dictionaryId,
            @RequestParam(defaultValue = "zh-CN") String language) {
        return ResponseEntity.ok(dictionaryManager.getDictionaryItemsLocalized(dictionaryId, language));
    }
    
    @PutMapping("/items/{itemId}")
    @Operation(summary = "更新字典项")
    public ResponseEntity<DictionaryItem> updateDictionaryItem(
            @PathVariable String itemId,
            @Valid @RequestBody DictionaryItemRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(dictionaryManager.updateDictionaryItem(itemId, request, userId));
    }
    
    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "删除字典项")
    public ResponseEntity<Void> deleteDictionaryItem(
            @PathVariable String itemId, @RequestHeader("X-User-Id") String userId) {
        dictionaryManager.deleteDictionaryItem(itemId, userId);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/items/{itemId}/translations")
    @Operation(summary = "更新字典项翻译")
    public ResponseEntity<Void> updateItemTranslations(
            @PathVariable String itemId,
            @RequestBody Map<String, String> translations,
            @RequestHeader("X-User-Id") String userId) {
        dictionaryManager.updateItemTranslations(itemId, translations, userId);
        return ResponseEntity.ok().build();
    }
    
    // ==================== 版本管理 ====================
    
    @GetMapping("/{dictionaryId}/versions")
    @Operation(summary = "获取版本历史")
    public ResponseEntity<List<DictionaryVersion>> getVersionHistory(@PathVariable String dictionaryId) {
        return ResponseEntity.ok(dictionaryManager.getVersionHistory(dictionaryId));
    }
    
    @PostMapping("/{dictionaryId}/rollback/{version}")
    @Operation(summary = "回滚到指定版本")
    public ResponseEntity<Dictionary> rollbackToVersion(
            @PathVariable String dictionaryId,
            @PathVariable Integer version,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(dictionaryManager.rollbackToVersion(dictionaryId, version, userId));
    }
}
