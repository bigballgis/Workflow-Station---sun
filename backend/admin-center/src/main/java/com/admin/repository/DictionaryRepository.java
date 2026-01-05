package com.admin.repository;

import com.admin.entity.Dictionary;
import com.admin.enums.DictionaryStatus;
import com.admin.enums.DictionaryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据字典仓库
 */
@Repository
public interface DictionaryRepository extends JpaRepository<Dictionary, String> {
    
    /**
     * 根据代码查找字典
     */
    Optional<Dictionary> findByCode(String code);
    
    /**
     * 检查代码是否存在
     */
    boolean existsByCode(String code);
    
    /**
     * 根据类型查找字典
     */
    List<Dictionary> findByType(DictionaryType type);
    
    /**
     * 根据状态查找字典
     */
    List<Dictionary> findByStatus(DictionaryStatus status);
    
    /**
     * 根据类型和状态查找字典
     */
    List<Dictionary> findByTypeAndStatus(DictionaryType type, DictionaryStatus status);
    
    /**
     * 分页查询字典
     */
    Page<Dictionary> findByTypeAndStatus(DictionaryType type, DictionaryStatus status, Pageable pageable);
    
    /**
     * 根据名称模糊查询
     */
    @Query("SELECT d FROM Dictionary d WHERE d.name LIKE %:keyword% OR d.code LIKE %:keyword%")
    List<Dictionary> searchByKeyword(@Param("keyword") String keyword);
    
    /**
     * 获取所有启用的字典
     */
    @Query("SELECT d FROM Dictionary d WHERE d.status = 'ACTIVE' ORDER BY d.sortOrder, d.code")
    List<Dictionary> findAllActive();
}
