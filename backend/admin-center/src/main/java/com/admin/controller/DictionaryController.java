package com.admin.controller;

import com.admin.component.DataDictionaryManagerComponent;
import com.admin.component.DataDictionaryManagerComponent.*;
import com.admin.entity.Dictionary;
import com.admin.entity.DictionaryItem;
import com.admin.entity.DictionaryVersion;
import com.admin.enums.DictionaryStatus;
import com.admin.enums.DictionaryType;
import io.swagger.v3.oas.annotations.Operation;
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
import com.platform.security.util.SecurityContextUtils;
import com.platform.common.i18n.I18nService;

@Slf4j
@RestController
@RequestMapping("/dictionaries")
@RequiredArgsConstructor
@Tag(name = "Data Dictionary Management", description = "Dictionary CRUD, item query and related data query APIs")
public class DictionaryController {
    
    private final DataDictionaryManagerComponent dictionaryManager;
    private final I18nService i18nService;
    
    // ==================== Dictionary CRUD ====================
    
    @PostMapping
    @Operation(summary = "Create dictionary")
    public ResponseEntity<Dictionary> createDictionary(
            @Valid @RequestBody DictionaryCreateRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        Dictionary dictionary = dictionaryManager.createDictionary(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dictionary);
    }
    
    @GetMapping
    @Operation(summary = "Get dictionary list")
    public ResponseEntity<List<Dictionary>> listDictionaries(
            @RequestParam(required = false) DictionaryType type,
            @RequestParam(required = false) DictionaryStatus status) {
        return ResponseEntity.ok(dictionaryManager.listDictionaries(type, status));
    }
    
    @GetMapping("/page")
    @Operation(summary = "Get dictionary list (paged)")
    public ResponseEntity<Page<Dictionary>> listDictionariesPaged(Pageable pageable) {
        return ResponseEntity.ok(dictionaryManager.listDictionaries(pageable));
    }

    
    @GetMapping("/{id}")
    @Operation(summary = "Get dictionary detail")
    public ResponseEntity<Dictionary> getDictionary(@PathVariable String id) {
        return ResponseEntity.ok(dictionaryManager.getDictionaryById(id));
    }
    
    @GetMapping("/code/{code}")
    @Operation(summary = "Get dictionary by code")
    public ResponseEntity<Dictionary> getDictionaryByCode(@PathVariable String code) {
        return ResponseEntity.ok(dictionaryManager.getDictionaryByCode(code));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update dictionary")
    public ResponseEntity<Dictionary> updateDictionary(
            @PathVariable String id,
            @Valid @RequestBody DictionaryUpdateRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(dictionaryManager.updateDictionary(id, request, userId));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete dictionary")
    public ResponseEntity<Void> deleteDictionary(@PathVariable String id) {
        dictionaryManager.deleteDictionary(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate dictionary")
    public ResponseEntity<Dictionary> activateDictionary(
            @PathVariable String id) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(dictionaryManager.activateDictionary(id, userId));
    }
    
    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate dictionary")
    public ResponseEntity<Dictionary> deactivateDictionary(
            @PathVariable String id) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(dictionaryManager.deactivateDictionary(id, userId));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search dictionaries")
    public ResponseEntity<List<Dictionary>> searchDictionaries(@RequestParam String keyword) {
        return ResponseEntity.ok(dictionaryManager.searchDictionaries(keyword));
    }
    
    // ==================== Dictionary Item Management ====================
    
    @PostMapping("/{dictionaryId}/items")
    @Operation(summary = "Create dictionary item")
    public ResponseEntity<DictionaryItem> createDictionaryItem(
            @PathVariable String dictionaryId,
            @Valid @RequestBody DictionaryItemRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dictionaryManager.createDictionaryItem(dictionaryId, request, userId));
    }
    
    @GetMapping("/{dictionaryId}/items")
    @Operation(summary = "Get dictionary items")
    public ResponseEntity<List<DictionaryItem>> getDictionaryItems(@PathVariable String dictionaryId) {
        return ResponseEntity.ok(dictionaryManager.getDictionaryItems(dictionaryId));
    }
    
    @GetMapping("/{dictionaryId}/items/valid")
    @Operation(summary = "Get valid dictionary items")
    public ResponseEntity<List<DictionaryItem>> getValidDictionaryItems(@PathVariable String dictionaryId) {
        return ResponseEntity.ok(dictionaryManager.getValidDictionaryItems(dictionaryId));
    }
    
    @GetMapping("/{dictionaryId}/items/localized")
    @Operation(summary = "Get localized dictionary items")
    public ResponseEntity<List<DictionaryItemLocalized>> getLocalizedItems(
            @PathVariable String dictionaryId,
            @RequestParam(defaultValue = "zh-CN") String language) {
        return ResponseEntity.ok(dictionaryManager.getDictionaryItemsLocalized(dictionaryId, language));
    }
    
    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update dictionary item")
    public ResponseEntity<DictionaryItem> updateDictionaryItem(
            @PathVariable String itemId,
            @Valid @RequestBody DictionaryItemRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(dictionaryManager.updateDictionaryItem(itemId, request, userId));
    }
    
    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Delete dictionary item")
    public ResponseEntity<Void> deleteDictionaryItem(
            @PathVariable String itemId) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        dictionaryManager.deleteDictionaryItem(itemId, userId);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/items/{itemId}/translations")
    @Operation(summary = "Update item translations")
    public ResponseEntity<Void> updateItemTranslations(
            @PathVariable String itemId,
            @RequestBody Map<String, String> translations) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        dictionaryManager.updateItemTranslations(itemId, translations, userId);
        return ResponseEntity.ok().build();
    }
    
    // ==================== Version Management ====================
    
    @GetMapping("/{dictionaryId}/versions")
    @Operation(summary = "Get version history")
    public ResponseEntity<List<DictionaryVersion>> getVersionHistory(@PathVariable String dictionaryId) {
        return ResponseEntity.ok(dictionaryManager.getVersionHistory(dictionaryId));
    }
    
    @PostMapping("/{dictionaryId}/rollback/{version}")
    @Operation(summary = "Rollback to specified version")
    public ResponseEntity<Dictionary> rollbackToVersion(
            @PathVariable String dictionaryId,
            @PathVariable Integer version) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(dictionaryManager.rollbackToVersion(dictionaryId, version, userId));
    }
}
