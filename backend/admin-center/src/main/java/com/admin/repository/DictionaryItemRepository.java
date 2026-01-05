package com.admin.repository;

import com.admin.entity.DictionaryItem;
import com.admin.enums.DictionaryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据字典项仓库
 */
@Repository
public interface DictionaryItemRepository extends JpaRepository<DictionaryItem, String> {
    
    /**
     * 根据字典ID查找所有字典项
     */
    List<DictionaryItem> findByDictionaryIdOrderBySortOrder(String dictionaryId);
    
    /**
     * 根据字典ID和状态查找字典项
     */
    List<DictionaryItem> findByDictionaryIdAndStatusOrderBySortOrder(String dictionaryId, DictionaryStatus status);
    
    /**
     * 根据字典ID和字典项代码查找
     */
    Optional<DictionaryItem> findByDictionaryIdAndItemCode(String dictionaryId, String itemCode);
    
    /**
     * 检查字典项代码是否存在
     */
    boolean existsByDictionaryIdAndItemCode(String dictionaryId, String itemCode);
    
    /**
     * 根据父级ID查找子项
     */
    List<DictionaryItem> findByParentIdOrderBySortOrder(String parentId);
    
    /**
     * 查找顶级字典项（无父级）
     */
    @Query("SELECT i FROM DictionaryItem i WHERE i.dictionary.id = :dictionaryId AND i.parent IS NULL ORDER BY i.sortOrder")
    List<DictionaryItem> findTopLevelItems(@Param("dictionaryId") String dictionaryId);
    
    /**
     * 查找有效的字典项
     */
    @Query("SELECT i FROM DictionaryItem i WHERE i.dictionary.id = :dictionaryId " +
           "AND i.status = 'ACTIVE' " +
           "AND (i.validFrom IS NULL OR i.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (i.validTo IS NULL OR i.validTo >= CURRENT_TIMESTAMP) " +
           "ORDER BY i.sortOrder")
    List<DictionaryItem> findValidItems(@Param("dictionaryId") String dictionaryId);
    
    /**
     * 删除字典的所有字典项
     */
    @Modifying
    @Query("DELETE FROM DictionaryItem i WHERE i.dictionary.id = :dictionaryId")
    void deleteByDictionaryId(@Param("dictionaryId") String dictionaryId);
    
    /**
     * 根据字典代码查找字典项
     */
    @Query("SELECT i FROM DictionaryItem i WHERE i.dictionary.code = :dictCode AND i.status = 'ACTIVE' ORDER BY i.sortOrder")
    List<DictionaryItem> findByDictionaryCode(@Param("dictCode") String dictCode);
}
